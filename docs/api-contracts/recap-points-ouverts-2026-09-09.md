# Réponse backend — 12 points signalés (2026-09-09)

> ⚠️ **Ce fichier remplace la version précédente publiée plus tôt aujourd'hui**, qui reposait sur une vérification faite par erreur contre la mauvaise base de données (voir section "Anomalie d'environnement" en bas). Tout ce qui suit a été revérifié **en direct contre un gateway relancé**, avec identifiants réels, sur la base confirmée `assur_zen_ci_db_test`.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 🔴 Bloquant

**1. `GET /contracts` / `GET /contracts/{id}` en 500** — ✅ **Corrigé, vérifié en direct.**
`GET /contracts?page=0&size=5` → `200`. `GET /contracts/64` (contrat existant) → `200` avec toutes les données. `GET /contracts/1` (inexistant) → `404` propre. Cause et correctif détaillés dans `contracts-500-commercial-commission-status-migration.md`.

**2. `DELETE /contracts/{id}` en 500 (contrats de test 64/65 bloqués)** — ✅ **Corrigé, vérifié en direct.**
Même cause racine que le point 1 (échec de mapping Hibernate empêchait même la lecture nécessaire à la suppression). `DELETE /contracts/64` → `204`, contrat réellement supprimé (vérifié en base après coup). `DELETE /contracts/65` → `404` propre (n'existait déjà plus). Les deux contrats de test sont purgés.

## 🟠 Bugs confirmés

**3. `GET /commercial-performance/dashboard` en 409 en parallèle avec `/ranking`** — ✅ **Déjà corrigé le 2026-09-07** (avant même votre signalement), reconfirmé en direct : les deux endpoints appelés en parallèle renvoient chacun `200`. Ancienne cause : les 4 endpoints de lecture faisaient un `repository.save()` sur une entité versionnée (`@Version`), provoquant un conflit de verrou optimiste entre lectures concurrentes. Le chemin de lecture ne persiste plus rien du tout désormais (voir javadoc `CommercialPerformanceService`). Le contournement séquentiel côté frontend peut être retiré.

**4. `POST /auth/login/otp-resend` en 500 après un premier 429** — ❌ **Non reproduit.** 3 appels consécutifs juste après un `/auth/login` (donc immédiatement en fenêtre de cooldown) ont tous renvoyé `429 OTP_RESEND_TOO_SOON` proprement, jamais de 500. Cette classe de bug (`SmsSendException` non gérée) a été corrigée le 2026-08-06 (`otp-resend.md`) — les deux handlers (`OtpResendTooSoonException` → 429, `SmsSendException` → 503) sont bien en place et actifs. Si ça se reproduit côté frontend, il nous faut la réponse brute exacte (corps + code) pour investiguer plus loin — un cas non couvert par ces deux handlers serait alors un nouveau bug, pas une régression du correctif d'août.

**5. Exception Hibernate brute dans `/satisfaction-imports` (téléphone partagé par plusieurs assurés)** — ✅ **Bug réel confirmé, corrigé.** `InsuredRepository.findByClientPhone()` suppose l'unicité (`Optional<Insured>`) ; sans contrainte `UNIQUE` en base, deux assurés peuvent légitimement partager un numéro, ce qui faisait remonter `IncorrectResultSizeDataAccessException` jusqu'au catch-all générique (`ERREUR_INCONNUE`, message Hibernate brut). Nouveau type d'erreur dédié **`CLIENT_AMBIGU`** avec message métier ("Plusieurs assurés partagent ce numéro de téléphone, impossible de déterminer le bon client"). Corrigé dans `CsvImportRowProcessor.rechercherClient()`.

## 🟡 Fonctionnalités en attente

**6. Pièce jointe sur paiement d'échéance** — ✅ **Livré, testé en direct de bout en bout**, exactement la forme proposée dans `paiement-piece-jointe.md` :
```
GET  /contracts/{id}/paiements/{paiementId}/pieces-jointes
POST /contracts/{id}/paiements/{paiementId}/pieces-jointes   (multipart "file")
GET  /contracts/{id}/paiements/{paiementId}/pieces-jointes/{pieceId}
```
`PaiementPieceJointeResponse` identique au contrat proposé. Testé : création d'un paiement → upload d'un fichier (`201`) → listing (`200`, fichier présent) → téléchargement (`200`, contenu correct). Aucune suppression pour cette phase, comme documenté dans la proposition initiale.

**7. `typeProfileId` absent de `User`/`UserProfileResponse`** — ❌ **Déjà présent, vérifié en direct.** Une vraie connexion (`POST /auth/login`) renvoie `"typeProfileId":1` dans `UserResponse`. `UserProfileResponse` porte aussi le champ. Livré depuis fin août (`user-type-profile-id.md`) — rien à faire ici, sauf si le blocage vient d'un champ mal nommé ou d'un cache côté client, à creuser ensemble si ça persiste.

**8. CORS bloqué sur la gateway de recette (`medicis-apps.com` → 9443)** — Le code a déjà `medicis-apps.com` dans `allowed-origins`, à la fois dans la config de base (`application.yml`, héritée par tous les profils) et dans `application-prod.yml` — ajouté suite à un incident CORS réel déjà vécu. Si le blocage persiste, ce n'est donc pas un gap de code mais très probablement soit un `CORS_ALLOWED_ORIGINS` d'environnement qui écrase cette valeur sur le serveur de recette réel, soit un redéploiement manquant, soit une couche reverse-proxy en amont — confirmé "config serveur uniquement" comme vous l'aviez déjà noté, on ne peut pas le vérifier depuis ce poste de dev.

**9. Pas de `notificationEnvoyee` sur `POST /enquetes-satisfaction`** — Design actuel : le statut d'envoi SMS est porté par le champ `statut` existant (`EnqueteStatut.ENVOYEE`/`ECHEC_ENVOI`/`REPONDUE`/`EXPIREE`), pas par un booléen séparé — décision prise et confirmée lors de l'implémentation initiale. C'est une divergence de convention volontaire avec les autres modules (Réclamation, Fleet, etc.), pas un oubli. **Question ouverte plutôt qu'un bug** : voulez-vous qu'on ajoute quand même un `notificationEnvoyee` dérivé (`statut != ECHEC_ENVOI`) pour uniformiser avec les autres écrans, ou `statut` suffit-il côté frontend ? À trancher ensemble avant d'y toucher.

**10. Pas de `notificationEnvoyee` sur les réponses Flotte** — ❌ **Déjà présent, vérifié dans le code.** `FleetVehicleResponse.notificationEnvoyee` et `FleetImportResponse.notificationEnvoyee` existent tous les deux (`Boolean`, null sur les endpoints de preview/liste, renseigné à la création — même convention que `CommercialResponse.notificationEnvoyee`). Si l'écran ne l'affiche pas, le champ existe côté API — à vérifier côté mapping frontend.

## ⚪ Décisions à trancher (commission commerciale interne)

**11. Visibilité du commercial sur sa propre commission** — Déjà livré : `GET /commercials/me/commissions` (self-service, `CommercialCommissionSelfResponse` : résumé + détail contrat par contrat), pas besoin d'un endpoint admin-only pour ça. Voir `module-commercial-reponse-backend.md` point 1 pour le détail complet et pourquoi le paiement partiel n'est volontairement pas inclus.

**12. Dashboard/rappel "commission prête à payer"** — Pas encore livré, vraie question produit. `GET /commercial-commissions/commercials` donne déjà les totaux en attente par commercial (admin-only) — un rappel/notification proactif est une nouvelle fonctionnalité à spécifier si vous la voulez, pas un gap dans l'existant.

---

## ⚠️ Anomalie d'environnement découverte au passage

Le `.env` du gateway local a `DB_URL` pointant explicitement vers `assur_zen_ci_db` (sans suffixe), qui écrase le défaut `assur_zen_ci_db_test` du profil `dev`. Repéré car le premier passage de vérification (avant celui-ci) s'est fait sans le savoir contre cette base, sans trouver les bugs que vous aviez signalés — d'où la première version erronée de ce fichier. Confirmé ensuite que le poste de dev doit utiliser `assur_zen_ci_db_test`. Si un environnement partagé (recette) pointe réellement vers `assur_zen_ci_db` sans que ce soit voulu, ça vaut la peine de vérifier — un correctif de code non accompagné du bon redéploiement/bonne base peut sembler "non livré" côté frontend alors qu'il est bien present dans le dépôt.

## Rappel

Une seule petite fiche de test volontairement laissée en base pour la vérification du point 6 : paiement `id=5` sur le contrat `66` (`CTR2026000635`), avec une pièce jointe `justificatif.txt` — à purger si besoin, sinon sans impact.

---

## Clôture (réponse frontend du 2026-09-09)

Points 1-7, 10, 11 : confirmés clos par le frontend (contournement séquentiel du point 3 retiré).

- **Point 8** (CORS recette) : confirmé non-gap côté dépôt, transmis à l'équipe déploiement/infra pour vérifier `CORS_ALLOWED_ORIGINS` et l'état du dernier déploiement sur le serveur de recette réel.
- **Point 9** (`notificationEnvoyee` satisfaction) : design existant conservé tel quel, décision du frontend — pas de champ dérivé ajouté (éviterait une duplication à maintenir sans valeur ajoutée).
- **Point 12** (rappel "commission prête à payer") : pas prioritaire, laissé en attente de spécification. `GET /commercial-commissions/commercials` suffit pour le besoin actuel en consultation à la demande.

Plus aucun point ouvert dans ce lot.
