# Contrat d'interface — Avenants sur contrat signé

> ✅ **Implémenté côté backend** (2026-08-23). Vérifié directement dans le schéma réel (`GET /v3/api-docs`) et testé en live sur un contrat réel. Aucune ambiguïté à lever — ce contrat est prêt à consommer tel quel.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Ce qui change sur un contrat déjà signé

Jusqu'ici, `PUT /contracts/{id}` acceptait de modifier n'importe quel champ, même sur un contrat `signed`, sans laisser de trace structurée. **Ce n'est plus le cas** pour 5 champs sensibles :

`premium`, `commissionRate`, `commissionAmount`, `partenaireId`, `compagnieId`

Sur un contrat `signed`, si l'un de ces champs diffère de la valeur actuelle, `PUT /contracts/{id}` renvoie désormais :

```
409 Conflict
{ "message": "Un contrat signe ne permet plus de modifier directement premium, commissionAmount - passez par POST /contracts/{id}/avenants" }
```

Le message liste précisément les champs bloqués détectés. **Aucun changement si le contrat n'est pas `signed`** (draft/pending/cancelled restent librement modifiables comme avant), et les autres champs (`notes`, `dateEcheance`, `modePaiement`, etc.) restent modifiables même sur un contrat signé.

Seul un avenant explicite (§2) permet désormais de faire évoluer ces 5 champs sur un contrat signé.

---

## 2. Créer un avenant

```
POST /contracts/{id}/avenants
```

Réservé aux administrateurs (403 sinon). 409 si le contrat n'est pas `signed`.

### Requête

```json
{
  "type": "MODIFICATION_PRIME",
  "dateEffet": "2026-09-01",
  "motif": "Régularisation prime suite avenant véhicule",
  "description": "Texte libre optionnel, détail complémentaire",
  "premium": 200000,
  "commissionRate": null,
  "commissionAmount": 36000,
  "partenaireId": null,
  "compagnieId": null
}
```

| Champ | Type | Requis | Notes |
|---|---|---|---|
| `type` | string (enum `TypeAvenant`) | oui | Voir §3 — classification complète |
| `dateEffet` | date (`AAAA-MM-JJ`) | oui | Date d'effet métier de l'avenant |
| `motif` | string (max 500) | oui | Justification obligatoire |
| `description` | string | non | Détail libre, notamment pour les types sans champ structuré (garanties, bien assuré, conducteur, zone...) |
| `premium` | int64 | non | Nouvelle prime — `null` = inchangé |
| `commissionRate` | number | non | Nouveau taux de commission — `null` = inchangé |
| `commissionAmount` | int64 | non | Nouveau montant de commission — `null` = inchangé |
| `partenaireId` | int64 | non | Nouveau partenaire rattaché — `null` = inchangé |
| `compagnieId` | int64 | non | Nouvelle compagnie — `null` = inchangé |

**Important** : seuls `premium`, `commissionRate`, `commissionAmount`, `partenaireId`, `compagnieId` correspondent à un champ réellement modélisé sur `Contract`. Les autres natures d'avenant (garanties, bien assuré, conducteur, franchise, plafond, usage, zone...) **n'ont aucun champ structuré dans le schéma actuel** — elles sont tracées uniquement via `motif`/`description` en texte libre. Ne pas envoyer de valeur dans ces cas, seul le texte compte.

`partnerShare` est **toujours recalculé côté serveur** (`premium - commissionAmount`) dès que `premium`, `commissionAmount` ou `partenaireId` changent — jamais à fournir, jamais à recalculer côté front.

### Réponse `201`

```json
{
  "id": 1,
  "contractId": 46,
  "contractPolicyNumber": "CTR2026000270",
  "numero": 1,
  "type": "MODIFICATION_PRIME",
  "categorie": "FINANCIER",
  "dateEffet": "2026-09-01",
  "motif": "Régularisation prime suite avenant véhicule",
  "description": "Texte libre optionnel, détail complémentaire",
  "ancienPremium": 150000,
  "nouveauPremium": 200000,
  "ancienneCommissionRate": null,
  "nouvelleCommissionRate": null,
  "ancienCommissionAmount": 27000,
  "nouveauCommissionAmount": 36000,
  "ancienPartenaireId": null,
  "ancienPartenaireNom": null,
  "nouveauPartenaireId": null,
  "nouveauPartenaireNom": null,
  "ancienneCompagnieId": null,
  "ancienneCompagnieNom": null,
  "nouvelleCompagnieId": null,
  "nouvelleCompagnieNom": null,
  "createdById": 29,
  "createdByUsername": "0700000013",
  "createdAt": "2026-08-23T02:33:53Z"
}
```

