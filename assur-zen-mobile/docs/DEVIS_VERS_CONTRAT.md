# Chantier « devis vers contrat » : plan côté application

Contrat d'API de référence (dépôt de la Gateway) : `docs/api-contracts/devis-en-ligne.md`, section
« Conversion d'un devis accepté en contrat » (décisions du 2026-11-05).

**État :** plan seulement, aucun code écrit. Les lots 1 et 3 de la Gateway (commits `1c669b0` et `5889e59`)
ne sont ni déployés ni démarrés en recette. À ne coder qu'après (1) la confirmation de la Gateway que la
recette est à jour, (2) l'accord de l'utilisateur.

## Parcours du client

1. Il obtient un devis (moto ou VTC), l'accepte (`POST /mobile/quotes/{reference}/accept`, déjà en place).
2. Sur un devis `ACCEPTE`, un bouton **« Payer et obtenir mon contrat »** lance le paiement Jeko.
3. À la confirmation, le devis passe à `PAYE` : l'écran dit « Paiement reçu, votre contrat est en cours
   d'émission ». Un conseiller finalise ensuite au back-office (numéro de police, attestation).
4. Le devis passe à `CONVERTI` : un lien mène au contrat, qui affiche ses documents et son attestation.

## Ce qui existe déjà et se réutilise

| Besoin | Existant |
| --- | --- |
| Écran de paiement, suivi toutes les 3 s, ouverture de `redirectUrl`, message « La confirmation tarde » | `features/payment/payment_page.dart` |
| Statuts de devis | `MotoQuoteStatus` (`calcule`, `accepte`, `expire`, `converti`) dans `data/models/moto_quote.dart` |
| Liste et détail des devis, acceptation | `features/quote/my_quotes_page.dart`, `moto_quote_page.dart`, `vtc_quote_page.dart` |
| Notification avec lien vers le contrat | `AppNotification.contractId` ; `notifications_page.dart` ouvre déjà le contrat |
| Liste des documents d'un contrat | `ContractDocument`, `_DocumentRow` dans `contract_detail_page.dart` |
| Attestation | `ContractAttestation`, `features/contracts/attestation_page.dart` |

## Changements à faire

### 1. Données (sans impact visible tant que le serveur n'envoie rien)
- `MotoQuoteStatus` : ajouter `paye` (`PAYE`).
- `MotoQuote` : ajouter `conversion` (`status NONE|PENDING|CONFIRMED|FAILED`, `paymentReference`, `paidAt`),
  `contractId`, `policyNumber`. Tout est facultatif : champ absent = rien d'affiché.
- `Endpoints` : `POST /mobile/quotes/{reference}/convert`, `GET /mobile/contracts/{id}/documents/{documentId}`,
  `GET /mobile/contracts/{id}/attestation`.
- `PaymentRepository` : `convertQuote(reference, methodId, phone)` → même `PaymentAttempt` que le renouvellement
  (`redirectUrl`, `expiresAt`, `PENDING`). Le montant vient du serveur (`premium.total`).
- Types de documents : le serveur envoie `ATTESTATION`, `CONDITIONS_PARTICULIERES`, `CARTE_GRISE`, `AUTRE`.
  Aujourd'hui `_docType` ne reconnaît que `CONDITIONS`, `CEDEAO`, `RECEIPT` : ajouter les nouveaux types
  (et `carteGrise` dans `DocumentType`) sans casser les anciens.
- Aucun `verificationCode` à inventer : il vaut `null` pour l'instant.

### 2. Écrans
- **Détail d'un devis** (`ACCEPTE`) : bouton « Payer et obtenir mon contrat » qui ouvre `PaymentPage` avec
  `payableReference` fourni par le serveur et un nouvel indicateur `isQuotePayment`.
- **Devis `PAYE`** : bandeau « Paiement reçu, votre contrat est en cours d'émission. » Pas de second paiement.
- **Devis `CONVERTI`** : bandeau et lien « Voir mon contrat » (`contractId`) ; afficher `policyNumber` s'il existe.
- **Liste « Mes devis »** : ajouter la pastille « Payé » (`PAYE`) ; « Souscrit » reste pour `CONVERTI`.
- **Écran de paiement** : après confirmation d'un devis, le message final est « Paiement reçu, votre contrat est
  en cours d'émission » (et non « Votre contrat est renouvelé »). Jamais « payé » avant `CONFIRMED` du serveur.
