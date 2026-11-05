# AssurZen Mobile

**Votre sérénité, notre priorité.** Application Android d’AssurZen Assurances Côte d’Ivoire, en Flutter / Dart.

Ce projet reprend la maquette validée (38 écrans) et les 61 décisions du questionnaire signé le 25/10/2026. Il fonctionne avec des **données de démonstration fictives** ou branché sur la **Gateway** (backend Java / Spring Boot). Aucun contenu métier n’est écrit en dur : catalogue, garanties, tarifs, coordonnées et délais viennent de l’API, paramétrables depuis le back-office. Une information absente côté serveur n’est pas affichée.

---

## 1. Démarrer

**Prérequis**
- Flutter avec Dart **3.5 ou plus récent** (`flutter --version`)
- Android Studio avec le plugin Flutter, et un émulateur ou un téléphone Android 7.0 minimum (API 24)
- `flutter doctor` sans erreur sur la partie Android

**Installation**

1. Récupérez le dossier `assur-zen-mobile/` du dépôt.
2. Placez le fichier Firebase `google-services.json` dans `android/app/`. Il n’est **pas publié** (il est ignoré par git) : demandez-le à l’équipe, il vient du projet Firebase d’AssurZen. Le plugin Google Services le lit pour les notifications push, et le build Android en a besoin.
3. Dans ce dossier : `flutter pub get`
4. Lancez l’application en mode démonstration : `flutter run`

Le dossier `android/` est déjà versionné. Le script `scripts/setup.ps1` (Windows) ou `scripts/setup.sh` (macOS / Linux) reste disponible : il est sans danger à relancer, il ne régénère `android/` que s’il manque et applique la configuration du dossier `android_config/` (manifeste, permissions, icône, activité compatible empreinte).

---

## 2. Environnements

Trois environnements, choisis au lancement avec `--dart-define=APP_ENV=…`. Sans option, l’application tourne en **démonstration** (`USE_MOCK=true`).

| `APP_ENV` | Gateway | Usage |
|---|---|---|
| `local` (défaut) | `http://localhost:9191` | Gateway de développement sur votre poste |
| `recette` | `https://medicis-apps.com:9443/assur-zen-gateway-recette/` | Recette |
| `prod` | `https://medicis-apps.com:9443/assur-zen-gateway/` | Production |

```bash
flutter run                                                          # démonstration
flutter run --dart-define=USE_MOCK=false                             # Gateway locale
flutter run --dart-define=USE_MOCK=false --dart-define=APP_ENV=recette
flutter build apk --release --dart-define=USE_MOCK=false --dart-define=APP_ENV=prod
```

- `API_BASE_URL` force une adresse précise et passe avant `APP_ENV`.
- Un `APP_ENV` inconnu est refusé au démarrage quand l’application se branche sur la Gateway (`USE_MOCK=false`).
- **Gateway locale sur un téléphone branché en USB** : ouvrez le tunnel avec `adb reverse tcp:9191 tcp:9191`. Le HTTP en clair n’est autorisé que dans le build de debug.

---

## 3. Tester la démonstration

Toutes les données sont fictives. Règles utiles pour parcourir les états :

| Écran | Pour tester… | Saisir |
|---|---|---|
| Connexion | une connexion réussie | n’importe quel numéro valide (ex. `07 08 12 34 56`) et un PIN à 6 chiffres |
| Connexion | un PIN erroné | `000000` |
| Code SMS | un code invalide | `000000` (tout autre code passe) |
| Paiement | un paiement confirmé | n’importe quel numéro : confirmation après environ 5 s |
| Paiement | un paiement refusé | un numéro qui finit par `99` |
| Création de compte | un nouveau client sans contrat | créer un compte, puis vérifier le code |

Le bouton « Découvrir les offres sans compte » ouvre le catalogue et le devis sans connexion. Le compte n’est demandé qu’au moment de souscrire ou d’enregistrer un devis.

---

## 4. Branchement sur la Gateway

Toutes les données passent par des **dépôts** (`lib/data/repositories/repositories.dart`). Les écrans ne savent pas si les données viennent de la démonstration ou du serveur.

