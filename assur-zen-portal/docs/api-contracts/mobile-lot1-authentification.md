# Contrat API mobile — Lot 1 : authentification et inscription (PROPOSITION, rien n'est codé)

Statut : proposition de la session Gateway du 2026-11-02, à valider par la session Mobile et par l'utilisateur.
Base : `/api-assur-zen/`. Erreurs : `{ "code", "message", "timestamp" }` (+ `field` optionnel sur `VALIDATION_FAILED`, voir 7).
Les endpoints existants `/auth/login`, `/auth/login/otp-verify`, `/auth/login/otp-resend`, `/auth/refresh`, `/auth/logout`, `/auth/me`,
`/auth/forgot-password`, `/auth/reset-password` ne changent pas de comportement. Les nouveaux endpoints sont sous `/auth/mobile/` pour ne
rien casser côté back-office (les `/auth/otp/*` existants gardent leur contrat actuel).

## 1. Principes
- Identifiant = téléphone `^(01|05|07)\d{8}$` (10 chiffres). Secret = PIN `^\d{6}$`, haché côté serveur (même encodeur que les mots de passe).
- Nouveau rôle **CLIENT** (assuré). Un CLIENT n'a accès qu'à `/auth/**`, `/auth/mobile/**` et, plus tard, aux endpoints `/mobile/**`.
  **Prérequis sécurité** : aujourd'hui de nombreux endpoints back-office sont seulement `authenticated()` (n'importe quel rôle). Avant d'ouvrir
  l'inscription publique, je passe la configuration en liste blanche pour le rôle CLIENT (refus 403 partout ailleurs). Sans cela, un compte créé
  par n'importe qui pourrait lire des données du back-office.
- Pas d'énumération : login, forgot-password et `RESET_PIN` répondent de la même façon que le compte existe ou non. Exception voulue :
  `REGISTER` sur un numéro déjà inscrit répond 409 (demande du mobile), limitée par le quota OTP 3/h.

## 2. POST `/auth/mobile/otp/request` (public)
Corps : `{ "phone": "0700000000", "purpose": "REGISTER" | "RESET_PIN" }`
- 204 : SMS envoyé (RESET_PIN sur numéro inconnu : 204 sans SMS).
- 409 `PHONE_ALREADY_REGISTERED` (REGISTER sur un compte existant).
- 429 `OTP_QUOTA_EXCEEDED` (> 3 demandes/heure/numéro, `retryAfterSeconds`) ; 429 `OTP_RESEND_TOO_SOON` (délai 60 s, `remainingSeconds`).
- 400 `VALIDATION_FAILED` (`field = phone` ou `purpose`).
- Code à 6 chiffres envoyé par SMS, valable 5 min (configurable en base), 5 essais max.

## 3. POST `/auth/mobile/otp/verify` (public)
Corps : `{ "phone", "code": "123456", "purpose" }` → 200 `{ "verificationToken": "<JWT court>", "expiresInSeconds": 600 }`
- Le jeton est lié au téléphone et au purpose, à usage unique, valable 10 min.
- Erreurs : 400 `INVALID_OTP_CODE`, 400 `OTP_EXPIRED`, 400 `OTP_NOT_FOUND`, 429 `OTP_MAX_ATTEMPTS`.

## 4. POST `/auth/mobile/register` (public)
Corps : `{ "fullName": "Awa Koné", "phone", "pin": "123456", "verificationToken" }` (token de purpose REGISTER)
→ 201 `{ "user": {id, fullName, phone, email, role:"CLIENT", ...}, "tokens": {accessToken, refreshToken, tokenType, expiresIn}, "linkedInsured": true|false }`
- Pas d'étape OTP supplémentaire : le jeton prouve la possession du numéro.
- **Rapprochement avec les assurés existants** (créés en agence via `/souscriptions`) : à l'inscription, tous les assurés dont
  `clientPhone` ou `clientPhone2` (normalisé) égale le numéro sont liés au compte (nouvelle table de liaison compte↔assuré).
  `linkedInsured = true` si au moins un assuré a été rattaché. Règle de gestion à valider par l'utilisateur (voir 8).
- Erreurs : 409 `PHONE_ALREADY_REGISTERED`, 401/400 `INVALID_VERIFICATION_TOKEN`, 400 `VALIDATION_FAILED` (`field = pin|fullName|phone`).

## 5. POST `/auth/mobile/pin/reset` (public)
Corps : `{ "phone", "code": "123456", "newPin": "654321" }` → 204 (aussi si le numéro est inconnu, sans effet).
- Le code vient de `otp/request` avec `purpose = RESET_PIN` ; il est vérifié et consommé dans cet appel (pas de jeton intermédiaire).
- Toutes les sessions (refresh tokens) du compte sont révoquées ; le compte verrouillé est déverrouillé.
- Erreurs : 400 `INVALID_OTP_CODE`, `OTP_EXPIRED`, `OTP_NOT_FOUND`; 429 `OTP_MAX_ATTEMPTS`; 400 `PIN_REUSED` si identique au PIN actuel.

