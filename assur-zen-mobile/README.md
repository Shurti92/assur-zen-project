# AssurZen Mobile

**Votre sérénité, notre priorité.** Application Android d’AssurZen Assurances Côte d’Ivoire, en Flutter / Dart.

Ce projet reprend la maquette validée (38 écrans) et les 61 décisions du questionnaire signé le 25/10/2026. Il fonctionne tout de suite avec des **données de démonstration fictives**. Il se branche ensuite sur le backend Java / Spring Boot sans modifier les écrans.

---

## 1. Démarrer en 5 minutes

**Prérequis**
- Flutter **3.27 ou plus récent** (`flutter --version`)
- Android Studio avec le plugin Flutter, et un émulateur ou un téléphone Android 7.0 minimum
- `flutter doctor` sans erreur sur la partie Android

**Installation**

1. Décompressez le dossier `assurzen_mobile`.
2. Ouvrez un terminal dans ce dossier.
3. Lancez le script de préparation, une seule fois.
   - Windows (PowerShell) : `powershell -ExecutionPolicy Bypass -File scripts\setup.ps1`
   - macOS / Linux : `sh scripts/setup.sh`
4. Lancez l’application : `flutter run`

Le script fait 4 choses :
1. il génère le dossier `android/` adapté à **votre** version de Flutter (`flutter create`) ;
2. il y copie la configuration AssurZen du dossier `android_config/` : manifeste, permissions, icône, activité compatible empreinte ;
3. il fixe Android 7.0 (API 24) comme version minimale ;
4. il télécharge les dépendances (`flutter pub get`).

Vous pouvez ensuite ouvrir le dossier dans Android Studio (*File › Open*) et lancer avec ▶.

---

## 2. Tester la démo

Toutes les données sont fictives. Règles utiles pour parcourir les états :

| Écran | Pour tester… | Saisir |
|---|---|---|
| Connexion | une connexion réussie | n’importe quel numéro valide (ex. `07 08 12 34 56`) et un PIN à 6 chiffres |
| Connexion | un PIN erroné | `000000` |
| Code SMS | un code invalide | `000000` (tout autre code passe) |
| Paiement | un paiement confirmé | n’importe quel numéro : confirmation après environ 5 s |
| Paiement | un paiement refusé | un numéro qui finit par `99` |
| Création de compte | un nouveau client sans contrat | créer un compte, puis vérifier le code |

Le bouton « Découvrir les offres sans compte » ouvre le catalogue et le devis sans connexion. Le compte n’est demandé qu’au moment de souscrire.

---

## 3. Brancher le backend Java

```bash
flutter run --dart-define=USE_MOCK=false --dart-define=API_BASE_URL=https://api-test.assurzen.ci
```

Toutes les données passent par des **dépôts** (`lib/data/repositories/repositories.dart`). Les écrans ne savent pas si les données viennent de la démo ou du serveur.

**Étapes**

1. **Routes** : remplacer les chemins provisoires `/TODO/...` dans `lib/core/network/endpoints.dart` par ceux de la documentation OpenAPI (Swagger) de Spring Boot.
2. **Dépôts** : compléter `lib/data/api/api_repositories.dart`. `ApiContractRepository` est un **exemple complet** à copier : appel, conversion JSON, modèle. Les autres dépôts lèvent une erreur explicite « Service non branché » tant qu’ils ne sont pas reliés.
3. **Format d’erreur** : `ApiClient` attend `{ "code", "message", "field" }` sur une erreur 4xx. Le `message` est affiché tel quel au client, sous le champ `field` s’il est fourni.
4. **Authentification** : le jeton est envoyé en `Authorization: Bearer …` et stocké chiffré (`flutter_secure_storage`). Une réponse **401** verrouille l’application et redemande le PIN.
5. **Paiement** : l’application lance la transaction (`initiate`), puis interroge son statut (`status`) toutes les 3 s pendant 3 minutes au plus. La confirmation doit venir du serveur : webhook de l’agrégateur côté backend. L’application n’affiche jamais « payé » sans elle.

