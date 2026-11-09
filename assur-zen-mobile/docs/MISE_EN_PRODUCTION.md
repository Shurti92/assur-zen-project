# AssurZen Mobile : dossier de mise en production

Document de travail, établi à partir du code de l'application (version `1.0.0+1` dans `pubspec.yaml`, **mis à jour
le 8 novembre 2026** : documents, notifications, Zen Points, remises, Assurance Entreprise). Il distingue ce que le
code prouve, ce qui reste à vérifier, et ce que seule AssurZen peut fournir. **Les passages marqués « à valider »
doivent être relus par la personne responsable des données personnelles et par le service juridique avant
toute publication ou déclaration.** Aucune durée de conservation, aucun engagement de délai ni aucune
coordonnée n'est inventé ici.

---

## 1. Où en est l'application

| Sujet | État |
|---|---|
| Parcours branchés sur la Gateway | Authentification, contrats, demandes, sinistres avec photos, catalogue, rappel, assistance, notifications push, devis moto, VTC et voyage (calcul, enregistrement, acceptation, liste), cotation et demande de souscription Assurance Entreprise |
| Documents | Page « Mes documents » par contrat ; **ajout de documents par le client** (contrat, reçu, attestation ; PDF, JPG ou PNG, 5 Mo au plus) avec statut En attente, Refusé (motif) ou Disponible |
| Notifications | Écran « Mes notifications » : canaux push et SMS, grille par catégorie, accord SMS dont le texte vient du serveur (sans texte du serveur, les SMS ne peuvent pas être activés) |
| Zen Points | Adhésion, niveau, historique, règles, remises assurance auto (moto, VTC) au devis et au renouvellement, grille du niveau suivant. Actifs en production côté Gateway (réglages `LOYALTY_ENABLED` et `LOYALTY_DISCOUNT_ENABLED`) ; l'expiration des points est **désactivée** |
| Paiement | **À confirmer avec la Gateway** : l'application sait payer un devis accepté et un renouvellement, mais seule la Gateway peut dire si un agrégateur réel est branché en production |
| Souscription depuis un devis | Livrée côté application (statuts payé et converti, contrat émis) : **à valider en production** |
| Assurance Entreprise | Cotation et demande de souscription livrées en recette ; produit **masqué en production** tant que les textes ne sont pas validés par AssurZen |
| Essais sur la recette et la production | **Partiels** : connexion, Zen Points, cotation et demande Entreprise essayés sur la recette (8 novembre 2026). Le reste de la recette et toute la production : **à faire** |
| Build de publication signé | **Non fait** : le projet lit désormais `android/key.properties` pour signer, mais **aucun keystore n'existe**. Sans lui, la version `release` reste signée avec la clé de debug (essais seulement) |

## 2. Données personnelles : inventaire pour la politique de confidentialité, l'ARTCI et Google Play

### 2.1 Données envoyées à la Gateway

| Donnée | Quand | Finalité |
|---|---|---|
| Nom et prénoms | Inscription, modification du profil | Identifier le client |
| Numéro de téléphone | Inscription, connexion, code SMS, PIN oublié, demande de rappel, numéro à débiter pour un paiement | Identifier le client, envoyer les codes, rappeler, payer |
| Code PIN (6 chiffres) | Inscription, connexion, vérification avant paiement, changement, suppression de compte | Authentifier le client. Vérifié par le serveur ; jamais stocké par l'application |
| Adresse e-mail (facultative) | Profil | Contact |
| Consentement marketing | Inscription | Préférence de communication |
| Données de sinistre | Déclaration | Traiter le sinistre : contrat, type, date, heure, lieu, circonstances, tiers éventuel (nom, immatriculation, assureur) |
| **Photos** de sinistre | Déclaration | Pièces justificatives. Envoyées au serveur, non conservées par l'application |
| Données de devis moto ou VTC | Devis | Calculer et enregistrer le devis : type et caractéristiques du véhicule, marque, modèle, immatriculation ou châssis, année, dates |
| Données de devis voyage | Devis | Nom du voyageur, numéro de passeport, date de naissance, pays, dates de départ et de retour |
| **Documents ajoutés par le client** | « Ajouter un document » | Fichiers PDF, JPG ou PNG (5 Mo au plus) rattachés à un contrat, vérifiés par le back-office. Envoyés au serveur, non conservés par l'application |
| Préférences de notification | « Mes notifications » | Canaux push et SMS, catégories choisies, **accord SMS** (date et version du texte accepté) |
| Adhésion aux Zen Points | « Mes Zen Points » | Consentement au programme (version des conditions), solde, niveau, historique des points |
| Réponses au quiz, score de protection | Écran d'accueil | Mesurer et récompenser les bons gestes (consentement demandé, texte fourni par le serveur) |
| **Demande de souscription Entreprise** | Souscrire (Assurance Entreprise) | Domaine d'activité, nom de l'entreprise, numéro RCCM, localisation, **nom, fonction, téléphone et e-mail du représentant**, formule et option cotées. La Gateway envoie une alerte e-mail au conseiller. Un e-mail de confirmation au représentant (texte réglable, sans montant) est prévu : **livré en développement le 8 novembre 2026, pas encore déployé en recette ni en production** |
| Message d'assistance, avis | Assistance, avis après souscription | Répondre au client, mesurer la satisfaction |
| Demandes sur un contrat | Modification ou résiliation | Traiter la demande |
| Paiement | Renouvellement | Référence payable, montant, moyen, numéro de téléphone, clé d'idempotence |
| **Jeton de notification et identifiant d'appareil** | Après connexion | Envoyer des notifications push |

