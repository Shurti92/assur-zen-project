# Contrat d'interface — Module Commercial (force de vente)

> ✅ **Livré côté backend le 2026-09-07.** Nouveau module complet : gestion des commerciaux, objectifs
> (annuel + mensuel), performance mensuelle, bonus, dashboards individuel/management, export CSV,
> éléments de préparation paie. 69 tests unitaires, build complet vert.
>
> Aucun développement frontend commencé à ce jour — ce document sert de contrat de départ pour
> l'équipe force de vente (app commerciaux) et de note de passation pour l'équipe backend.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Pour l'équipe "force de vente" (app commerciaux)

### Connexion

Aucun nouveau mécanisme d'authentification : un commercial est un compte back-office standard
(`User`/`Profile`) avec `roles = "COMMERCIAL"`. Login, JWT, refresh token, changement de mot de passe
obligatoire au premier login, verrouillage après échecs : **exactement le même flux que le reste de
l'application**, rien de spécifique à coder côté auth.

### Endpoints principaux (self-service, un commercial connecté)

| Méthode | Chemin | Usage |
|---|---|---|
| `GET` | `/commercials/me` | Sa propre fiche (nom, matricule, localité, **et sa rémunération** — c'est sa propre fiche) |
| `GET` | `/commercials/me/dashboard` | "Mon activité" du mois courant : objectif, réalisé, taux, reste à réaliser, bonus potentiel |
| `GET` | `/commercials/{id}/objectives` | Ses objectifs annuels (id = son propre id, vérifié côté serveur) |
| `GET` | `/commercials/{id}/monthly-objectives` | Ses objectifs mensuels |
| `GET` | `/commercials/{id}/performance` | Historique de sa performance mensuelle (incluant le détail du bonus) |
| `GET` | `/commercial-performance/ranking?moisComptableId=` | Classement de l'équipe (taux d'atteinte uniquement, **jamais de montant**) |
| `GET` | `/contracts` | **Son portefeuille de contrats** — endpoint déjà existant, automatiquement filtré à ses propres contrats (pas de nouvel endpoint dédié) |

### Règle d'affichage importante : le bonus est toujours "estimé" avant validation

`CommercialDashboardResponse.bonusEstime` (et `CommercialPerformanceResponse.statut`) indiquent si le
montant affiché est définitif ou non :

- `statut` en `OPEN`/`CALCULATED`/`ELIGIBLE`/`NOT_ELIGIBLE` → **bonus estimé**, afficher un badge type
  "estimé"/"provisoire", ne jamais le présenter comme acquis.
- `statut` en `VALIDATED`/`PAID`/`CLOSED` → montant définitif, figé, peut être affiché normalement.

Le bonus est toujours calculé sur l'**objectif du mois**, jamais sur le réalisé, même en cas de fort
dépassement (ex. objectif 5M / réalisé 8M → bonus = 5% × 5M, pas 5% × 8M).

### Ce qui reste réservé aux administrateurs (pas d'UI commercial dessus)

Création/modification/activation d'un commercial, définition des objectifs, validation/paiement du
bonus, dashboard management global, export CSV, éléments de paie — tous en `POST`/`PUT`/`PATCH` sur
les mêmes chemins, protégés `hasRole(ADMIN)`. Un compte `COMMERCIAL` qui les appelle reçoit un `403`.

### Données jamais exposées à un autre commercial

`GET /commercials/{id}` où `{id}` n'est pas le sien → `403`. Le classement (`/ranking`) ne renvoie
jamais de salaire ni de montant de bonus, uniquement le taux d'atteinte.

---

## 2. Pour l'équipe backend (passation)

### Ce qui a été réutilisé (pas de doublon créé)

- **`Contract.salesRep`** (déjà existant, `User` non-null) = la notion de "commercial" pour le
  scoping des contrats — aucune nouvelle relation `Commercial → Contract` n'a été ajoutée.
  `CommercialProductionService.computeRealise()` s'appuie dessus (statut `signed`, `signedAt` dans
  les bornes du `MoisComptable`).