| Écran | Lecture | Création | Fichier | Paiement | Auth |
|---|---|---|---|---|---|
| Connexion, inscription, code SMS, PIN oublié, verrouillage | | | | | ● |
| Accueil, catalogue, fiche produit, contrats, sinistres, notifications | ● | | | | |
| Devis (calcul), rappel conseiller, déclaration de sinistre, assistance | | ● | | | |
| Pièces justificatives, photos de sinistre | | | ● envoi | | |
| Documents du contrat (attestation, carte brune, reçus) | | | ● téléchargement | | |
| Paiement et renouvellement | | | | ● | |

---

## 4. Organisation du code

```
lib/
├─ main.dart                  démarrage, choix démo / API
├─ app/
│  ├─ app.dart                MaterialApp, français, verrouillage après 5 min
│  ├─ routes.dart             toutes les routes (noms kebab-case)
│  ├─ env.dart                paramètres et coordonnées officielles
│  ├─ app_scope.dart          injection des services (sans paquet tiers)
│  └─ session_controller.dart état de session
├─ core/
│  ├─ ui.dart                 import unique du Design System
│  ├─ theme/                  couleurs, typographie, espacements, thème
│  ├─ widgets/                composants Az… réutilisables
│  ├─ network/                ApiClient, erreurs, routes
│  ├─ storage/                stockage chiffré et brouillons
│  ├─ security/               empreinte digitale
│  ├─ state/                  AsyncView (chargement, vide, erreur, données)
│  └─ utils/                  FCFA, téléphone +225, dates, validations, liens
├─ data/
│  ├─ models/                 modèles métier (sans Flutter)
│  ├─ repositories/           interfaces des dépôts
│  ├─ mock/                   démonstration (données fictives)
│  └─ api/                    branchement backend
└─ features/
   ├─ onboarding/             lancement, présentation, bienvenue
   ├─ auth/                   connexion PIN, inscription, code SMS, PIN oublié, verrouillage
   ├─ shell/                  barre de navigation à 5 onglets
   ├─ home/                   accueil
   ├─ notifications/
   ├─ catalog/                catalogue, fiche produit, « Trouver mon assurance », rappel
   ├─ quote/                  devis et souscription (6 étapes)
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

## 5. Design System

| Jeton | Valeur | Usage |
|---|---|---|
| `AzColors.blue700` | `#033892` | Bleu AssurZen : actions, liens, texte de marque |
| `AzColors.orange500` | `#FB6404` | Orange AssurZen : bouton « Souscrire », accents (jamais pour du texte) |
| `AzColors.orange700` | `#B84500` | Orange lisible pour du texte |
| `AzColors.ink` | `#0F1B33` | Texte principal |
| `AzColors.background` | `#F5F7FB` | Fond des écrans |
| Police | Manrope 400 à 800 | Embarquée, aucun téléchargement |
| Marges | 20 dp | Bords d’écran |
| Rayons | 12 / 14 / 16 / 22 / 24 | Champs / boutons / cartes / héros / feuilles |

Les couleurs sont mesurées sur le logo fourni (JPG). Un logo vectoriel permettra de les confirmer au pixel près.

**Accessibilité** : zones tactiles d’au moins 44 à 48 dp, contrastes AA, libellés pour les lecteurs d’écran, texte agrandi accepté jusqu’à 130 %.

---

## 6. Avant la mise en production

- [ ] Logo vectoriel et icône de lancement définitive.
- [ ] Routes et DTO réels (OpenAPI) : `endpoints.dart` et `api_repositories.dart`.
- [ ] Garanties, formules, éligibilité et tarifs servis par l’API. Aucune donnée contractuelle n’est codée en dur.
- [ ] Agrégateur de paiement retenu, environnement de test (bac à sable).
- [ ] Liens officiels : CGU, politique de confidentialité, mentions légales.
- [ ] Déclaration du traitement auprès de l’ARTCI.
- [ ] Notifications push (Firebase Cloud Messaging) : enregistrement du jeton de l’appareil.
- [ ] Signature de l’application (`key.properties`) et publication sur Google Play.
- [ ] Valeur légale de l’attestation numérique et contenu du QR code de vérification.

---

Polices : Manrope, sous licence SIL Open Font License (`assets/fonts/OFL.txt`).