- **Erreurs 409** : messages clairs pour `QUOTE_NOT_ACCEPTED` (proposer d'accepter d'abord), `QUOTE_EXPIRED`,
  `QUOTE_ALREADY_PAID`, `PAYMENT_IN_PROGRESS` (suivre la référence `PAY-…` du message au lieu d'en lancer un second),
  `METHOD_DISABLED`, `AMOUNT_MISMATCH` ; 429 et 502 comme pour le renouvellement.

### 3. Documents du contrat
**Point technique important :** `GET /mobile/contracts/{id}/documents/{documentId}` et
`GET /mobile/contracts/{id}/attestation` exigent le jeton de session (en-tête `Authorization`). L'ouverture
actuelle par `Launch.url` (navigateur externe) ne peut donc pas envoyer ce jeton. Il faut télécharger le fichier
avec le client API, l'enregistrer dans un dossier temporaire de l'application, puis l'ouvrir avec une application
de lecture (PDF, image).
- Ajout d'une méthode « octets » au client API (`Uint8List`), qui garde le jeton, les délais et la gestion
  des erreurs (404 pour un document d'un tiers).
- Un package pour enregistrer et ouvrir le fichier (par exemple `path_provider` et `open_filex`) : **à valider**,
  car cela ajoute des dépendances et des permissions Android (voir « Décisions »).
- Le bouton de partage reste désactivé : le lien sécurisé de 48 h n'existe pas côté serveur.

### 4. Notifications
- « Paiement reçu » (catégorie `PAYMENT`) : pas de lien (le contrat n'existe pas encore) ; idéalement ouvrir le devis.
- « Votre contrat est émis » (catégorie `SUBSCRIPTION`, `contractId` renseigné) : ouvre le contrat, déjà géré.

## Ce que je demande à la Gateway
1. Pour la notification « Paiement reçu » : un identifiant ou une référence de devis (`quoteReference`) pour ouvrir
   le bon devis ; sinon l'application ne peut que lister « Mes devis ».
2. Confirmer le format exact de `conversion` et de `documents[]` dans `GET /mobile/quotes/{reference}` (échantillon JSON).
3. Confirmer qu'un devis `PAYE` a `canAccept` faux et un `validUntil` toujours renseigné.
4. Dire quand la recette est à jour, et passer le script `sql/manual/2026-11-05_mobile_paiement_devis.sql`
   avant le premier paiement d'un devis.

## Décisions à prendre (utilisateur)
1. **Ouverture des documents** : télécharger puis ouvrir avec l'application de lecture du téléphone
   (ajout de dépendances), ou afficher le PDF dans l'application (dépendance plus lourde).
2. **Moyens de paiement** : réutiliser la liste du serveur (comme le renouvellement), sans rien coder en dur.
3. **Essais en recette** : le débit Jeko reste plafonné à 100 F, mais le paiement d'un devis fait créer un vrai
   contrat à finaliser au back-office. Préciser quel devis de test utiliser (un devis de démo distinct des dossiers
   réels), comme pour `DEMO-MOB-*`.

## Tests à écrire
Analyse du JSON (statuts `PAYE`, `conversion`, `contractId`, `policyNumber`, documents et types), champ absent masqué,
messages par code d'erreur, écran de paiement d'un devis (confirmation seulement sur `CONFIRMED`), pastilles de la liste,
téléchargement (jeton envoyé, 404 géré), non-régression du renouvellement. Tests de mise en page à 100 % et 130 %.

## Ordre proposé
1. Données et tests de lecture (sans effet visible).
2. Paiement d'un devis accepté et messages d'erreur.
3. Statuts `PAYE` et `CONVERTI` sur le devis et la liste.
4. Téléchargement et ouverture des documents et de l'attestation.
5. Essai de bout en bout en recette, avec la Gateway et l'utilisateur.
