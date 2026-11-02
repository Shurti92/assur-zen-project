# Espace Partenaire « EP 2 » — contrats d'API (2026-10-03)

Besoins exprimés par la refonte Espace Partenaire (lots 2 à 7). Toutes les routes sont préfixées par
`/api-assur-zen/` et exigent un jeton Bearer. Rôles : partenaire terrain (role `user`, rattaché à un
partenaire) et commercial interne (role `commercial`), toujours limités à leur propre périmètre via le JWT.

## 1. Accueil — KPI, tendances, branches, historique

### `GET /dashboard/kpis?period=month|quarter|year|all`

`quarter` et `year` sont nouveaux. Les périodes sont **calendaires (UTC)** : `quarter` = trimestre civil
en cours, `year` = année civile en cours.

**Base de la production (2026-10-04)** : une seule base avec `production-history`, la **date de
signature** = `signedAt`, ou à défaut la date de création (`signedAt` n'est renseigné que s'il est fourni
à la création : 360 contrats signés sur 449 ne l'ont pas sur la copie de la prod).

| Découpage | Champs |
|---|---|
| contrats **signés** dans la période (date de signature) | `production`, `signedContracts`, `totalCommission`, `paidCommission`, `pendingCommission`, `partnerShare`, `pendingPartnerShare`, `paidPartnerShare`, `primeNonReversee*`, `previousSignedContracts`, `previousProduction`, `previousPartnerShare`, `repartitionParBranche` |
| contrats **créés** dans la période (`createdAt`) | `totalContracts`, `pendingContracts`, `avenantsCount`, `previousTotalContracts`, `conversionRate` (signés parmi les créés de la période, jamais > 100 %) |

`period=all` : tous les contrats, inchangé.

Champs ajoutés en fin de réponse (les anciens sont inchangés) :

| Champ | Type | Sens |
|---|---|---|
| `previousTotalContracts` | number \| null | contrats de la période précédente de même durée (mois, trimestre ou année civile précédents) |
| `previousSignedContracts` | number \| null | contrats `signed` de la période précédente |
| `previousProduction` | number \| null | production (somme des primes `signed`) de la période précédente |
| `previousPartnerShare` | number \| null | `partnerShare` des contrats `signed` de la période précédente |
| `repartitionParBranche` | array | production de la période par branche (ProductType) |

Les quatre `previous*` valent `null` pour `period=all`. Tendance côté front : `(production - previousProduction) / previousProduction`,
à ne pas calculer si `previousProduction` vaut 0 ou `null`.

`repartitionParBranche[]`, triée par production décroissante, seulement les contrats `signed` de la période :

```json
{ "brancheId": 1, "code": "AUTO", "branche": "Automobile", "couleur": "#2563eb",
  "nbContrats": 2, "production": 400000, "part": 0.8 }
```

`part` est une fraction 0..1 de la production de la période (4 décimales). Un contrat sans branche est
regroupé sous `brancheId: null`, `branche: "Sans branche"`. `couleur` peut être `null`.

### `GET /dashboard/production-history?months=6`

Nouveau. `months` est borné entre 1 et 24 (défaut 6). Renvoie les `months` derniers **mois comptables déjà
commencés**, du plus ancien au plus récent. Production = primes des contrats `signed` dont la date de
signature (`signedAt`, à défaut `createdAt`) tombe dans le mois comptable : même base que `kpis`.

```json
[
  { "moisComptableId": 12, "mois": "2026-10", "moisComptableLabel": "Octobre 2026",
    "dateDebut": "2026-10-01", "dateFin": "2026-10-31",
    "production": 300000, "nbContrats": 1,
    "objectif": 1000000, "objectifSource": "ANNUEL_PRORATISE", "enCours": true }
]
```

| Compte connecté | Production | Objectif |
|---|---|---|
| Partenaire | ses contrats `signed` | `objectifProduction / nombre de mois de son exercice`, seulement pour les mois de cet exercice ; `objectifSource = "ANNUEL_PRORATISE"` |
| Commercial interne | sa performance mensuelle (figée une fois le mois validé ou payé ; base du bonus, `signedAt` seul, sans repli) | son objectif mensuel `PREMIUM_AMOUNT` ; `objectifSource = "MENSUEL"` |
| Autre compte interne | ses contrats en tant que `salesRep` | `null` |

`objectif` et `objectifSource` valent `null` quand aucun objectif ne s'applique (pas d'objectif saisi,
mois hors exercice du partenaire).

