# Contrat d'interface API — Espace Partenaire (Assurance)

Ce document est la **source de vérité** entre le frontend (PWA) et le backend.
La spécification machine est dans [`openapi.yaml`](./openapi.yaml) (OpenAPI 3.1,
importable dans Swagger Editor / Postman). Ce fichier-ci en donne les règles et
le contexte.

> **Règle d'or** : le backend doit respecter ce contrat **au champ près** (noms,
> types, enums, casse). C'est ce qui permet au front de basculer des mocks vers
> l'API réelle (`VITE_USE_MOCKS=false`) **sans modifier une ligne d'UI**.

---

## 1. Conventions transverses

| Sujet | Règle |
| --- | --- |
| Format | JSON (`application/json`) en entrée et sortie |
| Casse des champs | **camelCase**, jamais renommer (`clientLastName`, pas `client_last_name`) |
| Montants | **Entiers en FCFA**, sans décimales |
| Taux | Nombres **entre 0 et 1** (`commissionRate`, `conversionRate`, `objectiveProgress`) |
| Dates | Chaînes **ISO 8601** (date-time, UTC) ou `null` |
| Base URL | Définie par `VITE_API_URL` (ex. `https://api.boxafrica.example/api`) |

---

## 2. Authentification (JWT)

Flux implémenté côté front (intercepteurs Axios) :

1. `POST /auth/login` → renvoie `{ user, tokens }`. Le front stocke
   `accessToken` + `refreshToken`.
2. Chaque requête authentifiée porte l'en-tête
   `Authorization: Bearer <accessToken>`.
3. Sur une réponse **`401`**, le front appelle **une fois**
   `POST /auth/refresh` avec le `refreshToken`, puis **rejoue** la requête
   d'origine. Si le refresh échoue → déconnexion + redirection login.

**Important pour le backend :**

- Renvoyer un **`401`** (et pas 403) quand l'access token est expiré/invalide,
  sinon le refresh automatique ne se déclenche pas.
- `/auth/refresh` doit accepter le `refreshToken` dans le **corps** (pas
  d'en-tête Bearer requis).
- `expiresIn` est en **secondes**.

---

## 3. Endpoints (résumé)

| Méthode | Route | Auth | Rôle |
| --- | --- | --- | --- |
| POST | `/auth/login` | — | Connexion |
| POST | `/auth/refresh` | — | Rafraîchir les tokens |
| POST | `/auth/logout` | ✅ | Déconnexion (invalider le refresh token) |
| GET | `/auth/me` | ✅ | Profil courant |
| PATCH | `/auth/me` | ✅ | Mise à jour profil (`fullName`, `email`, `sector`) |
| GET | `/contracts?search=&status=` | ✅ | Liste des contrats du commercial |
| GET | `/contracts/{id}` | ✅ | Détail d'un contrat |
| GET | `/dashboard/kpis?period=` | ✅ | KPIs agrégés |

> **Périmètre** : `/contracts` et `/dashboard/kpis` ne renvoient QUE les données
> du commercial authentifié (filtrage par le `sub` du JWT côté serveur).

---

## 4. Règles métier (invariants à garantir côté serveur)

Ces calculs sont attendus tels quels par le front. Le backend en est
responsable — le front ne recalcule rien.

### Par contrat

```
commissionAmount = round(premium * commissionRate)   # commission AssurZen, commissionRate en FRACTION (0.10 = 10 %)
partnerShare     = commission partenaire, montant DISTINCT de commissionAmount (FCFA entiers)
```

> Mise a jour 2026-09-25 : `partnerShare` n'est plus derive de `premium - commissionAmount`.
> Ordre de resolution : valeur fournie dans la requete > valeur deja enregistree (modification,
> meme partenaire) > commission configuree (`/partner-commissions` : partenaire precis, sinon son
> type) > **400** si rien. Sans partenaire : 0. Bornes : 0 <= montant <= premium, sinon 400.

- `commissionRate` dépend de la **branche** (barème) :

  | Branche | Taux |
  | --- | --- |
  | Auto | 0.10 |
  | Habitation (MRH) | 0.12 |
  | Santé | 0.08 |
  | Vie / Épargne | 0.15 |
  | Prévoyance | 0.18 |
  | RC Pro | 0.12 |

  > Ce barème est une **proposition** issue du front mock. À confirmer/ajuster
  > par le métier ; idéalement piloté par une table côté backend.

- `effectiveDate` et `signedAt` valent `null` tant que le contrat n'est pas
  respectivement en vigueur / signé.

### Agrégats dashboard (sur le périmètre du commercial + la période demandée)

```
production        = Σ premium           (status = signed)
totalCommission   = Σ commissionAmount  (status = signed)
paidCommission    = Σ commissionAmount  (status = signed ET commissionStatus = paid)
pendingCommission = totalCommission - paidCommission
partnerShare      = Σ partnerShare      (status = signed)
conversionRate    = signedContracts / totalContracts   (0 si totalContracts = 0)
objectiveProgress = min(production / monthlyObjective, 1)
```

- `period = month` → mois calendaire **courant** ; `period = all` → tout le
  portefeuille.
- `monthlyObjective` : objectif de production. Fixé à `2_000_000` dans le mock —
  à rendre configurable côté backend (par commercial / par période).

---

## 5. Énumérations (valeurs exactes)

- `ContractStatus` : `signed` | `pending` | `draft` | `cancelled`
  (en vigueur / en attente / devis / résilié)
- Filtre `status` de `/contracts` : les valeurs ci-dessus **+ `all`** (ou param
  absent = pas de filtre)
- `CommissionStatus` : `paid` | `pending`
- `role` : `commercial` | `manager`
- `period` : `month` | `all`

---

## 6. Erreurs

Format homogène attendu pour toute erreur :

```json
{ "message": "Identifiants invalides", "code": "INVALID_CREDENTIALS" }
```

- `message` est **affiché tel quel** à l'utilisateur → le rédiger en français,
  lisible.
- `code` est optionnel (usage machine).
- Codes HTTP : `400` (validation), `401` (non/auth expirée → déclenche refresh),
  `404` (introuvable), `409` (conflit), `422` (entité non traitable).

---

## 7. Correspondance avec le code front (pour traçabilité)

Le contrat dérive directement des *ports* hexagonaux du front. Toute évolution
de l'API doit rester alignée avec :

| Élément contrat | Fichier front |
| --- | --- |
| `User`, `AuthTokens`, `Credentials` | `src/core/entities/User.ts` |
| `Contract`, `DashboardKPIs`, enums | `src/core/entities/Contract.ts` |
| Endpoints Auth | `src/infrastructure/repositories/AuthRepository.ts` |
| Endpoints Contracts | `src/infrastructure/repositories/ContractRepository.ts` |
| Endpoints Dashboard | `src/infrastructure/repositories/DashboardRepository.ts` |
| Flux JWT / refresh | `src/infrastructure/http/axiosClient.ts` |

> Les `Mock*Repository` du même dossier servent de **référence de
> comportement** : le backend peut s'en inspirer pour les cas limites (tri,
> recherche, agrégats).

---

## 8. Pour démarrer côté backend

1. Importer `openapi.yaml` dans Swagger Editor / Postman / un générateur de
   stubs (openapi-generator, NestJS, etc.).
2. Implémenter les 8 endpoints en respectant schémas et invariants (section 4).
3. Tester l'intégration : passer le front en `VITE_USE_MOCKS=false` et
   renseigner `VITE_API_URL`. Si le contrat est respecté, **aucune autre
   modification front n'est nécessaire**.