### 2.2 Données conservées sur le téléphone

| Donnée | Stockage | Remarque |
|---|---|---|
| Jetons de session | Stockage chiffré du système (`flutter_secure_storage`) | Effacés à la déconnexion |
| Schéma de déverrouillage | Stockage chiffré, **sous forme de hachage salé** (20 000 itérations) | Jamais envoyé au serveur ; effacé à la déconnexion |
| Dernier numéro connu, choix d'empreinte, choix de verrouillage, préférences de notification, identifiant d'appareil push | Préférences de l'application (non chiffrées) | Le numéro de téléphone du dernier compte est donc lisible sur un téléphone « rooté » |
| Brouillon du **devis générique**, cache d'attestation hors connexion | Préférences de l'application (non chiffrées) | Voir le point 5.2 |

### 2.3 Services tiers
- **Gateway AssurZen** (`medicis-apps.com`) : toutes les données du 2.1.
- **Firebase Cloud Messaging (Google)** : reçoit le jeton de l'appareil pour acheminer les notifications.
- Aucun outil de statistiques, de publicité ni de suivi des plantages n'est intégré (dépendances vérifiées : `firebase_core` et `firebase_messaging` seulement).

### 2.4 Permissions Android demandées
`INTERNET`, `USE_BIOMETRIC` (empreinte), `POST_NOTIFICATIONS` (notifications, Android 13 et plus). Aucune permission de localisation, de contacts, de micro ni de stockage. Les photos passent par le sélecteur du système.

## 3. Mesures de sécurité en place (prouvées par le code)
- Le PIN est vérifié par le serveur, qui verrouille le compte après plusieurs essais faux ; l'application annonce le nombre de tentatives restantes quand le serveur le fournit.
- Session verrouillée après plus de 5 minutes hors de l'application.
- Jetons dans le stockage chiffré du système ; renouvellement automatique du jeton expiré.
- `allowBackup="false"` : les données de l'application ne sont pas sauvegardées dans le cloud Android.
- Le trafic en clair (HTTP) n'est autorisé que dans le build de **debug** ; les adresses de recette et de production sont en HTTPS.
- Un code SMS de connexion ne peut pas réinitialiser un PIN (cloisonnement vérifié sur le serveur).
- Suppression de compte demandée depuis l'application, avec confirmation par le PIN.

## 4. Fiche Google Play (brouillon à valider)

**Nom** : AssurZen
**Description courte** (80 caractères maximum) : *Vos contrats, devis et sinistres AssurZen, dans votre poche.*

**Description longue (proposition)**
> AssurZen Assurances Côte d'Ivoire dans votre poche.
>
> • Consultez vos contrats et leurs échéances.
> • Obtenez un devis en ligne pour votre moto, votre véhicule VTC ou votre voyage, enregistrez-le et acceptez-le.
> • Retrouvez et ajoutez les documents de vos contrats.
> • Gagnez des Zen Points et profitez de remises sur votre assurance auto.
> • Déclarez un sinistre avec photos et suivez son avancement.
> • Faites une demande de modification ou de résiliation de contrat.
> • Soyez prévenu par notification, et contactez un conseiller.
> • Protégez l'accès à l'application par votre code PIN, votre empreinte ou un schéma.

**À ne pas annoncer avant d'être confirmé** : le paiement en ligne (seule la Gateway peut dire si un agrégateur réel est branché), l'Assurance Entreprise (produit masqué en production tant que ses textes ne sont pas validés), l'expiration des points (désactivée). La liste ci-dessus doit rester conforme à ce qui est réellement en ligne le jour de la publication, et les remises Zen Points ne doivent être annoncées qu'avec les conditions validées par AssurZen.

**Formulaire « Sécurité des données »** (réponses proposées, à valider) :
- Données collectées : nom, adresse e-mail, numéro de téléphone, photos, identifiants d'appareil (jeton de notification), informations de paiement (numéro de téléphone et montant, uniquement lors d'un paiement).
- Données partagées avec des tiers : le jeton d'appareil est transmis à Google (Firebase Cloud Messaging) pour les notifications.
- Chiffrement en transit : oui, à confirmer par un essai sur la recette et la production (certificat HTTPS).
- Suppression : demande possible dans l'application. Google Play exige aussi **une adresse web** pour demander la suppression sans l'application : à fournir par AssurZen.

**Captures d'écran à produire** (téléphone, 2 à 8) : accueil, mes contrats, devis moto avec résultat, devis VTC, déclaration de sinistre, mes devis, choix de verrouillage. À réaliser sur un compte de démonstration, jamais sur un compte réel.

## 5. Points à traiter avant la publication

