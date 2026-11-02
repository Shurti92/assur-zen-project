# Réponse backend — Module Commercial, les 3 points de `module-commercial-retour-frontend.md`

> ✅ Réponse au message frontend du 2026-09-08. Les 3 points sont tranchés/vérifiés ci-dessous — 2 par lecture directe du code déjà livré (le point 1 était en fait déjà répondu par une livraison du 08/09 non encore documentée côté contrat), le point 2 par l'ajout d'un garde-fou qui n'existait pas encore.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Paiement partiel de commission pour un commercial interne — **tranché : fonctionnalité non prévue, à retirer**

Un vrai modèle de commission par contrat pour le commercial interne existe bien, livré le 2026-09-08 sous le contrat "Commission commerciale interne" (colonnes `commercial_commission_*` sur `Contract`) — donc la réponse à "un commercial touche-t-il une commission par contrat en plus de son salaire+bonus ?" est **oui**. Mais c'est un module volontairement allégé (commentaire dans `CommercialCommissionController` : *"mirroir volontairement allege de CommissionController (pas de demande de paiement partiel/avance, non demandee pour ce lot)"*) : **pas de demande de paiement partiel/avance pour le commercial interne, contrairement au partenaire.**

L'endpoint hypothétique `GET`/`POST /commercials/{id}/commissions/partial-payment-requests` sur lequel le bouton est câblé **n'existe pas et n'est pas prévu**. Merci de retirer ce bouton de l'onglet "Portefeuille" du commercial interne.

En remplacement, deux endpoints réels existent déjà pour le suivi (lecture seule, pas d'avance) :

- `GET ${env.ws}commercials/me/commissions` → `CommercialCommissionSelfResponse` :
  ```
  { pendingContractsCount, paidContractsCount, totalPending, totalPaid,
    contracts: [ { contractId, policyNumber, signedAt, commercialCommissionAmount, commercialCommissionStatus } ] }
  ```
- Côté admin : `GET ${env.ws}commercial-commissions/commercials`, `GET .../commercials/{commercialId}/contracts`, `POST .../pay`, `GET .../payments` (paiement en lot, pas de demande d'avance individuelle).

Si une fonctionnalité d'avance pour le commercial interne est bien voulue à terme, c'est une nouvelle demande produit — pas un oubli de cette livraison — merci de la formaliser séparément si besoin.

## 2. Rôle `COMMERCIAL` sur un compte partenaire — **confirmé, et maintenant appliqué en garde-fou (pas seulement une convention)**

Vérification du code : `roles` sur `User` était un champ libre, et la résolution de `partenaireId` (`AuthService.buildUserResponse()`) se base sur la relation réelle `Partenaire.profile`, indépendante du champ `roles`. **Rien n'empêchait explicitement les deux à la fois avant aujourd'hui** — techniquement possible via `PUT /users/{id}` (rôles) ou `PUT /partenaires/{id}` (rattachement d'un `profileId` déjà utilisé par un commercial).

Un garde-fou symétrique vient d'être ajouté dans les deux services :
- `PUT /users/{id}` avec un rôle contenant `COMMERCIAL` sur un compte déjà rattaché à un `Partenaire` → `409 CONFLICT`.
- `POST`/`PUT /partenaires` avec un `profileId` pointant vers un compte dont les rôles contiennent `COMMERCIAL` → `409 CONFLICT`.

Le signal `user.role === 'commercial'` est donc désormais fiable pour la bascule d'interface — pas besoin d'un second signal (`partenaireId`) comme évoqué en piste de repli dans votre message.

## 3. Noms de champs exacts — diff contre les DTOs réels (Swagger pas encore vérifié séparément, mais ce sont les DTOs qui l'alimentent)

Plusieurs divergences réelles avec les champs supposés dans votre message. Détail complet :

| Endpoint | Supposé côté front | **Réel (DTO)** |
| --- | --- | --- |
| `GET /commercials/me` | `id`, `fullName` (ou `nom`+`prenoms`), `matricule`, `villePartenaireNom`, `remuneration` | `id`, **`firstname`+`lastname`** (pas de `fullName` ni `nom`/`prenoms`), **`employeeCode`** (pas `matricule`), `villePartenaireNom` ✓, **pas de champ `remuneration` unique** — décomposé en `baseSalary`, `transportBenefitEnabled`/`transportBenefitAmount`, `phoneAllowanceEnabled`/`phoneAllowanceAmount` |
| `GET /commercials/me/dashboard` | `objectif`, `realise`, `achievementRate` (0-100), `resteARealiser`, `bonusEstime`, `statut` | **`objectiveAmount`** (pas `objectif`), **`realizedAmount`** (pas `realise`), `achievementRate` ✓ (0-100), `resteARealiser` ✓, **`bonusEstime` est un booléen** (le montant du bonus est `bonusPotentiel`, distinct), `statut` ✓ — bonus supplémentaire présent : `nombreContrats` |
| `GET /commercials/{id}/objectives` | `id`, `exerciceComptableLibelle`, `montant` | `id` ✓, **`exerciceLibelle`** (pas `exerciceComptableLibelle`), **`targetAmount`** (pas `montant`) — plus `objectiveType`, `targetCount` |
| `GET /commercials/{id}/monthly-objectives` | `id`, `moisComptableLibelle`, `montant` | `id` ✓, **`moisLibelle`** (pas `moisComptableLibelle`), **`targetAmount`** (pas `montant`) — plus `moisNumero`, `objectiveType`, `targetCount` |
| `GET /commercials/{id}/performance` | `id`, `moisComptableLibelle`, `objectif`, `realise`, `achievementRate`, `bonus`, `statut` | `id` ✓, **`moisLibelle`**, **`objectiveAmount`**, **`realizedAmount`**, `achievementRate` ✓, **`bonusAmount`** (pas `bonus`), `statut` ✓ — plus `bonusRateSnapshot`, `eligible`, `totalRemuneration`, `calculatedAt`/`validatedAt`/`paidAt` |
| `GET /commercial-performance/ranking` | `commercialId`, `fullName`, `achievementRate`, `rang` | `commercialId` ✓, **`nomComplet`** (pas `fullName`), `achievementRate` ✓, `rang` ✓ — plus `employeeCode` |

`achievementRate` est bien 0-100 partout, confirmé.

---

## Rappel

Garde-fous ajoutés dans `UserProfileService.java` (`rejeterSiRoleCommercialSurCompteRattacheAPartenaire`) et `PartenaireService.java` (`resolveProfileUnique`). Aucun changement de schéma/migration nécessaire pour ces 3 points.
