# Contrat d'interface — Audit trail métier (historique des modifications)

> 🗄️ **Historique — dépassé par la réalité.** Le backend a depuis implémenté un vrai Audit Trail Engine, mais avec un schéma différent de celui proposé ci-dessous (ex. `HistoriqueModificationResponse` au lieu de `AuditLogResponse`, endpoints `/audit-logs/*`). Le frontend (`core/audit-trail/`, `views/audit-trail/`) a été réécrit contre le vrai schéma confirmé via Swagger. Ce document reste utile pour comprendre le besoin métier d'origine, mais ne décrit plus l'API réelle — voir mémoire projet "dépendances backend manquantes" pour le mapping à jour.

> ⚠️ **Aucune de ces API n'existe aujourd'hui.** *(constat historique au moment de la rédaction — obsolète, voir note ci-dessus)* Vérifié via `/v3/api-docs` le 2026-08-09 : le seul endpoint contenant "audit" est `GET /audit` (`listAuditFlux`), qui alimente la page **Interceptors → Audit** déjà en production (`views/audit/`) — c'est un journal d'appels HTTP (route, code statut, durée, IP, utilisateur), pas un audit trail métier. Ce document propose une **API distincte et additive**, à ne pas confondre avec `/audit` existant.
>
> Route frontend déjà réservée pour ça : `configuration/historique-modifications` (sous Paramétrage), actuellement un `ComingSoonComponent` sans description. Rien ne sera construit côté frontend tant que ce contrat n'est pas au moins partiellement implémenté — voir mémoire projet "dépendances backend manquantes", entrée sur l'audit trail.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Contexte

Le besoin exprimé est un **audit trail métier** : pour un objet donné (contrat, sinistre, souscription, réclamation, parrainage, paiement...), pouvoir consulter qui a fait quoi, quand, et — pour les modifications — quelle valeur a changé (ancienne → nouvelle). Objectifs concrets :