### 5.1 Techniques
1. **Signature de publication.** Le projet est prêt : `android/app/build.gradle.kts` signe la version `release` avec le keystore décrit dans `android/key.properties` (lignes `storeFile`, `storePassword`, `keyAlias`, `keyPassword`), et retombe sur la clé de debug s'il est absent. **Reste à faire par AssurZen :** créer le keystore (par exemple avec `keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`) et le fichier `key.properties`, tous deux jamais publiés (déjà ignorés par git). Puis produire un `app bundle` signé (`flutter build appbundle --release --dart-define=USE_MOCK=false --dart-define=APP_ENV=prod`) et vérifier que les notifications fonctionnent dans cette version. *Essai du 8 novembre 2026 : le bundle de production est construit et signé avec la clé d'envoi (taille 63 Mo). La commande affiche toutefois « failed to strip debug symbols » et se termine en erreur parce que les « Android SDK Command-line Tools » ne sont pas installés ; le fichier `.aab` est bien produit et valide. Les installer (Android Studio, SDK Manager) supprime l'erreur et ajoute les symboles de débogage natifs que Google Play recommande.* *Le keystore et ses mots de passe doivent être sauvegardés par AssurZen : leur perte empêche toute mise à jour. Le numéro de version (`version:` dans `pubspec.yaml`) est à augmenter à chaque publication.*
2. **Essai complet sur la recette** puis la production : certificat HTTPS, comptes, vrais SMS, notifications, devis, sinistre avec photo. Non fait à ce jour.
3. **Fichier Firebase de production** (`google-services.json`) : l'application embarque le projet `fir-assurzen` (paquet `ci.assurzen.assurzen_mobile`). **À faire avant publication :** (a) lire, sur chaque serveur (recette, production), le champ `project_id` du fichier de compte de service désigné par `FIREBASE_CREDENTIALS_PATH` : il doit être `fir-assurzen`, sinon aucune notification n'arrivera ; (b) **`PUSH_ENABLED` est à `false` en recette et en production (relevé du 8 novembre 2026) : aucune notification push réelle ne part aujourd'hui.** Le passer à `true` dans le Paramétrage dynamique, sur décision d'AssurZen, puis tester sur un téléphone avec le bundle signé.
4. **Réduction et obscurcissement du code** (R8) : non activés ; à décider, puis à tester.
5. **Intégration continue** : analyse et tests à chaque modification du dossier mobile (aucun pipeline aujourd'hui).
6. **Logo vectoriel et icône de lancement définitive** (l'icône actuelle est provisoire).

### 5.2 Données personnelles
1. **Brouillon du devis générique** : il enregistre en clair, dans les préférences, le nom, le téléphone et l'e-mail du souscripteur pendant 30 jours. Ce parcours n'est atteignable que pour un produit déclaré « souscrivable en ligne » sans devis dédié ; à vérifier sur la Gateway de production avant publication. **Avant d'en activer un, il faut soit chiffrer ce brouillon, soit ne plus y stocker ces champs.**
2. **Demande de souscription Entreprise** : elle transmet l'identité d'un représentant d'entreprise (nom, fonction, téléphone, e-mail). À inscrire dans l'inventaire du traitement. L'e-mail de confirmation au représentant (texte réglable au back-office, sans montant) est livré en développement mais pas encore déployé : son texte est à valider par AssurZen. L'envoi se fait en arrière-plan et son état est visible au back-office.
3. **Dernier numéro de téléphone connu** : conservé en clair pour préremplir la connexion. À valider (acceptable ou à déplacer dans le stockage chiffré).
4. **Durées de conservation** côté serveur (compte, sinistres, photos, devis, journaux) : à définir par AssurZen et à publier.

### 5.3 À fournir par AssurZen
- **Politique de confidentialité** et **conditions générales d'utilisation** (liens renseignés dans le back-office ; l'application les affiche déjà quand le serveur les fournit).
- **Déclaration du traitement auprès de l'ARTCI** : l'inventaire du chapitre 2 peut servir de base, à compléter avec les finalités, les destinataires, les durées et le responsable du traitement.
- **Agrégateur de paiement**, son environnement de test, puis son contrat.
- **Adresse web de suppression de compte** (exigée par Google Play).
- **Compte développeur Google Play** (en cours d'achat) et ses informations d'entreprise.
- **Devis créés au back-office** : AssurZen a décidé de les rapprocher des comptes mobiles par numéro de téléphone. Le rapprochement est réalisé côté Gateway (en cours). **À inscrire dans le registre des traitements de l'ARTCI** : un devis établi par un conseiller pour un prospect devient visible dans l'application du client qui possède ce numéro. Garde-fou prévu : seuls les comptes dont le numéro a été vérifié par SMS sont concernés, à cause des numéros recyclés par les opérateurs.

## 6. Déroulement proposé
1. Essai complet sur la recette (sans publication).
2. Build signé, essai sur un téléphone, notifications comprises.
3. Test fermé auprès de quelques vrais clients, avec les captures et la fiche prêtes.
4. Publication progressive, après la déclaration ARTCI et la politique de confidentialité.
5. Ensuite : souscription et paiement, dès que la Gateway et l'agrégateur sont livrés.