## 2. Commissions — relevés mensuels

Réservé au partenaire lui-même et aux administrateurs (`403` pour un autre partenaire ou un commercial
interne). Lecture seule. Un relevé = un **mois calendaire (UTC) de versement**, seulement les mois ayant
au moins un versement.

Argent réellement versé dans le mois = commissions des contrats payés dans le mois, moins la part déjà
versée auparavant en avance (imputée), plus les avances (demandes de paiement partiel) payées dans le mois.

### `GET /partenaires/{id}/commission-statements`

Du plus récent au plus ancien, sans lignes :

```json
[
  { "mois": "2026-09", "nbContrats": 2, "montantCommissions": 2500, "avancesImputees": 1000,
    "avancesVersees": 7200, "montantVerse": 8700, "dateVersement": "2026-09-27T10:00:00Z",
    "statut": "CLOTURE", "lignes": null }
]
```

`statut` : `EN_COURS` (mois calendaire courant, peut encore évoluer) ou `CLOTURE`.

### `GET /partenaires/{id}/commission-statements/{mois}`

`mois` au format `AAAA-MM`. Même objet, avec `lignes` triées par date (`404` si aucun versement ce mois-là,
`400` si le mois est mal formé) :

```json
{ "date": "2026-09-27T10:00:00Z", "type": "COMMISSION", "contractId": 1, "policyNumber": "CTR2026000001",
  "montant": 1500, "avanceImputee": 1000, "montantVerse": 500, "reference": "SYC2026000077" }
```

`type` : `COMMISSION` (paiement d'un contrat) ou `AVANCE` (demande de paiement partiel payée :
`contractId`/`policyNumber` à `null`, `reference` = `"Demande #<id>"`).

### `GET /partenaires/{id}/commission-statements/{mois}/pdf`

Le même relevé en PDF (`application/pdf`, pièce jointe `releve-commissions-AAAA-MM.pdf`).

### Statuts des demandes de paiement (rappel)

