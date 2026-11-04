# AssurZen Mobile : dossier de mise en production

Document de travail, établi à partir du code de l'application (version `1.0.0+1`). Il distingue ce que le code
prouve, ce qui reste à vérifier, et ce que seule AssurZen peut fournir. **Les passages marqués « à valider »
doivent être relus par la personne responsable des données personnelles et par le service juridique avant
toute publication ou déclaration.** Aucune durée de conservation, aucun engagement de délai ni aucune
coordonnée n'est inventé ici.

---

## 1. Où en est l'application

| Sujet | État |
|---|---|
| Parcours branchés sur la Gateway | Authentification, contrats, demandes, sinistres avec photos, catalogue, rappel, assistance, notifications push, devis moto et VTC (calcul, enregistrement, acceptation, liste) |
| Paiement | **Simulé** par le serveur en développement. Aucun agrégateur réel n'est branché |
| Souscription depuis un devis | **Non livrée** côté Gateway (statut `CONVERTI` inutilisé) |
| Devis des autres produits | **Non livrés** côté Gateway (seuls moto et VTC existent) |
| Envoi de pièces à la souscription | **N'existe pas** côté Gateway (seules les photos de sinistre existent) |
| Essais sur la recette et la production | **Jamais faits** : tout est validé sur la Gateway de développement |
| Build de publication signé | **Non fait** : la version `release` utilise aujourd'hui la signature de debug |

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
> • Obtenez un devis en ligne pour votre moto ou votre véhicule VTC, enregistrez-le et acceptez-le.
> • Déclarez un sinistre avec photos et suivez son avancement.
> • Faites une demande de modification ou de résiliation de contrat.
> • Soyez prévenu par notification, et contactez un conseiller.
> • Protégez l'accès à l'application par votre code PIN, votre empreinte ou un schéma.

**À ne pas annoncer avant d'être livré** : le paiement en ligne (aujourd'hui simulé), la souscription depuis l'application, les devis d'autres produits. La liste ci-dessus doit rester conforme à ce qui est réellement en ligne le jour de la publication.

**Formulaire « Sécurité des données »** (réponses proposées, à valider) :
- Données collectées : nom, adresse e-mail, numéro de téléphone, photos, identifiants d'appareil (jeton de notification), informations de paiement (numéro de téléphone et montant, uniquement lors d'un paiement).
- Données partagées avec des tiers : le jeton d'appareil est transmis à Google (Firebase Cloud Messaging) pour les notifications.
- Chiffrement en transit : oui, à confirmer par un essai sur la recette et la production (certificat HTTPS).
- Suppression : demande possible dans l'application. Google Play exige aussi **une adresse web** pour demander la suppression sans l'application : à fournir par AssurZen.

**Captures d'écran à produire** (téléphone, 2 à 8) : accueil, mes contrats, devis moto avec résultat, devis VTC, déclaration de sinistre, mes devis, choix de verrouillage. À réaliser sur un compte de démonstration, jamais sur un compte réel.

## 5. Points à traiter avant la publication

### 5.1 Techniques
1. **Signature de publication.** Créer un keystore et un fichier `key.properties` (jamais publiés : déjà ignorés par git), puis produire un `app bundle` signé et vérifier que les notifications fonctionnent dans cette version. *Le keystore doit être sauvegardé par AssurZen : sa perte empêche toute mise à jour.*
2. **Essai complet sur la recette** puis la production : certificat HTTPS, comptes, vrais SMS, notifications, devis, sinistre avec photo. Non fait à ce jour.
3. **Fichier Firebase de production** (`google-services.json`) : à confirmer que le projet Firebase utilisé est celui de la production.
4. **Réduction et obscurcissement du code** (R8) : non activés ; à décider, puis à tester.
5. **Intégration continue** : analyse et tests à chaque modification du dossier mobile (aucun pipeline aujourd'hui).
6. **Logo vectoriel et icône de lancement définitive** (l'icône actuelle est provisoire).

### 5.2 Données personnelles
1. **Brouillon du devis générique** : il enregistre en clair, dans les préférences, le nom, le téléphone et l'e-mail du souscripteur pendant 30 jours. Ce parcours n'est atteignable que pour un produit déclaré « souscrivable en ligne », ce qui n'est le cas d'aucun produit aujourd'hui. **Avant d'en activer un, il faut soit chiffrer ce brouillon, soit ne plus y stocker ces champs.**
2. **Dernier numéro de téléphone connu** : conservé en clair pour préremplir la connexion. À valider (acceptable ou à déplacer dans le stockage chiffré).
3. **Durées de conservation** côté serveur (compte, sinistres, photos, devis, journaux) : à définir par AssurZen et à publier.

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
