# Nouveau module — KPI Dashboard (vignettes / sorties de fonds / assurés)

> 🆕 **Module backend complet, livré et testé de bout en bout** (4 endpoints, agrégation SQL, comparaison période précédente). Réservé aux administrateurs.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Endpoints

```
GET /dashboard/vignettes?dateDebut=2026-09-01&dateFin=2026-09-30
GET /dashboard/sorties-fonds?dateDebut=2026-09-01&dateFin=2026-09-30
GET /dashboard/assures?dateDebut=2026-09-01&dateFin=2026-09-30
GET /dashboard/global?dateDebut=2026-09-01&dateFin=2026-09-30
```

`dateDebut`/`dateFin` obligatoires (format ISO `YYYY-MM-DD`), tous les 4 réservés ADMIN (`403` sinon).

## 2. Format commun — `KpiValue<T>`

Chaque indicateur principal est un objet, pas juste une valeur brute :
```json
{ "valeur": 1245, "valeurPrecedente": 1090, "variation": 155, "variationPourcentage": 14.22, "tendance": "HAUSSE" }
```
`tendance` : `HAUSSE`/`BAISSE`/`STABLE`. **`variationPourcentage` peut être `null`** — quand `valeurPrecedente = 0`, la division n'est pas définie mathématiquement ; `tendance` reste calculable depuis le seul signe de la variation absolue. Gérez ce cas côté affichage (ex. afficher "—" plutôt qu'un pourcentage).

La "période précédente" est toujours la même durée, immédiatement avant `dateDebut` (ex. période du 1er au 30 septembre → précédente = 2 août au 31 août).

## 3. `/dashboard/vignettes`

20 indicateurs (volume/financier/performance) + 5 répartitions (`parTypeDemande`, `parCylindree`, `parAgeMoto`, `parZoneLivraison`, `parStatut`) + évolution temporelle.

**Un champ reste `null` en permanence** : `delaiMoyenTraitementJours`. Le workflow paiement/édition/livraison de la vignette n'a pas encore de service derrière (voir le contrat `vignette-moto.md`) — aucune date de transition n'existe au-delà de la création. Idem, `tauxTransformation`/`tauxEdition`/`tauxLivraison` resteront à 0% tant que cette phase n'est pas construite — pas un bug, juste l'état réel des données.

**Définition retenue pour `demandesEnAttente`** (pas précisée dans la demande initiale) : tout ce qui n'est ni livré, ni rejeté, ni annulé — `BROUILLON` inclus.

## 4. `/dashboard/sorties-fonds`

20 indicateurs + trésorerie (`totalEncaissements`, `solde`, `ratioSortiesEncaissements` — via les paiements de primes, seule donnée d'encaissement existante) + 3 répartitions (`parType`, `parBeneficiaire`, `parStatut`) + évolution.

**`montantPlusGrosseSortie` n'est jamais un `KpiValue`** — juste un montant brut (un maximum instantané n'a pas de sens comparé à une période précédente comme une somme).

**Attention à l'interprétation de `validees`/`executees`** : les KPI de volume donnent l'état courant (combien de sorties sont *actuellement* dans cet état). Le `tauxExecution`, lui, compare exécutées à (validées + déjà exécutées) — pas à validées seules, sinon le taux pourrait dépasser 100% de façon absurde pour toute sortie qui a fini par être décaissée.

## 5. `/dashboard/assures`

9 indicateurs + 2 répartitions (`parVille`, `parTypeClient`, non filtrées par période — portrait structurel) + évolution cumulative.

**Limite du modèle de données à connaître** : la table des assurés n'a aucune date de création propre. `nouveauxAssures` est déduit de la date du **premier contrat** de l'assuré — un proxy, pas une vraie date d'inscription. `assuresActifs`/`assuresPlusieursContrats`/`assuresMultiEquipes` sont des états courants reconstruits à une date de référence pour permettre une vraie comparaison période précédente (sinon on aurait toujours comparé "maintenant" à "maintenant").

**Deux notions distinctes à ne pas confondre** : `assuresPlusieursContrats` (2+ contrats, même produit ou non) ≠ `assuresMultiEquipes` (2+ contrats sur des produits **distincts** — c'est celui-ci qui alimente `tauxMultiEquipement`).

⚠️ **Donnée à connaître avant d'afficher ces chiffres en démo/prod** : en testant ce module, on a découvert que la base de test actuelle a des contrats référençant des assurés qui n'existent plus dans la table — `assuresActifs` peut dépasser `totalAssures` sur cette base précise. Ce n'est pas un bug du dashboard (il reflète fidèlement les données), c'est un problème de qualité de données préexistant, à nettoyer séparément si besoin.

## 6. `/dashboard/global`

Compose uniquement les 3 sections `kpis` des dashboards ci-dessus (`assures`, `vignettes`, `sortiesFonds`) — pas de répartitions/évolution ici, allez chercher le dashboard spécialisé pour le détail.

## 7. Évolution temporelle — granularité automatique

- Période ≤ 31 jours → point par **jour**
- Période ≤ ~6 mois → point par **semaine** (format `AAAA-Sxx`, semaine ISO)
- Au-delà → point par **mois** (format `AAAA-MM`)

Pas de paramètre pour forcer une granularité différente pour l'instant — à demander si besoin.

## 8. Erreurs

| Code | Cas |
|---|---|
| `400 DATE_PERIODE_INVALIDE` | `dateDebut`/`dateFin` manquant ou `dateDebut > dateFin` |
| `400 INVALID_PARAMETER` / `MISSING_PARAMETER` | date malformée ou paramètre absent |
| `403` | compte non-admin |

Une période sans aucune donnée ne renvoie **jamais** d'erreur — juste des zéros partout (`200`).

## 9. Pas encore livré (mentionné en amont, pas dans ce lot)

Filtres additionnels (`statut`, `produit`, `partenaire`, `ville`, `canal`) au-delà de `dateDebut`/`dateFin` — auraient demandé de modifier chaque requête d'agrégation, pas fait pour cette livraison. Dites-nous si un filtre précis est bloquant, on l'ajoute ciblé plutôt que tous d'un coup.

---

## Rappel

Swagger à jour sur `/swagger-ui/index.html` (tags "Dashboard - Vignettes/Sorties de fonds/Assures/Global"). Tests complets côté backend (46 tests dédiés + 344 tests de régression, 0 échec).