**Branché et utilisé** : authentification (PIN, SMS, inscription, PIN oublié, suppression de compte), contrats et demandes (modification, résiliation, renouvellement), paiement (simulé par le serveur en développement), sinistres avec photos, catalogue, demande de rappel, coordonnées et liens légaux, support, notifications push, et les **devis en ligne moto et VTC** (simulation, devis enregistré, acceptation, liste « Mes devis »). Énergies, durées, marques, plateformes, nombre de places et prix y viennent du serveur ; la marque et la plateforme se choisissent en auto-complétion, avec saisie libre possible.

**Pas encore branché** : le parcours de devis générique, utilisé pour les autres produits. Ses routes (`quotes`, `referentials`, `quoteDocuments`) sont encore provisoires (`/TODO/...`) dans `lib/core/network/endpoints.dart`. Tant qu’elles ne sont pas livrées, seuls les devis moto et VTC sont servis.

**Règles à respecter**
1. **Rien en dur** : un texte qui promet quelque chose (horaire, délai, coordonnée, tarif) doit venir du serveur. Champ absent, `null` ou vide : la rubrique ou le bouton est masqué. Les données de démonstration restent dans `lib/data/mock/`.
2. **Format d’erreur** : `ApiClient` attend `{ "code", "message", "field" }` sur une erreur 4xx. Le `message` est affiché tel quel, sous le champ `field` s’il est fourni.
3. **Authentification** : le jeton est envoyé en `Authorization: Bearer …` et stocké chiffré (`flutter_secure_storage`). Sur une réponse **401**, l’application renouvelle le jeton (`auth/refresh`) et rejoue l’appel ; si le renouvellement échoue, elle verrouille et redemande le PIN. Un PIN faux n’est pas un 401 de session : l’erreur affiche le nombre de tentatives restantes quand le serveur le fournit (`remainingAttempts`).
4. **Paiement** : l’application lance la transaction (`initiate`), puis interroge son statut (`status`) toutes les 3 s pendant 3 minutes au plus. La confirmation vient du serveur (webhook de l’agrégateur côté backend). L’application n’affiche jamais « payé » sans elle.
5. **Montants** : jamais calculés dans l’application. Le prix d’un devis est celui du serveur.

| Écran | Lecture | Création | Fichier | Paiement | Auth |
|---|---|---|---|---|---|
| Connexion, inscription, code SMS, PIN oublié, verrouillage | | | | | ● |
| Accueil, catalogue, fiche produit, contrats, sinistres, notifications | ● | | | | |
| Devis (calcul), rappel conseiller, déclaration de sinistre, assistance | | ● | | | |
| Pièces justificatives, photos de sinistre | | | ● envoi | | |
| Documents du contrat (attestation, carte brune, reçus) | | | ● téléchargement | | |
| Paiement et renouvellement | | | | ● | |

---

## 5. Verrouillage de l’application

Après plus de 5 minutes passées hors de l’application, la session se verrouille. Le client la rouvre au choix, depuis **Profil › Mon compte** :

- **Code PIN** : toujours disponible, vérifié par le serveur. Il reste la référence : il sert à la suppression de compte et de repli pour confirmer un paiement quand l’empreinte n’est pas utilisée.
- **Empreinte digitale** : à activer dans le profil.
- **Schéma** : grille 3×3 enregistrée sur l’appareil sous forme de hachage salé dans le stockage chiffré, jamais envoyée au serveur. Il ne rouvre qu’une session déjà ouverte. Après 5 schémas faux, il ne reste que le PIN. Il est effacé à la déconnexion.

---

## 6. Tests

```bash
flutter analyze
flutter test
```

Les tests couvrent les dépôts API (réponses simulées au format réel de la Gateway), les validations, le verrouillage par schéma, l’auto-complétion et les environnements.

---

## 7. Organisation du code