## 6. POST `/auth/mobile/me/delete-request` (Bearer, rôle CLIENT)
Corps : `{ "pin": "123456" }` → 202 `{ "reference": "DEL-2026-000123" }`
- Vérifie le PIN, enregistre une demande de suppression (traitement par un administrateur ; délai légal à définir), désactive immédiatement
  les nouvelles connexions. Idempotent : une demande déjà ouverte renvoie la même référence.
- Erreurs : 401 `INVALID_CREDENTIALS` si le PIN est faux (compte à verrouiller après N échecs, règle existante) ; 423 `ACCOUNT_LOCKED`.

## 6 bis. Ajouts validés côté Mobile (2026-11-02) — validation utilisateur toujours attendue
- `POST /auth/mobile/pin/verify` (Bearer, rôle CLIENT) `{ "pin" }` → 204. Aucun jeton ni session créés, pas d'OTP. Un PIN faux compte dans le
  verrouillage (401 `INVALID_CREDENTIALS` avec `remainingAttempts`, 423 `ACCOUNT_LOCKED`). Priorité P1 (redemande du PIN avant paiement et
  au déverrouillage).
- Force du PIN : 400 `PIN_TOO_WEAK` (`field = pin`) pour les PIN triviaux (tous chiffres identiques, suites croissantes/décroissantes
  évidentes comme 123456 ou 654321, 000000, 123123...), à l'inscription et au reset. Le serveur fait foi.
- Clé d'erreur unique `code` : `LoginFailureResponse` (401 du login) et `AccountLockedResponse` (423) renvoient aujourd'hui `error` au lieu de `code`.
  J'ajoute `code` (même valeur) et je garde `error` quelques versions pour le back-office. Vérifié dans le code.
- `register` renvoie aussi `linkedCount` (nombre d'assurés rattachés) en plus de `linkedInsured`. Risque à connaître : un numéro recyclé par
  l'opérateur rattacherait les contrats de l'ancien titulaire au nouveau ; à valider par l'utilisateur.
- Tous les `message` d'erreur de ces endpoints sont en français affichable.

## 7. Ajouts compatibles à l'existant
- `GET /auth/me` et `UserResponse` : ajout du champ `phone` (string, nullable).
- `VALIDATION_FAILED` : ajout du champ `field` (nom du champ en cause, premier si plusieurs) en plus de `message`.
- Login CLIENT : `twoFactorEnabled = false` par défaut pour un compte créé par `register` (le numéro est déjà vérifié) ; PIN verrouillé après
  `MAX_LOGIN_ATTEMPTS` (423 `ACCOUNT_LOCKED`). Le mobile continue de gérer les deux formes de réponse du login.

## 8. Modèle de données (créé par Hibernate `ddl-auto=update` + script SQL de contrôle pour la recette/prod)
- `OtpCode.purpose` (nullable) pour compter le quota par numéro et par purpose.
- Table de liaison compte (User) ↔ assuré (Insured) ; table de demandes de suppression (user, référence, date, statut).
- Rôle `CLIENT` ajouté à la liste des rôles.

## 8 bis. Livré le 2026-11-02 (gateway dev, HTTP 9190) — écarts avec la proposition
- Décisions de l'utilisateur : contrat validé, rattachement automatique par téléphone, 2FA désactivée pour les CLIENT. Politique de suppression : la demande est
  enregistrée (référence `DEL-AAAA-NNNNNN`), le compte est désactivé et les sessions révoquées ; le traitement légal reste à définir.
- Pas de colonne `purpose` sur `OtpCode` : le quota (3/h, `OTP_MAX_REQUESTS_PER_HOUR` en base) est compté par numéro, tous objets confondus.
- Un code de vérification erroné trop de fois reste `400 INVALID_OTP_CODE` (comportement existant), pas `429 OTP_MAX_ATTEMPTS`.
- `pin/reset` consomme le code même si le PIN est refusé pour `PIN_REUSED` (sinon on pourrait tester le PIN actuel sans code) : redemander un code.
- `delete-request` : la 2e demande, après désactivation du compte, répond 401 (jetons révoqués).
- `REGISTER` sur un numéro existant : le quota s'applique avant le 409.
- Le PIN, le nouveau PIN et le code SMS sont masqués dans le journal d'audit des requêtes.
- Liste blanche CLIENT : `ClientRoleRestrictionFilter` (403 hors `/auth/**`, `/mobile/**`, `/i18n/**`).

## 9. Points à décider (utilisateur / mobile)
1. Rapprochement : lier TOUS les assurés partageant le numéro (proposé) ou exiger une confirmation (ex. numéro de contrat) pour éviter qu'un tiers ne prenne
   le numéro d'un assuré ? Recommandation : lier automatiquement car le OTP prouve la possession du numéro.
2. Double authentification des CLIENT à chaque connexion : désactivée par défaut (proposé) ; l'OTP resterait possible à la demande.
3. Suppression de compte : conservation légale des contrats ; anonymisation vs suppression, délai de traitement.
4. Nom du rôle (`CLIENT`) et libellés d'erreur en français affichables (je fournis les messages).

## 10. Livraison
Je ne code rien avant la validation écrite de ce contrat par la session Mobile et par l'utilisateur. À la livraison : tests unitaires, passage
sur le gateway dev, puis message « API disponible » avec la liste des endpoints réellement testés.
