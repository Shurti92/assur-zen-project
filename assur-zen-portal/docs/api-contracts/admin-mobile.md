# Contrat API — Administration de l'application mobile (back-office)

Livré le 2026-11-03 sur le gateway dev. Base : `/api-assur-zen/admin/mobile/`. Rôle ADMIN obligatoire (403 sinon).
Format d'erreur : `{ code, message, timestamp, field? }`. Les listes de demandes renvoient au plus les 200 plus récentes.

## 1. Comptes clients (rôle CLIENT)
| Méthode | Chemin | Rôle |
|---|---|---|
| GET | `/accounts?q=&active=&page=0&size=20` | Liste paginée. `q` : numéro ou nom. Réponse `{items:[{userId, phone, fullName, active, createdAt, linkedInsuredCount, pendingDeletionReference}], page, size, totalElements, totalPages}` |
| GET | `/accounts/{userId}` | `{account, linkedInsureds:[{insuredId, name, phone}]}` ; 404 `ACCOUNT_NOT_FOUND` |
| POST | `/accounts` | Crée un compte pour un assuré existant. Corps `{insuredId, phone?, sendInvitation?}` (phone par défaut = téléphone de l'assuré ; sendInvitation par défaut true). 201 `AccountDetail`. Erreurs : 404 `INSURED_NOT_FOUND`, 400 `PHONE_INVALID`, 409 `PHONE_ALREADY_REGISTERED` |
| PATCH | `/accounts/{userId}/active` | `{active:boolean, comment?}` ; désactiver coupe les sessions |
| POST | `/accounts/{userId}/insureds` | `{insuredId}` : rattache un assuré (le client voit alors ses contrats et sinistres) |
| DELETE | `/accounts/{userId}/insureds/{insuredId}` | Détache un assuré (corrige un rattachement erroné, numéro recyclé) |

Un compte créé par l'administrateur n'a **aucun PIN connu**. Le client reçoit un SMS d'invitation et définit son PIN via « PIN oublié » dans l'application. Les assurés partageant le même numéro sont rattachés automatiquement (même règle que l'inscription par le client).

## 2. Demandes de suppression de compte
- `GET /deletion-requests?statut=EN_ATTENTE|TRAITEE|REFUSEE` → `[{id, reference, userId, phone, fullName, statut, requestedAt, processedAt, processedBy, comment}]`
- `POST /deletion-requests/{id}/process` `{decision:"APPROVE"|"REJECT", confirm?, comment?}`
  - APPROVE (irréversible, `confirm:true` obligatoire sinon 400 `CONFIRMATION_REQUIRED`) : le compte est anonymisé (identifiant libéré, nom et téléphone effacés, rattachements retirés) et reste désactivé. **Les contrats et assurés du back-office ne sont pas touchés.** Politique par défaut, à confirmer avec AssurZen (conservation légale).
  - REJECT : demande refusée, compte réactivé.
  - 409 `REQUEST_ALREADY_PROCESSED` si déjà traitée.

## 3. Demandes de modification / résiliation de contrat (faites depuis le mobile)
- `GET /contract-requests?statut=` → `[{id, reference, contractId, policyNumber, insuredName, userId, phone, type MODIFICATION|TERMINATION, message, statut, createdAt, processedAt, processedBy, comment}]`
- `POST /contract-requests/{id}/process` `{statut:"TRAITEE"|"REFUSEE", comment?}`. N'altère pas le contrat : l'avenant ou la résiliation se font dans le back-office.

## 4. Demandes de rappel (avec ou sans compte)
- `GET /callbacks?statut=` → `[{id, reference, phone, slot, productId, userId, statut, createdAt, processedAt, processedBy, comment}]`
- `POST /callbacks/{id}/process` `{statut:"RAPPELE"|"ANNULE", comment?}`
- Configuration optionnelle : clé `CALLBACK_EXPECTED_MINUTES` (délai annoncé au client ; absent = aucun engagement affiché).

## 5. Écrans suggérés
Liste des comptes mobile avec recherche, fiche compte (assurés rattachés, activer/désactiver), file « Demandes mobile » à onglets (suppressions, contrats, rappels) avec statut EN_ATTENTE par défaut.

## 6. Notes
- Toutes les actions sont tracées dans l'audit (module MOBILE).
- Un rappel ou une demande de contrat déjà traités ne peuvent plus être modifiés (409).
- Tables : `t_assur_zen_users` (rôle CLIENT), `t_assur_zen_profiles`, `t_assur_zen_client_account_insured`, `t_assur_zen_account_deletion_request`, `t_assur_zen_contract_customer_request`, `t_assur_zen_callback_request`.

## 7. Catalogue mobile paramétrable (ajouté le 2026-11-03)
Le catalogue affiché par l'application (`GET /public/products`, sans compte) est piloté depuis le back-office. Un produit n'apparaît sur le mobile que s'il est **actif ET marqué visible** ; sans contenu mobile enregistré, il est invisible. Aucun texte n'est écrit en dur côté serveur ou mobile.

| Méthode | Chemin | Rôle |
|---|---|---|
| GET | `/admin/mobile/products` | Tous les produits du back-office avec leur contenu mobile. `configured:false` = aucun contenu enregistré (produit invisible) |
| GET | `/admin/mobile/products/{productId}` | Contenu mobile d'un produit |
| PUT | `/admin/mobile/products/{productId}` | **Remplace** tout le contenu mobile du produit (champs absents effacés) |
| PUT | `/admin/mobile/products/order` | `{productIds:[4,1,6,...]}` : fixe l'ordre d'affichage (10, 20, 30...) |

Corps du PUT produit : `{visibleMobile (obligatoire), shortName?, family? VEHICLE|PERSON|PROPERTY|PROFESSIONAL, description? (≤2000), benefits? (≤10 textes de ≤200), howItWorks? (≤4000), eligibility? (≤2000), guarantees? (≤20 de {name, kind MANDATORY|INCLUDED|OPTIONAL|TO_CONFIRM}), icon? (minuscules, chiffres, - ou _, ≤40), displayOrder? (0-9999), onlineSubscription?}`.
Erreurs : 400 `VALIDATION_FAILED` (avec `field`), 404 `PRODUCT_NOT_FOUND`.
Les **garanties** parametrees alimentent aussi `guarantees` dans le détail d'un contrat mobile (`GET /mobile/contracts/{id}`).
`onlineSubscription` n'a d'effet que si la souscription en ligne existe réellement (devis et paiement réel non livrés) : le laisser à false.
Écran à construire côté portail : liste des produits (case « visible sur le mobile », ordre par glisser-déposer) et fiche d'édition des champs ci-dessus.
