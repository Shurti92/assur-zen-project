# Document de remise backend — Espace Partenaire (Assurance)

**À l'attention du développeur backend.** Ce document décrit, pour chaque
endpoint, **exactement** le payload de requête et la réponse attendus par
l'application front. Respecter ces formats au champ près garantit l'intégration
sans retouche côté front.

- Spec machine (Swagger/Postman, génération de stubs) : [`openapi.yaml`](./openapi.yaml)
- Règles & conventions détaillées : [`API_CONTRACT.md`](./API_CONTRACT.md)
- Types TypeScript prêts à référencer : [`api-types.ts`](./api-types.ts)
- **Ce fichier** : les payloads/réponses concrets + un jeu de test.

---

## 0. Rappels essentiels

| Sujet | Règle |
| --- | --- |
| Format | JSON partout (`Content-Type: application/json`) |
| Casse | **camelCase**, ne jamais renommer un champ |
| Montants | **entiers FCFA** (pas de décimales) |
| Taux | nombres **0..1** (ex. `0.1` = 10 %) |
| Dates | **ISO 8601** UTC (`2026-06-12T09:30:00.000Z`) ou `null` |
| Auth | `Authorization: Bearer <accessToken>` sur les routes protégées |
| Sur 401 | renvoyer **401** (pas 403) → déclenche le refresh automatique |

---

## 1. `POST /auth/login` — Connexion

**Auth :** non. **Requête :**

```json
{
  "username": "commercial",
  "password": "1234"
}
```

**Réponse `200` :**

```json
{
  "user": {
    "id": "u-001",
    "fullName": "Awa Diop",
    "email": "commercial@gmail.com",
    "role": "commercial",
    "sector": "Dakar — Plateau",
    "avatarUrl": null
  },
  "tokens": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.<payload>.<sig>",
    "refreshToken": "def502004a1b...<opaque>",
    "expiresIn": 3600
  }
}
```

**Réponse `401` :**

```json
{ "message": "Identifiants invalides", "code": "INVALID_CREDENTIALS" }
```

---

## 2. `POST /auth/refresh` — Rafraîchir les tokens

**Auth :** non (le refresh token est dans le corps). **Requête :**

```json
{ "refreshToken": "def502004a1b...<opaque>" }
```

**Réponse `200` :** (même forme que `tokens` du login)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.<new>.<sig>",
  "refreshToken": "def502009f7c...<rotated>",
  "expiresIn": 3600
}
```

**Réponse `401` :** refresh token invalide/expiré → le front déconnecte.

```json
{ "message": "Session expirée", "code": "REFRESH_EXPIRED" }
```

---

## 3. `POST /auth/logout` — Déconnexion

**Auth :** oui. **Requête :** corps vide.
**Réponse `204` :** aucun corps. (Le serveur invalide le refresh token.)

---

## 4. `GET /auth/me` — Profil courant

**Auth :** oui. **Réponse `200` :**

```json
{
  "id": "u-001",
  "fullName": "Awa Diop",
  "email": "commercial@gmail.com",
  "role": "commercial",
  "sector": "Dakar — Plateau",
  "avatarUrl": null
}
```

---

## 5. `PATCH /auth/me` — Mise à jour du profil

**Auth :** oui. **Requête :** mise à jour **partielle** (1 à 3 champs parmi
`fullName`, `email`, `sector`). Exemple :

```json
{ "email": "awa.diop@gmail.com", "sector": "Dakar — Almadies" }
```

**Réponse `200` :** le **profil complet** à jour (même forme que `GET /auth/me`).

**Réponse `400` :**

```json
{ "message": "Email invalide", "code": "VALIDATION_ERROR" }
```

---

## 6. `GET /contracts` — Liste des contrats

**Auth :** oui. **Query params (optionnels) :**

| Param | Valeurs | Effet |
| --- | --- | --- |
| `search` | texte libre | filtre sur nom, prénom, n° police, ville, branche |
| `status` | `all` \| `signed` \| `pending` \| `draft` \| `cancelled` | filtre statut (`all`/absent = tous) |

Exemple : `GET /contracts?status=signed&search=sow`

**Réponse `200` :** tableau de contrats du commercial connecté, **trié par
`createdAt` décroissant**.

```json
[
  {
    "id": "c-001",
    "policyNumber": "POL-2026-1001",
    "clientFirstName": "Mamadou",
    "clientLastName": "Sow",
    "clientPhone": "+221 77 100 20 30",
    "clientCity": "Dakar",
    "product": "Auto",
    "premium": 150000,
    "commissionRate": 0.1,
    "commissionAmount": 15000,
    "partnerShare": 135000,
    "status": "signed",
    "commissionStatus": "paid",
    "effectiveDate": "2026-05-10T00:00:00.000Z",
    "signedAt": "2026-05-10T00:00:00.000Z",
    "createdAt": "2026-05-10T00:00:00.000Z",
    "salesRep": "Awa Diop",
    "notes": null
  }
]
```

---

## 7. `GET /contracts/{id}` — Détail d'un contrat

**Auth :** oui. Exemple : `GET /contracts/c-001`
**Réponse `200` :** un objet `Contract` (forme identique à un élément du tableau
ci-dessus). **Réponse `404` :**

```json
{ "message": "Contrat introuvable", "code": "NOT_FOUND" }
```

---

## 8. `GET /dashboard/kpis` — Indicateurs agrégés

**Auth :** oui. **Query param (obligatoire) :**

| Param | Valeurs | Effet |
| --- | --- | --- |
| `period` | `month` \| `all` | `month` = mois courant ; `all` = tout le portefeuille |

Exemple : `GET /dashboard/kpis?period=all`

**Réponse `200` :**

```json
{
  "totalContracts": 3,
  "signedContracts": 2,
  "pendingContracts": 1,
  "production": 550000,
  "totalCommission": 75000,
  "paidCommission": 15000,
  "pendingCommission": 60000,
  "partnerShare": 475000,
  "conversionRate": 0.6667,
  "monthlyObjective": 2000000,
  "objectiveProgress": 0.275
}
```

---

## 9. Jeu de test cohérent (vecteur de validation)

Ce mini-portefeuille permet au backend de **vérifier ses calculs**. En entrée,
ces 3 contrats ; en sortie, les KPIs `period=all` attendus (section 8).

| id | branche | premium | rate | commissionAmount | partnerShare | status | commissionStatus |
| --- | --- | --- | --- | --- | --- | --- | --- |
| c-001 | Auto | 150000 | 0.10 | **15000** | **135000** | signed | paid |
| c-002 | Santé | 205000 | 0.08 | **16400** | **188600** | pending | pending |
| c-003 | Vie / Épargne | 400000 | 0.15 | **60000** | **340000** | signed | pending |

**Vérification des invariants :**

```
commissionAmount = round(premium * rate)
  c-001: round(150000*0.10)=15000   c-002: round(205000*0.08)=16400   c-003: round(400000*0.15)=60000
