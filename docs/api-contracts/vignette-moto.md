# Nouveau module — Demandes de vignette moto

> 🆕 **Module backend complet, livré et testé de bout en bout** (simulation, création, documents, soumission, garde-fous tarifaires). Aucun paiement/édition/livraison pour cette phase — statuts préparés dans l'enum mais pas de service derrière, comme convenu.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Sécurité

- `POST .../tarification/simuler` et tout `.../demandes/**` : ouvert à **tout compte authentifié** (pas de restriction de rôle particulière).
- `.../tarifs/**` et `.../livraisons/**` (configuration) : **réservés ADMIN**, 403 sinon.

## 2. Simulation de tarif (aucune création, juste un calcul)

```
POST /api-assur-zen/vignettes-moto/tarification/simuler
```
```json
{
  "datePremiereMiseCirculation": "2020-05-15",
  "cylindree": 110,
  "zoneLivraison": "ABIDJAN"
}
```
```json
{
  "categorieAge": "5_10_ANS",
  "ageMoto": 6,
  "categorieCylindree": "MOINS_125_CM3",
  "montantVignette": 3750,
  "fraisOperation": 1500,
  "fraisLivraison": 1500,
  "fraisAccessoires": 3000,
  "montantTotal": 6750
}
```

**`zoneLivraison` est un code choisi directement par l'utilisateur** (ex. `ABIDJAN`, `HORS_ABIDJAN`), pas un texte de ville à deviner côté backend — voir `GET .../livraisons` pour la liste des codes actifs à proposer dans un select. C'est un écart volontaire par rapport à une résolution automatique depuis un texte libre, décidé pour éviter toute ambiguïté (fautes de frappe, variantes de nom de ville).

## 3. Création d'une demande

```
POST /api-assur-zen/vignettes-moto/demandes
```
Champs **plats** (pas d'objets `assure{}`/`moto{}` imbriqués, pour rester cohérent avec le reste de l'API) :
```json
{
  "typeDemande": "NOUVELLE_DEMANDE",
  "nomAssure": "KOUASSI",
  "prenomsAssure": "Jean",
  "adresseLivraison": "Cocody",
  "villeLivraison": "Abidjan",
  "zoneLivraison": "ABIDJAN",
  "marqueMoto": "Yamaha",
  "modeleMoto": "Crypton",
  "numeroChassisMoto": "XXXXXXXXXXXX",
  "cylindreeMoto": 110,
  "datePremiereMiseCirculation": "2020-05-15"
}
```
`typeDemande` : `NOUVELLE_DEMANDE` ou `RENOUVELLEMENT`.

**Important** : le demandeur n'est **pas** rattaché à un client existant (`Insured`), et la moto n'est **pas** rattachée au module Flotte (`Vehicule`) — tout est saisi en clair sur la demande. Décision prise pour couvrir le cas d'un demandeur non-assuré et d'une moto non enregistrée par ailleurs.

Réponse (`201`) : tous les montants sont **calculés et figés immédiatement** à la création — `montantVignette`, `fraisOperation`, `fraisLivraison`, `fraisAccessoires`, `montantTotal` ne bougeront plus jamais, même si la grille tarifaire change ensuite. Statut initial : `BROUILLON`.

## 4. Documents (réutilise le module GED existant)

```
POST /api-assur-zen/vignettes-moto/demandes/{id}/documents?typeCode=CNI_ASSURE   (multipart "file")
GET  /api-assur-zen/vignettes-moto/demandes/{id}/documents
```
`typeCode` : `CNI_ASSURE`, `RECU_ACHAT_MOTO` (toujours obligatoires), `ANCIENNE_VIGNETTE` (obligatoire uniquement pour `RENOUVELLEMENT`). Mêmes contraintes de fichier que le reste du GED (extensions autorisées : pdf, png, jpg, jpeg, docx, xlsx, csv).

## 5. Soumission

```
POST /api-assur-zen/vignettes-moto/demandes/{id}/soumettre
```
`BROUILLON → EN_ATTENTE_PAIEMENT`. Bloqué en **409** tant qu'un document obligatoire manque, avec la liste précise des types manquants dans le message :
```json
{"code":"409 CONFLICT","message":"Document(s) obligatoire(s) manquant(s) avant soumission: CNI_ASSURE, RECU_ACHAT_MOTO"}
```

`PATCH .../{id}/annuler` et `PATCH .../{id}/rejeter` (`{"motif": "..."}`) disponibles aussi — voir le Swagger pour le détail des statuts autorisés en entrée de chaque transition.

## 6. Codes d'erreur métier à gérer côté écran

| Code | Cas |
|---|---|
| `400` | cylindrée ≤ 0, date de mise en circulation dans le futur, champ obligatoire absent |
| `404` | demande/tarif/zone introuvable, **ou tarif désactivé/hors période de validité** (même code, pas de distinction) |
| `409` | tarifs qui se chevauchent (config admin), document obligatoire manquant à la soumission, transition de statut invalide |

## 7. Administration (tarifs et zones — réservé ADMIN)

```
GET/POST/PUT   /api-assur-zen/vignettes-moto/tarifs[/{id}]
PATCH          /api-assur-zen/vignettes-moto/tarifs/{id}/desactiver
GET/POST/PUT   /api-assur-zen/vignettes-moto/livraisons[/{id}]
PATCH          /api-assur-zen/vignettes-moto/livraisons/{id}/desactiver
```
Pas d'endpoint de réactivation pour l'instant (pas demandé initialement) — à ajouter facilement si besoin, même pattern que `ProductType` (`enable`/`disable`).

**⚠️ Rien n'est pré-chargé en base.** Comme le reste du référentiel GED, la grille tarifaire (6 lignes) et les zones de livraison (2 lignes) doivent être créées via ces APIs après déploiement, pas via un seed automatique — cohérent avec le fonctionnement déjà en place pour les types de documents GED. Idem pour les 3 types de documents (`CNI_ASSURE`, `RECU_ACHAT_MOTO`, `ANCIENNE_VIGNETTE`) à créer via `POST /ged/document-types` avant la première utilisation.

---

## Rappel

Swagger complet disponible sur `/swagger-ui/index.html` (tag "Vignettes Moto - ..."), 4 groupes. Tous les endpoints, schémas et exemples y sont à jour.