- Un onglet **"Historique"** intégrable dans les écrans de détail existants (Contrat, Sinistre, Réclamation, Parrainage, etc.), affichant la chronologie des événements sur *cet objet précis*.
- Une page transverse **"Historique des modifications"** (route déjà réservée) permettant de chercher/filtrer tous les événements d'audit métier de l'application, indépendamment de l'objet.
- Un tableau de bord avec des indicateurs agrégés (volumes par type d'action, par module, par utilisateur, évolution dans le temps).
- Une vue dédiée aux **actions sensibles** (suppressions, validations, approbations, exports...).

Ce n'est pas un remplacement du journal technique existant (`/audit`, appels HTTP/latence/erreurs) — les deux peuvent coexister, avec des sources de données et des cas d'usage différents.

### Ce qui existe déjà côté "historique", à ne pas dupliquer

Plusieurs modules ont déjà leur propre historique **local à l'objet**, avec un format différent à chaque fois :

- `GET /parrainage/parrainages/{id}/historique` → `HistoriqueParrainageResponse[]` (`typeEvenement`, `ancienStatut`, `nouveauStatut`, `description`, `createdAt`, `createdBy`)
- `GET /fidelite/comptes/{insuredId}/historique` → mouvements de points
- `GET /cross-sell/opportunites/{id}/historique` → changements de statut d'opportunité

Ces endpoints spécifiques peuvent rester tels quels (ils sont déjà consommés par leurs pages respectives) — l'API ci-dessous n'a pas besoin de les remplacer immédiatement. Mais si un audit trail générique est construit, il serait cohérent de faire converger ces historiques spécifiques vers la même table/mécanisme à terme, pour éviter la prolifération de formats.

## 2. Proposition — recherche/liste (page transverse)

```
GET /api-assur-zen/audit-logs
```

| Paramètre | Type | Requis | Détail |
|---|---|---|---|
| `page`, `size` | int | non | Pagination serveur standard (défaut `0`/`20`), comme partout ailleurs dans l'API |
| `dateDebut`, `dateFin` | date ISO | non | Bornes de `createdAt` |
| `utilisateur` | string | non | Filtre par `userId` ou identifiant (téléphone) |
| `module` | string | non | Ex. `CONTRATS`, `SINISTRES`, `SOUSCRIPTION` — enum à définir avec le backend |
| `entite` | string | non | Type d'entité, ex. `Contrat`, `Sinistre` |
| `entiteId` | string | non | Identifiant de l'objet concerné |
| `action` | string | non | `CREATE`, `UPDATE`, `DELETE`, `APPROVE`, `REJECT`, `CANCEL`, `LOGIN`, `EXPORT` (enum à confirmer) |
| `statut` | string | non | Succès / échec de l'action, si applicable |
| `canal` | string | non | `WEB`, `API`, `MOBILE`, `BATCH`... (si le concept existe côté backend) |
| `correlationId` | string | non | Regroupe tous les événements d'une même transaction/requête |
| `q` | string | non | Recherche libre (utilisateur, objet, description) |

### Réponse `200` proposée — `PageResponseAuditLogResponse`

```json
{
  "content": [
    {
      "id": 123,
      "dateAction": "2026-08-09T14:20:00Z",
      "utilisateurId": 7,
      "utilisateurNom": "Jean Kouassi",
      "role": "Agent",
      "module": "CONTRATS",
      "entite": "Contrat",
      "entiteId": "46",
      "entiteLibelle": "CTR2026000046",
      "action": "UPDATE",
      "statut": "SUCCES",
      "canal": "WEB",
      "ipAddress": "10.0.0.4",
      "userAgent": "Mozilla/5.0 ...",
      "correlationId": "a1b2c3d4-...",
      "traceId": "e5f6...",
      "motif": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 340,
  "totalPages": 17
}
```

Tri par défaut : `dateAction` décroissant. Tri serveur sur au moins `dateAction`.

## 3. Proposition — détail d'un événement

```
GET /api-assur-zen/audit-logs/{id}
```

Réponse `AuditLogDetailResponse` = tous les champs de `AuditLogResponse` (§2) + :

```json
{
  "...": "champs de AuditLogResponse",
  "changements": [
    { "champ": "prime", "libelleChamp": "Prime", "ancienneValeur": "125000", "nouvelleValeur": "135000" },
    { "champ": "statut", "libelleChamp": "Statut", "ancienneValeur": "DRAFT", "nouvelleValeur": "ACTIVE" }
  ]
}
```

Points d'attention :
- `ancienneValeur`/`nouvelleValeur` en **string déjà formatée lisible** si possible (éviter que le frontend doive deviner le type/formattage d'un champ générique) — sinon fournir un `type` par changement (`STRING`/`NUMBER`/`DATE`/`ENUM`) pour permettre un affichage correct.
- Pour une `CREATE`, `ancienneValeur` est `null` sur tous les champs ; pour une `DELETE`, `nouvelleValeur` est `null`.

## 4. Proposition — historique d'un objet précis (pour l'onglet "Historique")

```
GET /api-assur-zen/audit-logs/entity/{entiteNom}/{entiteId}
```

Retourne la liste chronologique (`AuditLogResponse[]`, non paginée ou paginée légèrement — à discuter selon le volume attendu par objet) pour un objet donné, triée par `dateAction` croissant (ordre chronologique naturel pour une timeline). C'est l'endpoint que consommerait l'onglet "Historique" intégré dans Contrat/Sinistre/Souscription/Paiement/Réclamation/Parrainage/etc.

`entiteNom` : valeur de l'enum `entite` utilisé en §2 (ex. `Contrat`, `Sinistre`).

## 5. Proposition — statistiques (tableau de bord)

```
GET /api-assur-zen/audit-logs/statistics?dateDebut=&dateFin=
```

```json
{
  "totalActions": 4820,
  "parAction": { "CREATE": 1200, "UPDATE": 2100, "DELETE": 80, "APPROVE": 340, "REJECT": 40, "CANCEL": 60, "LOGIN": 900, "EXPORT": 100 },
  "actionsSensibles": 180,
  "utilisateursActifs": 24,
  "parModule": [{ "module": "CONTRATS", "total": 1800 }, { "module": "SINISTRES", "total": 620 }],
  "parJour": [{ "date": "2026-08-01", "total": 210 }, { "date": "2026-08-02", "total": 185 }]
}
```

Le découpage exact des KPI est à affiner avec le backend selon ce qui est réellement calculable simplement (agrégats SQL sur la même table que §2, pas de nouvelle source de données).

## 6. Proposition — actions sensibles

Plutôt qu'un endpoint dédié, un simple filtre booléen sur §2 est probablement suffisant côté backend (`GET /audit-logs?sensible=true`) — évite de dupliquer la pagination/le tri. Reste à définir avec le backend ce qui qualifie une action comme "sensible" (liste d'actions fixe type `DELETE`/`APPROVE`/`EXPORT`, ou un flag posé explicitement à l'écriture).

## 7. Proposition — export

```
GET /api-assur-zen/audit-logs/export/csv?{mêmes filtres que §2}
GET /api-assur-zen/audit-logs/export/excel?{mêmes filtres que §2}
```

Réponse : fichier binaire (`Content-Type: text/csv` ou `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`), généré et streamé côté backend — le frontend ne doit reconstruire aucune donnée d'export lui-même, seulement déclencher le téléchargement du fichier reçu.

## 8. Autorisation

**Aucune convention de rôle n'existe aujourd'hui côté frontend** — l'app n'a pas de notion de `ROLE_*` ni de garde basée sur les permissions ; `AuthUser.role` est une simple chaîne, jamais utilisée pour masquer une fonctionnalité. Deux options, à trancher avec le backend avant de construire l'UI :

- **Option A (minimale, recommandée pour un premier lot)** : le backend renvoie `403` sur les endpoints sensibles (`/audit-logs/export/*`, accès au module en général) pour les utilisateurs non autorisés ; le frontend affiche/masque simplement en réaction à un `403` déjà reçu, sans avoir besoin de connaître les rôles à l'avance.
- **Option B (si un vrai contrôle d'accès UI est nécessaire)** : exposer les rôles/permissions de l'utilisateur courant (ex. champ `permissions: string[]` sur la réponse de login/`/profiles/me`), ce qui introduirait un premier système de permissions frontend générique — impact plus large que ce seul module, à cadrer séparément si retenu.

## 9. Modèles proposés (résumé)

```
AuditLogResponse {
  id, dateAction, utilisateurId, utilisateurNom, role,
  module, entite, entiteId, entiteLibelle,
  action, statut, canal, ipAddress, userAgent,
  correlationId, traceId, motif
}

AuditChangeResponse { champ, libelleChamp, ancienneValeur, nouvelleValeur, type? }

AuditLogDetailResponse extends AuditLogResponse { changements: AuditChangeResponse[] }

AuditStatisticsResponse { totalActions, parAction, actionsSensibles, utilisateursActifs, parModule[], parJour[] }

AuditLogPage { content: AuditLogResponse[], page, size, totalElements, totalPages }
```

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes". Tant que ce contrat n'est pas implémenté (même partiellement — §2+§4 suffiraient pour un premier lot utile : liste transverse + onglet Historique par objet), aucun frontend ne sera construit contre ces endpoints.