partnerShare = premium - commissionAmount
  c-001: 135000   c-002: 188600   c-003: 340000
  (exemple historique - depuis le 2026-09-25, partnerShare est la commission partenaire saisie ou
  configuree, plus derivee de la prime : voir API_CONTRACT.md)

KPIs (period=all) :
  totalContracts    = 3
  signedContracts   = 2            (c-001, c-003)
  pendingContracts  = 1            (c-002)
  production        = 150000 + 400000              = 550000   (signed uniquement)
  totalCommission   = 15000 + 60000                = 75000    (signed)
  paidCommission    = 15000                        (signed ET commissionStatus=paid)
  pendingCommission = 75000 - 15000                = 60000
  partnerShare      = 135000 + 340000              = 475000   (signed)
  conversionRate    = 2 / 3                         = 0.6667
  monthlyObjective  = 2000000
  objectiveProgress = min(550000/2000000, 1)        = 0.275
```

> Note : `c-002` est `pending` (pas `signed`), donc **exclu** de production,
> commission et part partenaire. C'est le piège classique — la commission n'est
> comptée qu'à partir du moment où le contrat est en vigueur.

---

## 10. Format d'erreur (toutes routes)

```json
{ "message": "Texte lisible affiché à l'utilisateur", "code": "MACHINE_CODE" }
```

- `message` : **affiché tel quel** au commercial → en français, clair.
- `code` : optionnel, pour le logging/diagnostic.
- HTTP : `400` validation · `401` non/auth expirée (**déclenche le refresh**) ·
  `404` introuvable · `409` conflit · `422` non traitable.

---

## 11. Checklist d'intégration

- [ ] Les 8 endpoints répondent avec les formats ci-dessus (camelCase exact).
- [ ] JWT Bearer accepté ; **401** renvoyé sur access token expiré.
- [ ] `/auth/refresh` accepte le refresh token dans le corps, renvoie de
      nouveaux tokens.
- [ ] `/contracts` et `/dashboard/kpis` filtrés sur le commercial du JWT.
- [ ] Invariants de calcul respectés (section 9).
- [ ] Montants en entiers FCFA, taux en 0..1, dates ISO 8601.
- [ ] Erreurs au format `{ message, code }`.
- [ ] Test final : front en `VITE_USE_MOCKS=false` + `VITE_API_URL` → l'app
      fonctionne sans modification.