```
lib/
├─ main.dart                  démarrage, choix démo / API
├─ app/
│  ├─ app.dart                MaterialApp, français, verrouillage après 5 min
│  ├─ routes.dart             toutes les routes (noms kebab-case)
│  ├─ env.dart                environnements (local, recette, prod) et délais
│  ├─ app_scope.dart          injection des services (sans paquet tiers)
│  └─ session_controller.dart état de session
├─ core/
│  ├─ ui.dart                 import unique du Design System
│  ├─ theme/                  couleurs, typographie, espacements, thème
│  ├─ widgets/                composants Az… réutilisables
│  ├─ network/                ApiClient, erreurs, routes
│  ├─ push/                   notifications push (Firebase Cloud Messaging)
│  ├─ storage/                stockage chiffré et brouillons
│  ├─ security/               empreinte digitale, schéma de déverrouillage
│  ├─ state/                  AsyncView (chargement, vide, erreur, données)
│  └─ utils/                  FCFA, téléphone +225, dates, validations, liens
├─ data/
│  ├─ models/                 modèles métier (sans Flutter)
│  ├─ repositories/           interfaces des dépôts
│  ├─ mock/                   démonstration (données fictives)
│  └─ api/                    branchement Gateway
└─ features/
   ├─ onboarding/             lancement, présentation, bienvenue
   ├─ auth/                   connexion PIN, inscription, code SMS, PIN oublié, verrouillage
   ├─ shell/                  barre de navigation à 5 onglets
   ├─ home/                   accueil
   ├─ notifications/
   ├─ catalog/                catalogue, fiche produit, « Trouver mon assurance », rappel
   ├─ quote/                  devis moto et VTC en ligne, « Mes devis », devis générique (6 étapes)
   ├─ payment/                paiement Mobile Money, carte, agence
   ├─ contracts/              contrats, attestation avec QR code
   ├─ claims/                 déclaration et suivi des sinistres
   ├─ profile/                profil et paramètres
   └─ support/                contact et assistance
```

**Conventions**
- Fichiers en `snake_case`, classes en `PascalCase`.
- Composants du Design System préfixés `Az` (`AzButton`, `AzCard`, `AzStatusBadge`…).
- Écrans suffixés `Page`.
- Un écran importe `core/ui.dart`, jamais les fichiers de thème un par un.
- Aucune couleur ni taille en dur dans les écrans : utiliser `AzColors`, `AzText`, `AzSpace`, `AzRadius`.

---

## 8. Design System

| Jeton | Valeur | Usage |
|---|---|---|
| `AzColors.blue700` | `#033892` | Bleu AssurZen : actions, liens, texte de marque |
| `AzColors.orange500` | `#FB6404` | Orange AssurZen : bouton « Souscrire », accents (jamais pour du texte) |
| `AzColors.orange700` | `#B84500` | Orange lisible pour du texte |
| `AzColors.ink` | `#0F1B33` | Texte principal |
| `AzColors.background` | `#F5F7FB` | Fond des écrans |
| Police | Sora 600 à 800 (titres, montants) et Plus Jakarta Sans 400 à 800 (texte) | Embarquées, aucun téléchargement |
| Tailles de texte | 30 / 22 / 19 / 16 / 15 / 14 / 13 / 12 | Titre à légende ; titres en Sora 700 |
| Marges | 20 dp | Bords d’écran |
| Rayons | 12 / 14 / 16 / 22 / 24 | Champs / boutons / cartes / héros / feuilles |

Les couleurs sont mesurées sur le logo fourni (JPG). Un logo vectoriel permettra de les confirmer au pixel près.

**Accessibilité** : zones tactiles d’au moins 44 à 48 dp, contrastes AA, libellés pour les lecteurs d’écran, texte agrandi accepté jusqu’à 130 %.

---

## 9. Avant la mise en production

- [ ] Logo vectoriel et icône de lancement définitive.
- [ ] Brancher le devis générique pour les produits autres que la moto (routes `/TODO` dans `endpoints.dart`).
- [ ] Garanties, formules, éligibilité et tarifs servis par l’API pour tous les produits (c’est le cas des devis moto et VTC).
- [ ] Agrégateur de paiement réel : le paiement est aujourd’hui simulé par le serveur en développement.
- [ ] Liens officiels (CGU, politique de confidentialité, mentions légales) renseignés dans le back-office.
- [ ] Déclaration du traitement auprès de l’ARTCI.
- [x] Notifications push (Firebase Cloud Messaging) : enregistrement du jeton de l’appareil.
- [ ] Signature de l’application : la version `release` utilise aujourd’hui la signature de debug. Créer `key.properties` et un keystore (jamais publiés), puis publier sur Google Play.
- [ ] Valeur légale de l’attestation numérique et contenu du QR code de vérification.

---

Polices : Sora et Plus Jakarta Sans, sous licence SIL Open Font License (`assets/fonts/OFL-Sora.txt` et `assets/fonts/OFL-PlusJakartaSans.txt`).