Les historiques de demandes (partenaire : paiement partiel ; commercial : `TOTAL`/`PARTIAL`) renvoient
`status` parmi `pending`, `processing` (virement SycaPay lancé, en attente de confirmation), `paid` et
`failed` (virement en échec, montant toujours réservé, relançable par l'administrateur). **Il n'existe
pas de statut `rejected`** : une demande ne peut pas être refusée côté backend. `DEMANDE_PAIEMENT_ECHEC`
correspond au passage de la demande en `failed`. `sycapayStatus = "REJECTED"` n'apparaît que sur la
réponse de la validation admin (aucune tentative de virement) et ne change pas le statut de la demande.

## 3. Sécurité — historique de connexion, appareils connectés, signalement

Toutes les routes portent sur le **compte connecté** (JWT), sans id d'utilisateur dans l'URL. Elles
vivent sous `/auth/me/` : `401` sans jeton valide.

### `GET /auth/me/login-history?size=20`

Dernières connexions, réussies **et échouées**, les plus récentes d'abord (`size` de 1 à 100) :

```json
[
  { "id": 812, "date": "2026-10-03T08:00:00Z", "resultat": "ECHEC", "motifEchec": "MOT_DE_PASSE",
    "deviceType": "MOBILE", "navigateur": "Chrome", "os": "Android", "ville": "Abidjan", "pays": "Côte d'Ivoire",
    "ip": "41.202.1.1", "signale": false }
]
```

`resultat` : `SUCCES` ou `ECHEC`. Seuls les mots de passe incorrects sur un compte existant sont
enregistrés comme échecs, **à partir de ce déploiement** (avant, seules les réussites étaient
historisées). `ville` / `pays` sont approximatifs (à partir de l'IP) et peuvent valoir `null`.
`deviceType` : `DESKTOP`, `MOBILE`, `TABLET`, `BOT` ou `INCONNU`.

Les vues administrateur (`GET /historique-connexions`, statistiques, `GET /users/{id}/login-history`)
restent limitées aux connexions réussies.

### `POST /auth/me/login-history/{id}/report`

« Ce n'était pas moi ». `204`, idempotent. `404` si la connexion n'appartient pas au compte.
Trace le signalement (`signale: true`), l'inscrit au journal d'audit et alerte les administrateurs sur
Telegram (une seule fois par connexion). Ne déconnecte rien : proposer ensuite « Déconnecter les autres
appareils » et le changement de mot de passe.

### `GET /auth/me/sessions`

Appareils connectés : sessions dont le jeton de renouvellement est encore valide, la plus récemment
active d'abord.

```json
[
  { "id": "6f1c…", "appareil": "DESKTOP", "navigateur": "Edge", "os": "Windows", "ville": "Abidjan",
    "pays": "Côte d'Ivoire", "ip": "41.202.1.1", "connecteeLe": "2026-10-03T07:58:00Z",
    "derniereActivite": "2026-10-03T08:58:00Z", "courante": true }
]
```

- `id` est stable pendant toute la session (il ne change pas à chaque renouvellement du jeton).
- `courante` marque l'appareil qui fait la requête.
- `derniereActivite` = dernier renouvellement du jeton, donc précise à l'heure près environ.
- Sessions ouvertes **avant** ce déploiement : `id` = `"L<nombre>"`, appareil, navigateur et ville à
  `null`, jamais `courante`. Elles disparaissent d'elles-mêmes à la prochaine connexion ou à expiration.

### `DELETE /auth/me/sessions/{id}`

Déconnecte cet appareil : `204`, `404` si la session n'est pas active pour ce compte. Le jeton de
renouvellement est révoqué et le dernier jeton d'accès de la session cesse d'être accepté immédiatement.

### `DELETE /auth/me/sessions?autres=true`

Déconnecte tous les autres appareils, la session courante est conservée. Réponse `{ "count": 2 }`.
`400` sans `autres=true`. `409` si la session courante est inconnue (jeton émis avant ce déploiement) :
se reconnecter, puis réessayer.

## 4. Préférences de notification

Seules les catégories **réellement émises** et que le compte connecté peut recevoir sont renvoyées.
La notification in-app est toujours créée : seuls le push et le SMS se règlent. Sans préférence
enregistrée, tout est activé.

| Catégorie | Compte | Types couverts | SMS réglable |
|---|---|---|---|
| `COMMISSION_GAGNEE` | partenaire | `COMMISSION_GAGNEE`, `COMMISSION_DISPONIBLE` | oui |
| `COMMISSION_VERSEE` | partenaire, commercial | `COMMISSION_VERSEE`, `DEMANDE_PAIEMENT_PAYEE`, `DEMANDE_PAIEMENT_ECHEC`, `COMMISSION_COMMERCIALE_PAYEE` | non (toujours envoyé) |
| `BONUS` | commercial | `BONUS_VALIDE`, `BONUS_PAYE` | non (SMS de bonus payé toujours envoyé) |

Un autre compte (admin...) reçoit une liste vide.

### `GET /notifications/preferences`

```json
[
  { "categorie": "COMMISSION_GAGNEE", "push": true, "sms": true,
    "pushModifiable": true, "smsModifiable": true, "modifiable": true },
  { "categorie": "COMMISSION_VERSEE", "push": true, "sms": true,
    "pushModifiable": true, "smsModifiable": false, "modifiable": true }
]
```

### `PUT /notifications/preferences`

Corps : un tableau `[{ "categorie": "...", "push": false, "sms": false }]` (les champs `*Modifiable`
sont ignorés). Seules les catégories fournies sont modifiées. Un SMS non réglable reste à `true`.
`400` pour une catégorie que le compte ne reçoit pas. Renvoie la liste complète, comme le `GET`.

Effet côté serveur, pour `COMMISSION_GAGNEE` : push coupé → SMS envoyé comme si aucun appareil
n'était abonné ; SMS coupé → ni SMS direct, ni SMS de secours si le push n'atteint aucun appareil.
Pas de catégorie `SECURITE`, `ECHEANCE`, `CONTRAT` ni `NOUVEAUTES` : aucune notification de ce type
n'est émise aujourd'hui.

## 5. Centre de notifications — commercial interne, suppression, objet lié

Le centre de notifications (`/notifications`) sert désormais **le partenaire et le commercial interne**.
Le destinataire est déduit du JWT : le partenaire du compte s'il en a un, sinon le compte utilisateur
lui-même. Un compte sans notification reçoit toujours `200` avec une page vide (`content: []`), jamais `403`.

Routes existantes inchangées : `GET /notifications`, `GET /notifications/unread-count`,
`PUT /notifications/{id}/read`, `PUT /notifications/read-all`. Elles excluent les notifications supprimées.

### `DELETE /notifications/{id}`

Nouveau. `204`. `404` si la notification n'appartient pas au compte connecté ou est déjà supprimée.
La notification disparaît du centre de notifications (et du compteur de non lues) mais reste dans
l'historique administrateur `GET /partenaires/{id}/notifications`.

### Champs ajoutés à chaque notification

| Champ | Type | Sens |
|---|---|---|
| `objetType` | string \| null | `CONTRAT`, `DEMANDE_PAIEMENT`, `PERFORMANCE_COMMERCIALE`, ou `null` (rappel sans objet) |
| `objetId` | number \| null | identifiant de l'objet (`contractId`, id de la demande, id de la performance mensuelle) |
| `route` | string \| null | route interne de l'application EP quand le backend la connaît (`/commissions`), sinon `null` : router à partir de `objetType` / `objetId` |

Les anciennes notifications de commission (avant ce changement) renvoient `objetType: "CONTRAT"` et
`objetId = contractId` quand un contrat est lié.

### Types de notification

| `type` | Destinataire | Déclencheur | `objetType` |
|---|---|---|---|
| `COMMISSION_DISPONIBLE` | partenaire | rappel périodique (existant) | `null` |
| `COMMISSION_GAGNEE` | partenaire | contrat signé (existant) | `CONTRAT` |
| `COMMISSION_VERSEE` | partenaire | commission versée sur Wave (existant) | `CONTRAT` |
| `DEMANDE_PAIEMENT_PAYEE` | partenaire ou commercial | demande de paiement payée (virement confirmé, ou enregistrée hors application) | `DEMANDE_PAIEMENT` |
| `DEMANDE_PAIEMENT_ECHEC` | partenaire ou commercial | virement SycaPay de la demande en échec (relançable par l'administrateur) | `DEMANDE_PAIEMENT` |
| `BONUS_VALIDE` | commercial | bonus mensuel validé (mois éligible) | `PERFORMANCE_COMMERCIALE` |
| `BONUS_PAYE` | commercial | bonus mensuel payé | `PERFORMANCE_COMMERCIALE` |
| `COMMISSION_COMMERCIALE_PAYEE` | commercial | commission(s) commerciale(s) payée(s), une notification par paiement groupé | `CONTRAT` si un seul contrat, sinon `null` |

Il n'existe pas de statut « rejetée » pour une demande de paiement côté backend : seuls `paid` et
l'échec du virement (`failed`) produisent une notification.

Les nouvelles notifications ne déclenchent **aucun SMS supplémentaire** : les SMS existants de ces
événements restent envoyés comme avant. Elles partent aussi en push si le destinataire a un appareil
abonné (`data.route` = `route`, ou `/notifications` à défaut).

Mise en production : `sql/manual/2026-10-03_notifications_commercial.sql` après le premier démarrage.

## 6. « À traiter » — actions traitées, expiration des documents

### Actions marquées comme traitées

La liste « À traiter » est calculée côté front. Le backend mémorise seulement, **par utilisateur
connecté** (JWT), les clés des actions marquées comme traitées. Aucune donnée métier n'est modifiée.

| Route | Réponse |
|---|---|
| `GET /me/actions/traitees` | `["ECHEANCE:123:2026-10-15", "PIECE:9"]`, les plus récentes d'abord |
| `POST /me/actions/{cle}/traitee` | `204`, idempotent |
| `DELETE /me/actions/{cle}/traitee` | `204`, idempotent (même si la clé n'était pas marquée) |

`cle` : 1 à 150 caractères parmi lettres, chiffres, `_`, `:`, `-` et `.` (sinon `400`). Format libre,
choisi par le front, ex. `ECHEANCE:{contractId}:{dateEcheance}`. Au-delà de 2 000 clés pour un même
utilisateur, les plus anciennes sont oubliées.

### Date d'expiration des documents partenaire

- `POST /partenaires/{id}/documents` (multipart `file` + `type`) accepte un champ facultatif
  `dateExpiration` au format `AAAA-MM-JJ`.
- Nouveau `PATCH /partenaires/{id}/documents/{documentId}` avec `{ "dateExpiration": "2027-03-31" }`
  pour la fixer, ou `{ "dateExpiration": null }` pour l'effacer. Renvoie le document.
- Chaque document (`GET /partenaires/{id}/documents`) renvoie en plus `dateExpiration` (date ou `null`)
  et `expire` (`true` si la date est passée, strictement avant aujourd'hui).

Les documents existants ont `dateExpiration: null` : la date doit être saisie pour qu'une pièce expirée
soit détectée. Pas de script SQL : les nouvelles colonnes et la table sont créées au démarrage.

## 7. Contact d'aide — `GET /me/support-contact` (2026-10-04)

```json
{ "nom": "Service partenaires AssurZen", "fonction": null, "telephone": "27 20 00 00 00",
  "email": "partenaires@exemple.ci", "whatsapp": null, "horaires": "Lun-Ven 8h-17h", "source": "SUPPORT" }
```

Aucun gestionnaire n'est rattaché aux partenaires dans le modèle : c'est le **contact support général**,
réglé par environnement dans le back-office (module Configuration, module `ESPACE_PARTENAIRE`) avec les
clés `SUPPORT_NOM`, `SUPPORT_FONCTION`, `SUPPORT_TELEPHONE`, `SUPPORT_EMAIL`, `SUPPORT_WHATSAPP` et
`SUPPORT_HORAIRES`, créées **vides** au démarrage. Un champ non renseigné vaut `null` : masquer la carte
quand `nom`, `telephone` et `email` sont tous `null`. `source = "GESTIONNAIRE"` est réservé à un futur
gestionnaire rattaché (changement de modèle, sur décision de l'utilisateur).

## 8. Validation des documents partenaire (2026-10-04)

Chaque document (`GET /partenaires/{id}/documents`, réponses d'envoi et de modification) porte en plus :

| Champ | Type | Sens |
|---|---|---|
| `statut` | `EN_ATTENTE` \| `VALIDE` \| `REFUSE` | vérification par AssurZen ; `EN_ATTENTE` pour les documents antérieurs |
| `motifRefus` | string \| null | motif du refus |
| `valideLe` | date-heure \| null | date de validation ou de refus |
| `validePar` | string \| null | administrateur qui a validé ou refusé |

- Document envoyé par le partenaire : `EN_ATTENTE`. Envoyé par un administrateur : `VALIDE` d'office.
- `PATCH /partenaires/{id}/documents/{documentId}/validation` (**administrateurs**) avec
  `{ "statut": "VALIDE" }` ou `{ "statut": "REFUSE", "motif": "Photo illisible" }`. Motif obligatoire pour
  un refus (500 caractères maximum), sinon `400`. `403` pour un non-administrateur. Renvoie le document.
  Un refus crée la notification `DOCUMENT_REFUSE` (section 9).
- L'écran back-office de validation relève de l'application Angular (session `assur-zen-core`).

### Documents à vérifier dans la liste des partenaires (2026-10-05, back-office)

- Chaque partenaire de `GET /partenaires` (et de `GET /partenaires/{id}`) porte `documentsEnAttente` (nombre) :
  documents `EN_ATTENTE`, documents antérieurs sans statut inclus.
- Filtre facultatif `GET /partenaires?documentsEnAttente=true` (au moins un document à vérifier) ou `false`
  (aucun), combinable avec `q`, `active`, `type`, `page`, `size`.

### Erreurs de `PATCH .../validation`

| Cas | Statut | `code` | `message` |
|---|---|---|---|
| statut absent ou autre que VALIDE/REFUSE | `400` | `400 BAD_REQUEST` | `statut attendu : VALIDE ou REFUSE` |
| refus sans motif | `400` | `400 BAD_REQUEST` | `Le motif du refus est obligatoire` |
| motif > 500 caractères | `400` | `400 BAD_REQUEST` | `Motif trop long (500 caracteres maximum)` |
| non administrateur | `403` | `403 FORBIDDEN` | `Reserve aux administrateurs` (texte du contrôle d'accès commun) |
| document inconnu pour ce partenaire | `404` | `404 NOT_FOUND` | `Document introuvable: <id>` |

Corps toujours `{ "code", "message", "timestamp" }`. `validePar` est un **nom affichable** (prénom et nom du
profil de l'administrateur, à défaut son identifiant de connexion), comme `uploadedBy`.

### Fichiers acceptés (2026-10-05, correctif de sécurité)

`POST /partenaires/{id}/documents` (partenaire **et** administrateur) n'accepte que **PDF, JPEG et PNG**
authentiques : l'extension (`.pdf`, `.jpg`, `.jpeg`, `.png`), le type déclaré (`application/pdf`,
`image/jpeg`, `image/png`, ou `application/octet-stream`) et les premiers octets du fichier (`%PDF`,
`FF D8 FF`, `89 50 4E 47 0D 0A 1A 0A`) doivent concorder. Le type enregistré est celui déduit des octets ;
le nom de fichier est nettoyé (sans chemin ni caractère de contrôle, 255 caractères au plus).

| Cas | Statut | Corps |
|---|---|---|
| type refusé | `400` | `{ "code": "DOCUMENT_TYPE_NON_AUTORISE", "message": "Format de fichier non autorise : seuls les fichiers PDF, JPG et PNG sont acceptes", "timestamp": ... }` |
| fichier > 5 Mo | `400` | `{ "code": "FICHIER_TROP_VOLUMINEUX", "message": "Fichier trop volumineux (5 Mo maximum)", "timestamp": ... }` |

`FICHIER_TROP_VOLUMINEUX` vaut pour tous les envois de fichiers du serveur (limite multipart globale de 5 Mo).
Attribut `accept` conseillé : `.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png`.

Téléchargement (`GET /partenaires/{id}/documents/{documentId}`) : type contrôlé, `X-Content-Type-Options: nosniff`,
affiché en ligne pour PDF/JPEG/PNG ; tout autre type (fichier antérieur au contrôle) est servi en pièce jointe
`application/octet-stream`.

## 9. Nouvelles notifications : échéances, primes, documents, sécurité (2026-10-04)

In-app + push selon les préférences, **jamais de SMS**.

| `type` | Destinataire | Déclencheur | Catégorie | `objetType` / `objetId` |
|---|---|---|---|---|
| `ECHEANCE_PROCHE` | partenaire | contrat signé dont l'échéance est dans 30 ou 7 jours | `ECHEANCE` | `CONTRAT` / id du contrat |
| `PRIME_NON_REVERSEE` | partenaire | prime toujours non reversée N jours après la signature (N = `PRIME_NON_REVERSEE_RAPPEL_JOURS`, 7 par défaut), une seule fois | `PRIME_A_REVERSER` | `CONTRAT` / id du contrat |
| `DOCUMENT_EXPIRE_BIENTOT` | partenaire | document expirant dans 30 ou 7 jours | `DOCUMENT` | `DOCUMENT` / id du document |
| `DOCUMENT_EXPIRE` | partenaire | document expirant aujourd'hui | `DOCUMENT` | `DOCUMENT` / id du document |
| `DOCUMENT_REFUSE` | partenaire | refus d'un document par un administrateur (immédiat) | `DOCUMENT` | `DOCUMENT` / id du document |
| `NOUVELLE_CONNEXION` | partenaire ou commercial | connexion réussie depuis un appareil jamais vu sur le compte (type d'appareil + famille de navigateur + système, versions ignorées ; pas d'alerte à la toute première connexion) | `SECURITE` | `CONNEXION` / id dans `GET /auth/me/login-history` |

- `route` (2026-10-05) : `/contracts/{objetId}` pour `ECHEANCE_PROCHE` et `PRIME_NON_REVERSEE`, `/documents`
  pour `DOCUMENT_*`, `/profile` pour `NOUVELLE_CONNEXION`. Tous les types ont désormais une route :
  `/commissions` (commissions, demandes de paiement, commission commerciale), `/performance` (`BONUS_*`).
- Push : `data` = `{ type, reference (id de la notification), route, objetType?, objetId? }` ; `objetType` et
  `objetId` (texte) seulement quand la notification porte sur un objet.
- Les **rappels planifiés** (échéances, primes, documents) passent chaque jour à 8h (Abidjan) et sont
  **désactivés par défaut** : clé `NOTIFICATIONS_RAPPELS_AUTO_ENABLED` à mettre à `true` en back-office.
  `DOCUMENT_REFUSE` et `NOUVELLE_CONNEXION` sont toujours actifs.

Catégories de `GET /notifications/preferences` désormais renvoyées :

| Compte | Catégories |
|---|---|
| partenaire | `COMMISSION_GAGNEE`, `COMMISSION_VERSEE`, `ECHEANCE`, `PRIME_A_REVERSER`, `DOCUMENT`, `SECURITE` |
| commercial | `COMMISSION_VERSEE`, `BONUS`, `SECURITE` |

Pour `ECHEANCE`, `PRIME_A_REVERSER`, `DOCUMENT` et `SECURITE` : `sms: false`, `smsModifiable: false`
(aucun SMS). `SECURITE` : `push: true`, `pushModifiable: false`, `modifiable: false` (non désactivable).

Mise en production : `sql/manual/2026-10-04_notifications_rappels_documents.sql` après le premier démarrage.

## 10. Changement de mot de passe : mot de passe actuel incorrect (2026-10-05)

`PATCH /auth/change-password` avec un `currentPassword` faux renvoie désormais **`400`** et non plus `401` :

```json
{ "code": "INVALID_CURRENT_PASSWORD", "message": "Mot de passe actuel incorrect", "timestamp": "..." }
```

Raison : un `401` déclenchait un renouvellement de session inutile côté applications, alors que l'utilisateur est
bien connecté. Aucune session n'est révoquée dans ce cas. Les autres erreurs sont inchangées : `400`
`PASSWORD_REUSED` (mot de passe déjà utilisé), `400` de validation (nouveau mot de passe trop court), et un
changement réussi révoque toujours **toutes** les sessions, la courante comprise.
