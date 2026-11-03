# Demande à la session Back-Office (portail) — administration de l'application mobile

De : Session Gateway AssurZen. À : Session Back-Office (assur-zen-core, portail Angular).
Date : 2026-11-03. Priorité : à planifier avec l'utilisateur ; les API sont **déjà disponibles et testées** sur le gateway dev.

## Contexte
L'application mobile AssurZen (clients) est branchée sur le gateway. Le personnel doit pouvoir, depuis le portail :
1. paramétrer le catalogue affiché dans l'application,
2. gérer les comptes clients de l'application,
3. traiter les demandes envoyées depuis l'application.
Tout le contrat d'API est dans **`docs/api-contracts/admin-mobile.md`** (dépôt du gateway). Base : `/api-assur-zen/admin/mobile/`. Rôle ADMIN obligatoire, jeton Bearer comme le reste du portail. Erreurs au format `{ code, message, timestamp, field? }`.

## Écrans à construire (par ordre de valeur)

### 1. Catalogue mobile (demandé par l'utilisateur)
Écran « Produits sur le mobile » :
- Liste de tous les produits : `GET /admin/mobile/products` → titre, `configured`, `visibleMobile`, ordre, icône.
- Case à cocher « visible sur le mobile » et réordonnancement par glisser-déposer → `PUT /admin/mobile/products/order` `{productIds:[...]}` (le premier de la liste est affiché en premier).
- Fiche d'édition d'un produit → `PUT /admin/mobile/products/{productId}` (remplace TOUT le contenu) : visibilité, nom court, famille (VEHICLE/PERSON/PROPERTY/PROFESSIONAL), description (≤2000), avantages (liste, ≤10 × 200), « comment ça marche » (≤4000), éligibilité (≤2000), garanties (liste de `{name, kind}` avec kind MANDATORY / INCLUDED / OPTIONAL / TO_CONFIRM), icône (code : car, moto, taxi, health, home, plane… minuscules), souscription en ligne (laisser à false : la souscription en ligne n'existe pas encore).
- Point d'attention : un produit sans contenu enregistré est **invisible** sur le mobile. Sur recette et prod, le catalogue est vide tant que le personnel n'a pas rendu des produits visibles : prévoir un message d'aide sur l'écran.
- Les garanties saisies ici apparaissent aussi dans le détail de chaque contrat du client.
- Aucun texte commercial n'a été saisi : c'est AssurZen qui rédige le contenu.

### 2. Comptes clients de l'application
- Liste paginée avec recherche (`q` : numéro ou nom) et filtre actif/inactif : `GET /admin/mobile/accounts?q=&active=&page=&size=`.
- Fiche compte : `GET /admin/mobile/accounts/{userId}` → assurés rattachés.
- Créer un compte pour un assuré existant : `POST /admin/mobile/accounts` `{insuredId, phone?, sendInvitation?}`. Aucun PIN n'est défini : le client reçoit un SMS et choisit son PIN via « PIN oublié ». 409 si le numéro a déjà un compte.
- Activer / désactiver : `PATCH /admin/mobile/accounts/{userId}/active` `{active, comment?}` (la désactivation coupe les sessions).
- Rattacher / détacher un assuré : `POST /accounts/{userId}/insureds` `{insuredId}` et `DELETE /accounts/{userId}/insureds/{insuredId}`. Utile pour corriger un rattachement erroné (numéro de téléphone recyclé ou partagé).
- Bouton « Créer un compte mobile » à proposer sur la fiche d'un assuré existant.

### 3. File « Demandes mobile » (onglets, EN_ATTENTE par défaut)
- **Rappels** : `GET /admin/mobile/callbacks?statut=` puis `POST /callbacks/{id}/process` `{statut: RAPPELE|ANNULE, comment?}`. Afficher numéro, créneau (ASAP / MORNING / AFTERNOON / EVENING), produit.
- **Demandes de contrat** (modification / résiliation) : `GET /admin/mobile/contract-requests?statut=` puis `POST /contract-requests/{id}/process` `{statut: TRAITEE|REFUSEE, comment?}`. Le traitement ne modifie pas le contrat : lien vers la fiche contrat pour faire l'avenant ou la résiliation.
- **Suppressions de compte** : `GET /admin/mobile/deletion-requests?statut=` puis `POST /deletion-requests/{id}/process` `{decision: APPROVE|REJECT, confirm?, comment?}`. APPROVE est irréversible (comptes anonymisés, contrats conservés) : demander une confirmation explicite et envoyer `confirm:true`.
- Prévoir un compteur de demandes en attente sur le menu.

## Points à faire valider par AssurZen (non bloquants pour l'écran)
- Politique de suppression de compte (conservation légale, anonymisation) : la règle par défaut est décrite dans le contrat.
- Délai de rappel annoncé aux clients : clé de configuration `CALLBACK_EXPECTED_MINUTES` (vide = aucun engagement affiché dans l'application).
- Grille tarifaire pour le futur devis en ligne (aucune pour l'instant).

## Comment tester
Gateway dev : `http://localhost:9190/api-assur-zen/` (HTTP clair en dev, SERVER_SSL_ENABLED=false), Swagger sur `/v3/api-docs`. Connexion administrateur par le flux habituel (login + code OTP lu dans le journal du gateway quand les SMS sont simulés).
Aucune API ne manque pour ces écrans. Si un champ ou une liste manque, dites-le à la session Gateway.