- **`VillePartenaire`** = localité du commercial (`Profile.villePartenaire`), pas de nouvelle table.
- **`MoisComptable`/`ExerciceComptable`** = référence de période pour les objectifs mensuels/annuels
  (pas de colonnes `year`/`month` brutes) — la clôture mensuelle automatique existante
  (`ExerciceComptableScheduler`) verrouille aussi implicitement la performance commerciale.
- **Moteur d'audit fonctionnel existant** (`FunctionalAuditRecorder`) pour l'historique des révisions
  d'objectifs — pas de table `commercial_objective_history` dédiée.
- **`UserProfileService.create()`** pour la création de compte (mot de passe temporaire + SMS de
  bienvenue) — `CommercialService.create()` ne fait que l'appeler avec `roles="COMMERCIAL"`.

### ⚠️ Seed à rejouer manuellement en PROD

`application-prod.yml` a `sql.init.mode: never` — `data.sql` **n'est jamais rejoué en prod**. Les
nouvelles clés/lignes ajoutées à `assur-zen-jpa/src/main/resources/data.sql` pour ce module doivent
être exécutées manuellement en prod (comme cela avait déjà été signalé pour le module Prime Objectif) :

- 3 clés de configuration dynamique : `COMMERCIAL_BONUS_ENABLED` (`true`), `COMMERCIAL_BONUS_RATE`
  (`5`), `COMMERCIAL_BONUS_MIN_ACHIEVEMENT_RATE` (`100`) — table `t_assur_zen_dynamic_configuration`.
- 1 catégorie + 1 modèle de communication SMS : `COMMERCIAL` / `BONUS_COMMERCIAL_PAYE` — tables
  `t_assur_zen_communication_template_category` / `t_assur_zen_communication_template`.

Sans ce seed manuel, le module fonctionne quand même (les valeurs par défaut codées dans
`CommercialPerformanceService` — 5%, 100% — s'appliquent via `DynamicConfigurationService.getX(key,
fallback)`), mais le SMS de paiement de bonus échouera silencieusement (template introuvable, échec
non bloquant) tant que le modèle n'est pas seedé.

### Schéma : `ddl-auto: update`, pas de Liquibase

Comme le reste du projet, les 4 nouvelles tables (`t_assur_zen_commercial`,
`t_assur_zen_commercial_objective`, `t_assur_zen_commercial_monthly_objective`,
`t_assur_zen_commercial_performance`) sont générées par Hibernate au démarrage, pas de script de
migration à jouer séparément.

### Décisions produit déjà tranchées (pas à redemander)

- Annulation d'un contrat après clôture de la période → production acquise, pas de recalcul
  automatique (régularisation manuelle admin si besoin).
- Performance + bonus + snapshot de rémunération fusionnés dans **une seule entité**
  (`CommercialPerformance`) plutôt que 3 tables séparées.
- Métrique de production fixée à `PREMIUM_AMOUNT` uniquement pour cette phase (pas de clé
  `COMMERCIAL_PERFORMANCE_METRIC` — aucune autre métrique n'étant câblée, cette clé aurait été une
  abstraction inutilisée).

### Volontairement non construit (à discuter si besoin réel)

- Pas de vrai fichier `.xlsx` (Apache POI absent du projet) — export CSV uniquement, comme tous les
  autres exports de l'application.
- Pas de journal `commercial_production_entry` ligne-à-ligne (Phase 2 possible si la traçabilité
  contrat-par-contrat devient un besoin réel) — calcul à la volée pour l'instant.
- Pas de permissions granulaires (`COMMERCIAL_VIEW_SALARY`, etc.) — le projet n'a pas cette
  infrastructure, réutilisation du schéma binaire `ADMIN`/propriétaire déjà en place partout ailleurs.

### Endpoints complets

Voir Swagger (`/v3/api-docs`) une fois déployé — tags **"Commerciaux"**, **"Objectifs commerciaux"**,
**"Performance commerciale"**, **"Paie commerciale"**.

---

## Rappel

Module implémenté en une session (2026-09-07), 69 tests unitaires, `mvn clean install` vert. Détails
de conception complets dans l'échange qui a précédé cette livraison (analyse d'architecture,
arbitrages de réutilisation, plan d'implémentation).