`numero` est séquentiel par contrat (1, 2, 3...), calculé automatiquement — jamais à fournir. Les champs `ancien*/nouveau*` ne sont renseignés (non-`null`) que pour ceux réellement fournis et différents de la valeur courante ; tous les autres restent `null`.

---

## 3. Classification (`TypeAvenant` / `categorie`)

`categorie` est dérivée automatiquement de `type`, toujours cohérente — ne jamais l'envoyer en requête (absente d'`AvenantRequest`), uniquement présente en réponse.

### `ADMINISTRATIF`
`MODIFICATION_ASSURE`, `MODIFICATION_SOUSCRIPTEUR`, `MODIFICATION_COORDONNEES`, `MODIFICATION_ADRESSE`, `MODIFICATION_BENEFICIAIRE`, `CORRECTION_INFORMATION`, `CHANGEMENT_INTERMEDIAIRE`, `CHANGEMENT_MODE_PAIEMENT`, `CHANGEMENT_FREQUENCE_PAIEMENT`

### `TECHNIQUE`
`MODIFICATION_RISQUE`, `MODIFICATION_BIEN_ASSURE`, `REMPLACEMENT_BIEN`, `AJOUT_BIEN`, `RETRAIT_BIEN`, `AJOUT_GARANTIE`, `SUPPRESSION_GARANTIE`, `MODIFICATION_GARANTIE`, `MODIFICATION_FRANCHISE`, `MODIFICATION_PLAFOND`, `MODIFICATION_USAGE`, `MODIFICATION_ZONE`, `MODIFICATION_CONDUCTEUR`, `MODIFICATION_CARACTERISTIQUE`, `EXTENSION_TERRITORIALE`, `REDUCTION_TERRITORIALE`

### `FINANCIER`
`MODIFICATION_PRIME`, `SURPRIME`, `REMISE`, `ANNULATION_REMISE`, `PRIME_COMPLEMENTAIRE`, `REDUCTION_PRIME`, `REMBOURSEMENT_PRIME`, `REGULARISATION_PRIME`, `MODIFICATION_TAXE`, `REGULARISATION_FINANCIERE`, `ANNULATION_OPERATION_FINANCIERE`, `TRANSFERT_CREDIT`

Seuls `MODIFICATION_PRIME`, `SURPRIME`, `REMISE`, `PRIME_COMPLEMENTAIRE`, `REDUCTION_PRIME`, `REGULARISATION_PRIME`, `ANNULATION_REMISE`, `REMBOURSEMENT_PRIME`, `REGULARISATION_FINANCIERE` ont un usage naturel avec `premium`/`commissionAmount`. Les autres types financiers (`MODIFICATION_TAXE`, `ANNULATION_OPERATION_FINANCIERE`, `TRANSFERT_CREDIT`) comme tous les types techniques n'ont pas de champ dédié — texte libre uniquement.

---

## 4. Lister les avenants d'un contrat

```
GET /contracts/{id}/avenants
```

Accessible à l'admin, au partenaire concerné, ou au commercial (`salesRep`) du contrat. Retourne un tableau `AvenantResponse[]` (même forme que §2), trié par `numero` croissant.

---

## 5. Contrats ayant subi au moins un avenant

Deux listes, résumé agrégé par contrat (pas le détail des avenants — utiliser §4 pour ça) :

```
GET /contracts/avenants?page=0&size=20
```
Global, tous partenaires confondus. **Réservé aux administrateurs.**

```
GET /partenaires/{id}/contracts/avenants?page=0&size=20
```
Scopé à un partenaire. Accessible à l'admin ou au partenaire lui-même.

### Réponse `200` (les deux, même forme `PageResponse<ContractAvenantSummaryResponse>`)

