# Contrat d'interface — Masquage des contrats anciens non renouvelés (app partenaire)

> ✅ **Implémenté côté backend** (2026-08-23). Vérifié en live avec un compte partenaire réel. Concerne exclusivement `GET /partenaires/{id}/contracts` — endpoint consommé principalement par le **front partenaire** ("Espace Partenaire" / "Force de Vente"), pas le back-office Angular. Documenté ici (dépôt `assur-zen-core`) faute d'accès à ce jour au dépôt du front partenaire, à faire suivre à l'équipe concernée.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Besoin métier

Un partenaire accumule au fil du temps des contrats échus et jamais renouvelés, qui finissaient par polluer sa liste de contrats par défaut. Demande : ne plus les afficher par défaut après 2 mois, tout en gardant la possibilité de les retrouver via une recherche/consultation explicite.

## 2. Ce qui change sur `GET /partenaires/{id}/contracts`

**Comportement par défaut modifié** : les contrats remplissant les 3 conditions suivantes sont désormais **exclus** de la liste, sans action requise côté front :

1. `status = signed`
2. `dateEcheance` renseignée et dépassée de plus de **2 mois** (seuil configurable côté backend, `CONTRACT_NON_RENOUVELE_MASQUAGE_MOIS`, ajustable sans redéploiement)
3. Aucun autre contrat ne le référence comme `contratPrecedent` (= pas renouvelé)

Un contrat échu depuis moins de 2 mois, ou déjà renouvelé (même échu depuis longtemps), **reste visible normalement**.

### Nouveau paramètre pour les revoir

```
GET /partenaires/{id}/contracts?inclureAnciensNonRenouveles=true
```

| Paramètre | Type | Requis | Défaut | Effet |
|---|---|---|---|---|
| `inclureAnciensNonRenouveles` | boolean | non | `false` (absent = masqué) | `true` = comportement d'avant cette livraison, tous les contrats du partenaire réapparaissent |

Combinable avec les paramètres déjà existants (`search`, `status`, `page`, `size`) — par exemple pour retrouver un contrat ancien précis par son numéro de police :

```
GET /partenaires/1/contracts?search=CTR2026000165&inclureAnciensNonRenouveles=true
```

Aucun autre champ de la réponse ne change (`PageResponse<ContractResponse>`, même forme qu'aujourd'hui) — seul le contenu de `content[]`/`totalElements` varie selon ce paramètre.

### Exemple vérifié en live (compte partenaire réel, 33 contrats au total)

| Appel | `totalElements` |
|---|---|
| `GET /partenaires/1/contracts` | 26 |
| `GET /partenaires/1/contracts?inclureAnciensNonRenouveles=true` | 33 |

---

## 3. Ce que le front partenaire fera dès livraison confirmée

1. Ne rien changer sur l'appel par défaut (`GET /partenaires/{id}/contracts` sans le nouveau paramètre) — le masquage est automatique côté serveur.
2. Ajouter une action explicite (ex. bouton/lien "Voir les contrats anciens non renouvelés" ou un filtre dédié dans la recherche) qui relance le même appel avec `inclureAnciensNonRenouveles=true`.
3. Pas de changement de modèle côté front (`ContractResponse` inchangé) — uniquement un paramètre de requête optionnel à ajouter au service Angular qui appelle cet endpoint.

---

## Rappel

Portée strictement limitée à `GET /partenaires/{id}/contracts` — n'affecte ni `GET /contracts` (back-office/admin), ni aucun autre endpoint contrat. Suivi dans la mémoire projet "avenant module implementation" (session du 2026-08-23).
