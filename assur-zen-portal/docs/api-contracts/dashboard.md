# Contrat d'interface — Tableau de bord (back-office Angular)

> ✅ **Implémenté côté backend** : `GET /api-assur-zen/dashboard/kpis-back-office` (période `month`/`all`) — livré exactement conforme à ce contrat (blocs `volumes`/`finance`/`objectifGlobal`/`cycleDeVie`/`repartitionParProduit`/`objectifParApporteur`). Le frontend est branché dessus depuis le 2026-08-03 (`core/dashboard/dashboard.service.ts`, `core/dashboard/models/dashboard-kpis.model.ts`) — `views/dashboard/insurance-widgets/` n'agrège plus rien côté client, tout vient de cet unique appel.
>
> 🆕 **Mise à jour 2026-08-22/23** : le bloc `finance` a gagné 3 champs depuis la version initiale de ce contrat — `primeNonReverseeCount`, `primeNonReverseeMontant` (suivi des primes encaissées par un partenaire mais pas encore reversées à la compagnie) et `avenantsCount` (nombre total d'avenants sur les contrats du périmètre, voir `docs/api-contracts/avenants.md`). Rétro-compatible : champs ajoutés en fin d'objet, rien de supprimé/renommé.
>
> ⚠️ **Reste manquant** : la partie §3 ci-dessous (écriture de l'objectif, `PUT .../objectif`) n'existe pas encore côté backend. C'est le seul point encore ouvert. Voir mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Ce que la page affiche aujourd'hui, et d'où viennent les données actuellement

| Section | Données actuelles | Source actuelle (à remplacer) |
|---|---|---|
| Volumes (contrats totaux/signés/en attente, taux de conversion) | Réelles | `GET /dashboard/kpis` (API mobile, réutilisée par erreur) |
| Finance (production, commissions, part partenaire) | Réelles | `GET /dashboard/kpis` (idem) |
| Objectif global | Réelles (toujours vide, aucune écriture possible) | `GET /dashboard/kpis` (idem) |
| Cycle de vie des contrats (réalisés / échéance proche / non renouvelés) | Réelles, calculées côté front | `GET /contracts` (jusqu'à 1000 lignes rapatriées, agrégées en JS) |
| Répartition par produit | Réelle, calculée côté front | `GET /contracts` (même appel que ci-dessus) |
| Objectif par apporteur | Réelle, calculée côté front | `GET /partenaires` (jusqu'à 1000 lignes rapatriées, agrégées en JS) |

Le but de ce contrat : que le backend expose **un endpoint dédié au back-office** qui retourne directement ces données déjà agrégées, au lieu que le frontend rapatrie des centaines de lignes brutes pour les recalculer à chaque chargement.

---

## 2. Proposition — lecture des indicateurs

```
GET {{ENDPOINT}}?period={month|all}
```

| Paramètre | Type | Requis | Valeurs |
|---|---|---|---|
| `period` | string (enum) | oui | `month` (mois calendaire courant) \| `all` (cumul depuis toujours) |

`period` ne s'applique qu'aux blocs **volumes**, **finance** et **objectifGlobal** ci-dessous. Les blocs **cycleDeVie**, **repartitionParProduit** et **objectifParApporteur** reflètent l'état courant (non filtré par période) — c'est le comportement actuel du frontend, à conserver.

### Réponse `200` proposée

```json
{
  "volumes": {
    "totalContracts": 4,
    "signedContracts": 3,
    "pendingContracts": 0,
    "conversionRate": 0.75
  },
  "finance": {
    "production": 240000,
    "totalCommission": 42000,
    "paidCommission": 37500,
    "pendingCommission": 4500,
    "partnerShare": 10200,
    "primeNonReverseeCount": 3,
    "primeNonReverseeMontant": 450000,
    "avenantsCount": 1
  },
  "objectifGlobal": {
    "montant": 45000000,
    "progression": 0.78
  },
  "cycleDeVie": {
    "realises": 0,
    "echeanceProche": 2,
    "nonRenouveles": 0
  },
  "repartitionParProduit": [
    { "productId": 3, "productName": "Assurance Auto", "count": 6 },
    { "productId": 7, "productName": "Assurance Moto", "count": 2 }
  ],
  "objectifParApporteur": {
    "totalObjectif": 14000000,
    "totalRealise": 530000,
    "tauxGlobal": 3.8,
    "partenaires": [
      {
        "partenaireId": 11,
        "nom": "Kouassi Serge",
        "type": "DIRECT",
        "objectif": 5000000,
        "realise": 325000,
        "taux": 6.5
      }
    ]
  }
}
```

### Détail des champs

| Bloc.Champ | Type | Notes |
|---|---|---|
| `volumes.totalContracts` / `signedContracts` / `pendingContracts` | int64 | Même sémantique que l'actuel `/dashboard/kpis` |
| `volumes.conversionRate` | double | **Fraction 0–1** (0.75 = 75 %) |
| `finance.production` / `.totalCommission` / `.paidCommission` / `.pendingCommission` / `.partnerShare` | int64 | Montants FCFA — mêmes champs que l'actuel `/dashboard/kpis` |
| `finance.primeNonReverseeCount` | int64 | Nombre de contrats `signed` dont la prime encaissée par le partenaire n'a pas encore été reversée à la compagnie (`Contract.primeReversementStatus = non_reverse`) |
| `finance.primeNonReverseeMontant` | int64 | Somme des `premium` de ces mêmes contrats (FCFA) |
| `finance.avenantsCount` | int64 | Nombre total d'avenants (tous types) sur les contrats `signed` de la période — détail via `docs/api-contracts/avenants.md` |
| `objectifGlobal.montant` | int64 | `0` = aucun objectif défini |
| `objectifGlobal.progression` | double | Fraction 0–1, `0` si `montant` vaut `0` |
| `cycleDeVie.realises` | int64 | Contrats signés dont l'échéance est dépassée (même définition que la page "Contrats échus") |
| `cycleDeVie.echeanceProche` | int64 | Contrats signés actifs dont l'échéance tombe dans les 30 prochains jours |
| `cycleDeVie.nonRenouveles` | int64 | Parmi les `realises`, ceux sans contrat successeur (même assuré + même produit, débutant après l'échéance). Voir dépendance manquante : aucun champ de lien de renouvellement n'existe — c'est une heuristique, à formaliser si le backend ajoute un vrai champ de lien plus tard |
| `repartitionParProduit[]` | array | Triée par `count` décroissant côté backend (évite un tri redondant côté front) |
| `objectifParApporteur.partenaires[]` | array | Seulement les partenaires avec `objectif > 0` ; triée par `taux` décroissant. `type` = `DIRECT \| AGENT \| APPORTEUR` |
| `objectifParApporteur.taux` / `.tauxGlobal` | double | **Pourcentage** ici (6.5 = 6.5 %), pas une fraction — à confirmer/harmoniser avec le reste de l'API si besoin |

---

## 3. Proposition — écriture de l'objectif global

```
PUT {{ENDPOINT}}/objectif
```

```json
{ "montant": 45000000 }
```

| Champ | Type | Requis | Contrainte |
|---|---|---|---|
| `montant` | integer (int64) | oui | `>= 0`, FCFA |

Réponse `200` : le bloc `objectifGlobal` mis à jour, ou l'objet complet du §2 recalculé.

### Questions ouvertes pour le backend

- **Portée temporelle** : objectif unique récurrent, ou à ressaisir chaque mois ? Le frontend n'a besoin que d'un objectif "courant" pour l'instant, pas d'historique.
- **Permissions** : quel rôle peut appeler ce `PUT` (à aligner avec `/users` et les rôles existants) ?
- **Traçabilité** : faut-il tracer les changements via le module Audit existant (`AuditFluxResponse`) ?

---

## 4. Migration côté frontend une fois l'endpoint créé

1. Renommer/pointer `DashboardService.getKpis()` (`core/dashboard/dashboard.service.ts`) vers `{{ENDPOINT}}` avec le vrai nom fourni.
2. Adapter `DashboardKpis` (`core/dashboard/models/dashboard-kpis.model.ts`) à la forme en blocs (`volumes`/`finance`/`objectifGlobal`/...) si le backend suit cette structure.
3. Supprimer les appels `ContractService.listAll()` / `PartenaireService.listAll()` et les agrégations côté front dans `insurance-widgets.component.ts` (cycle de vie, répartition par produit, objectif par apporteur) au profit des blocs retournés directement par `{{ENDPOINT}}`.
4. Ajouter le formulaire d'édition d'objectif appelant `PUT {{ENDPOINT}}/objectif`.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes" (entrée 1, mise à jour 2026-08-03).