```json
{
  "content": [
    {
      "contractId": 46,
      "policyNumber": "CTR2026000270",
      "partenaireId": 1,
      "partenaireName": "VenteTest Force",
      "nombreAvenants": 2,
      "dernierNumeroAvenant": 2,
      "dernierTypeAvenant": "MODIFICATION_ADRESSE",
      "dernierAvenantAt": "2026-08-23T02:33:53Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## 6. Historique complet d'un partenaire (avenants + audit générique)

```
GET /partenaires/{id}/contracts/historique?page=0&size=20
```

Timeline chronologique unique (triée par date décroissante) combinant :
- les avenants (`source: "AVENANT"`) de tous les contrats du partenaire ;
- les entrées d'audit générique existantes (`source: "AUDIT"` — création, modifications hors avenant, changements de statut) du même périmètre.

Accessible à l'admin ou au partenaire lui-même (pas de bypass commercial ici, contrairement à `GET /partenaires/{id}/contracts`).

### Réponse `200`

```json
{
  "content": [
    {
      "contractId": 46,
      "policyNumber": "CTR2026000270",
      "source": "AVENANT",
      "type": "MODIFICATION_PRIME",
      "numeroAvenant": 1,
      "motif": "Régularisation prime suite avenant véhicule",
      "description": "Texte libre optionnel",
      "champsModifies": null,
      "date": "2026-08-23T02:33:53Z",
      "auteurId": 29,
      "auteur": "0700000013"
    },
    {
      "contractId": 46,
      "policyNumber": "CTR2026000270",
      "source": "AUDIT",
      "type": "UPDATE",
      "numeroAvenant": null,
      "motif": null,
      "description": "Contract modifie : notes passe de ... a ...",
      "champsModifies": "[\"notes\"]",
      "date": "2026-08-10T09:12:00Z",
      "auteurId": 5,
      "auteur": "commercial1"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

| Champ | Notes |
|---|---|
| `source` | `"AVENANT"` ou `"AUDIT"` — détermine comment interpréter les autres champs |
| `type` | Pour `AVENANT` : une valeur `TypeAvenant` (§3). Pour `AUDIT` : une action générique (`CREATE`, `UPDATE`, ...) |
| `numeroAvenant` | Uniquement renseigné pour `source: "AVENANT"` |
| `champsModifies` | Uniquement renseigné pour `source: "AUDIT"` (JSON array en texte, ex. `["notes"]`) — toujours `null` pour un avenant (utiliser `GET /contracts/{id}/avenants` pour le détail structuré avant/après) |

---

## 7. Champs ajoutés aux KPIs (`/dashboard/kpis` et `/dashboard/kpis-back-office`)

`avenantsCount` (int64) — nombre total d'avenants (tous types confondus) sur les contrats du périmètre :
- `/dashboard/kpis` (mobile, scopé au partenaire/commercial connecté) : champ racine `avenantsCount`.
- `/dashboard/kpis-back-office` (ce repo, vue admin globale) : `finance.avenantsCount`, respecte le filtre `period` comme le reste du bloc `finance`.

Voir `docs/api-contracts/dashboard.md` §2 pour le détail complet du bloc `finance` (mis à jour en parallèle avec ce champ + `primeNonReverseeCount`/`primeNonReverseeMontant`, déjà livrés le 2026-08-22).

---

## 8. Ce que le frontend fera dès livraison confirmée

1. Formulaire "Créer un avenant" sur la fiche contrat (visible seulement si `status === 'signed'`, réservé aux rôles admin) : sélection du type (regroupé par catégorie, §3), date d'effet, motif obligatoire, description libre, et les champs prime/commission/partenaire/compagnie **seulement si pertinents pour le type choisi**.
2. Sur `PUT /contracts/{id}` d'un contrat signé : si le backend renvoie `409` avec le message de restriction, afficher un message clair invitant à passer par le formulaire d'avenant plutôt qu'un message d'erreur générique.
3. Onglet "Avenants" sur la fiche contrat : `GET /contracts/{id}/avenants`, affiché numéroté chronologiquement.
4. Nouvel écran/onglet "Historique" sur la fiche partenaire : `GET /partenaires/{id}/contracts/historique`, timeline mixte avenants + audit.
5. KPI "Avenants" (compteur) sur les tableaux de bord existants, à partir de `avenantsCount`.

---

## Rappel

Suivi dans la mémoire projet "avenant module implementation" (session du 2026-08-23).
