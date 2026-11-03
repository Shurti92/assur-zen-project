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

## Assurance automobile VTC (`productKey: "automobile-vtc"`)
Formule **Tiers Simple VTC** (`tiers-simple-vtc`), garanties : Responsabilité civile, Défense et Recours. Tarif unique pour les véhicules de **3 CV et plus, 4 à 8 places**, usage public ; durées 1, 3, 6 et 12 mois : **23 800 / 54 160 / 98 732 / 185 667 F**. Frais fixes par tarif : accessoires 2 024, CEDEAO 1 000, attestation 1 000, FGA 280 (taxes 0, non communiquées) ; prime nette = total - 4 304 F. Hypothèses à confirmer : taxes à 0 ; remise calculée sur la prime nette uniquement.

Corps (simulation et création) `VtcSimulationRequest` : `{fiscalPower, energy ESSENCE|DIESEL|HYBRIDE|ELECTRIQUE, brand, model, firstRegistrationYear (<= année en cours + 1), seats, vtcDeclared (booleen obligatoire), platform?, durationMonths (1|3|6|12), registrationNumber? , chassisNumber? (au moins un), effectiveDate (>= aujourd'hui), formulaId?, discountPercent? (back-office uniquement)}`.
Erreurs 400 `VALIDATION_FAILED` + `field` : `seats` (aucun tarif pour cette puissance ou ce nombre de places), `durationMonths`, `discountPercent` (remise interdite hors back-office, ou supérieure au plafond), `firstRegistrationYear`, `effectiveDate`, `registrationNumber`.

`premium` (VTC et moto) : `{netPremium, taxes, accessories, fga, cedeao, attestation, discount, total, currency}` avec `total = netPremium - discount + taxes + accessories + fga + cedeao + attestation` (cedeao, attestation, discount = 0 pour la moto).

Public : `GET /public/quotes/vtc/form` -> `{productKey, energies[], durations[{months,total}], formulaId, formulaName, minEffectiveDate, brands[], platforms[], minFiscalPower, minSeats, maxSeats}` ; `POST /public/quotes/vtc/simulate` -> `{productKey, formulaId, formulaName, durationMonths, effectiveDate, expiryDate, premium, guarantees[]}` (aucune remise).
Client connecté : `POST /mobile/quotes/vtc` (201, `QuoteView`, aucune remise) ; liste, détail et acceptation : mêmes endpoints que la moto (`/mobile/quotes...`).
`QuoteView` gagne (null pour la moto) : `fiscalPower, energy, seats, firstRegistrationYear, vtcDeclared, platform, discountPercent`.

Back-office : `POST /admin/quotes/vtc` (ADMIN et COMMERCIAL) `{prospectName, prospectPhone, vehicle: VtcSimulationRequest}` avec `discountPercent` facultatif, plafonné par `QUOTE_VTC_MAX_DISCOUNT_PERCENT` (10 par défaut) ; liste, détail, acceptation : `/admin/quotes...` (inchangés).
Paramétrage (ADMIN) :
- `GET/PUT /admin/quotes/vtc/brands` `{brands:[...]}` (marques de véhicules, mêmes règles que la moto) ;
- `GET/PUT /admin/quotes/vtc/platforms` `{platforms:[...]}` (50 max, 60 caractères, sans virgule) ;
- `GET /admin/quotes/vtc/discount-limit` -> `{maxDiscountPercent}` : lecture du plafond de remise pour ADMIN **et COMMERCIAL** (pour régler le curseur de l'écran de devis).
- `GET/PUT /admin/quotes/vtc/settings` `{maxDiscountPercent}` (0 à 100 ; 0 = aucune remise).
- `GET/PUT /admin/quotes/tariffs` : lignes VTC avec `vehicleCategory: "VTC"` (suffixe possible pour une autre tranche, ex. `VTC-2`), champs `cedeao`, `attestation`, `minFiscalPower`, `minSeats`, `maxSeats` ; le total doit égaler netPremium + taxes + accessories + fga + cedeao + attestation.

## Envoi du devis par e-mail (back-office : ADMIN et COMMERCIAL)
- `POST /admin/quotes/moto` et `POST /admin/quotes/vtc` acceptent `prospectEmail?` (e-mail valide, 150 max) et `sendEmail?` (true = enregistrer puis envoyer ; un échec d'envoi ne fait pas échouer la création : `emailSentCount` reste 0).
- `POST /admin/quotes/{reference}/send-email` corps facultatif `{email?, message? (500 max, texte brut échappé), attachPdf? (défaut true), copyToMe? (défaut false : copie à l'e-mail du compte connecté)}` -> `QuoteView` à jour. `email` fourni remplace `prospectEmail`.
  Erreurs : 400 `VALIDATION_FAILED` field `email` | `message` | `copyToMe` ; 404 `NOT_FOUND` (devis d'un autre commercial) ; 409 `QUOTE_EXPIRED` ; 429 `TOO_MANY_REQUESTS` (5 envois par devis et par 24 h) ; 502 `MAIL_SEND_FAILED` ; 503 `MAIL_DISABLED` (paramètre `EMAIL_ENABLED` à false).
- `GET /admin/quotes/{reference}/emails` -> `{items:[{sentAt,to,sentBy,withPdf,copyTo}]}`, plus récent d'abord.
- `GET /admin/quotes?email=ENVOYE|NON_ENVOYE` (filtre serveur) ; `q` cherche aussi dans l'e-mail du prospect.
- `QuoteView` gagne `prospectEmail, emailSentCount, lastEmailSentAt, lastEmailTo, lastEmailSentBy`.
- Un devis ACCEPTE peut être renvoyé ; un devis EXPIRE ne peut pas l'être. Le prix envoyé est le prix figé du devis (remise comprise). Chaque envoi est tracé (qui, quand, à qui, avec ou sans PDF).
- Contenu : objet `Votre devis AssurZen {reference} · {produit}`, HTML + PDF joint (`Devis-{reference}.pdf`) généré par le gateway. Mention légale et pied de page modifiables dans la configuration (`QUOTE_EMAIL_LEGAL_TEXT`, `QUOTE_EMAIL_FOOTER`). Expéditeur : paramètre de configuration `QUOTE_EMAIL_FROM` (`customer@assurzen.ci` par défaut ; le compte SMTP utilisé doit être autorisé à envoyer pour cette adresse). Les autres e-mails (campagnes) gardent `MAIL_FROM`.

## À venir
Statut CONVERTI : conversion d'un devis accepté en contrat, avec le paiement dans l'application (API de paiement à fournir par l'utilisateur).
