# Devis en ligne : contrat d'API (moto, lots 1 et 2)

Base : `/api-assur-zen`. Montants en F CFA entiers. Erreurs : `{code, message, timestamp, field?}`.
Produit livré : **Moto**, formule **Tiers Simple Moto** (`tiers-simple-moto`), 12 mois, total **8 000 F** (prime nette 6 447 provisoire, taxes 925, accessoires 500, FGA 128). Tarif identique pour MOTO et TRICYCLE. Cylindrée, marque et modèle sont enregistrés mais n'influencent pas le prix.

## Public (sans compte)
- `GET /public/quotes/moto/form` : `{productKey, vehicleTypes[MOTO,TRICYCLE], durations[{months,total}], formulaId, formulaName, minEffectiveDate, brands[]}`. `brands` = liste déroulante des marques (clé de configuration `QUOTE_MOTO_BRANDS`, séparée par des virgules, modifiable dans l'écran de configuration du portail) ; vide = saisie libre. `brand` reste une chaîne libre dans les appels de simulation et de création.
- `POST /public/quotes/moto/simulate` : ne stocke rien.

Corps (simulation et création) : `{vehicleType: MOTO|TRICYCLE, cylinderCc, brand, model, durationMonths, registrationNumber?, chassisNumber?, effectiveDate, formulaId?}`.
Règles : immatriculation OU châssis obligatoire ; `effectiveDate` >= aujourd'hui ; durée présente dans la grille. 400 `VALIDATION_FAILED` avec `field`.

Réponse simulation : `{productKey, formulaId, formulaName, vehicleType, durationMonths, effectiveDate, expiryDate, premium{netPremium,taxes,accessories,fga,total,currency:"XOF"}, guarantees[{label, details[{label,amount}]}]}`.

## Client connecté (rôle CLIENT)
- `POST /mobile/quotes/moto` (201) : enregistre le devis, prix figé. Réponse `QuoteView`.
- `GET /mobile/quotes` : `{items: QuoteView[]}`, plus récent d'abord.
- `GET /mobile/quotes/{reference}` : détail (404 `NOT_FOUND` si le devis n'est pas à ce client).
- `POST /mobile/quotes/{reference}/accept` : `ACCEPTE` (idempotent) ; 409 `QUOTE_EXPIRED` si périmé.

`QuoteView` : `{reference "DEV-AAAA-NNNNNN", status CALCULE|ACCEPTE|EXPIRE, canAccept, productKey, formulaId, formulaName, vehicleType, cylinderCc, brand, model, registrationNumber, chassisNumber, durationMonths, effectiveDate, expiryDate, validUntil, premium{...}, guarantees[...], createdAt, acceptedAt}`.
Validité : `QUOTE_VALIDITY_DAYS` (défaut 30), plafonnée à la date d'effet. Passage à EXPIRE à la lecture et chaque nuit à 03:15.

## Administration (ADMIN)
- `GET /admin/quotes/tariffs`, `PUT /admin/quotes/tariffs` `{items:[{productKey,formulaId,vehicleCategory,durationMonths,netPremium,taxes,accessories,fga,total,active?}]}` : le total doit égaler la somme des composantes (400 sinon). Ajouter une ligne pour une autre durée l'expose automatiquement dans `/form`.

## Marques du devis moto (ADMIN)
- `GET /admin/quotes/moto/brands` -> `{brands:[...]}` (ordre d'affichage).
- `PUT /admin/quotes/moto/brands` `{brands:[...]}` : remplace la liste ; doublons retirés (casse ignorée), ordre conservé ; une marque : 1 à 60 caractères, sans virgule, point-virgule ni retour à la ligne (400 `VALIDATION_FAILED`, field `brands[i]`) ; 200 marques maximum. Liste vide = saisie libre dans l'application. Stockée dans la clé de configuration `QUOTE_MOTO_BRANDS` ; prise en compte immédiatement par `GET /public/quotes/moto/form` (champ `brands`).

## Back-office (ADMIN et COMMERCIAL) : devis pour un prospect (lot 3)
- `POST /admin/quotes/moto` (201) : `{prospectName, prospectPhone (10 chiffres), vehicle:{...corps de la simulation...}}` -> `QuoteView` (channel `BACKOFFICE`).
- `GET /admin/quotes?statut=CALCULE|ACCEPTE|EXPIRE&q=` : q cherche dans nom, téléphone, référence, immatriculation, châssis. Un COMMERCIAL ne voit que ses devis ; un ADMIN voit tout.
- `GET /admin/quotes/{reference}` : 404 si le devis appartient à un autre commercial.
- `POST /admin/quotes/{reference}/accept` : accord du prospect ; 409 `QUOTE_EXPIRED` si périmé.
`QuoteView` gagne `channel` (MOBILE|BACKOFFICE), `prospectName`, `prospectPhone`, `createdByUserId` (champs additifs, nuls pour le canal MOBILE hormis channel).
Pas de remise sur la moto. `/admin/quotes/tariffs` reste réservé aux ADMIN.

## À venir
Statut CONVERTI : conversion d'un devis accepté en contrat, avec le paiement dans l'application (API de paiement à fournir par l'utilisateur).
