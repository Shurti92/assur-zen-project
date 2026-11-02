# Contrat d'interface — Création de comptes utilisateurs (partenaires & back-office)

> 🆕 **Aucune de ces API n'existe aujourd'hui.** Vérifié dans le code frontend (2026-08-11) : `ProfileService` n'expose que `GET /profiles`, `GET /profiles/{id}`, `PUT /profiles/{id}` — **aucun `POST /profiles`**. `AuthService` n'a aucune méthode de création de compte (seulement `login`, `resetPassword`, `changePassword`, `unlockAccount`...). Aucun écran de gestion des utilisateurs n'existe dans le frontend. Le référentiel **Type Profil** (`/type-profiles`, valeurs actuelles `REPRESENTANT`/`ADMIN`) existe déjà (module Paramétrage) mais **n'est relié à rien** — ni à `Partenaire`, ni à `ProfileRecord`.
>
> Deux besoins distincts couverts par ce contrat :
> - **A.** Création **automatique** d'un compte de connexion (accès application mobile) pour tout Partenaire d'affaire dont le Type Profil est `REPRESENTANT`.
> - **B.** Création **manuelle** de comptes "classiques" pour l'accès back-office (personnel interne), via un nouvel écran Administration → Utilisateurs.
>
> Les deux flux doivent envoyer un **SMS de bienvenue avec les accès de connexion** — deux modèles distincts (partenaire/mobile vs back-office), en réutilisant le système de modèles de communication déjà en place (`fidelisation/campagnes/modeles`).
>
> ✅ **Les 6 points ouverts ont été tranchés avec le produit** (§6) : mécanisme de déclenchement, conflit de téléphone (avec correction possible depuis la fiche partenaire), mot de passe back-office aléatoire, rôles `USER`/`ADMIN` pour l'instant, deux SMS distincts, téléphone en tant que nouveau champ. Ce contrat est prêt à transmettre tel quel.
>
> **Ajout tardif** : un second numéro est aussi demandé à la création du partenaire — `numeroWaveCommission` (§3.1), distinct du téléphone de connexion, pour le versement des commissions exclusivement via Wave.
>
> ✅✅ **Livré côté backend et vérifié via Swagger le 2026-08-11.** `PartenaireRequest`/`PartenaireResponse`, `TypeProfileRequest` : conformes au contrat, noms de champs identiques (`typeProfileId`, `telephone`, `numeroWaveCommission`, `compteCree`, `declencheCreationCompte`), plus un bonus non demandé mais bienvenu : `compteCreationErreur` sur `PartenaireResponse`. **Écart bénéfique pour la Partie B** : au lieu du `POST /profiles` proposé dans ce contrat, le backend a livré un module `/users` complet et plus riche — voir §4bis ci-dessous, qui remplace le §4 d'origine pour l'implémentation frontend.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Besoin métier

**A. Partenaires (accès mobile)** — Tout Partenaire d'affaire dont le Type Profil est `REPRESENTANT` doit automatiquement disposer d'un compte de connexion à l'application mobile, sans action manuelle :
- Identifiant de connexion (`username`) = numéro de téléphone du partenaire.
- Mot de passe par défaut = `assur@zen0`, à changer obligatoirement à la première connexion.
- Rôle = `USER`.
- Un SMS de bienvenue est envoyé au partenaire avec ses accès.

**B. Personnel interne (accès back-office)** — Un administrateur doit pouvoir créer manuellement un compte pour un membre du personnel qui a besoin d'accéder à ce back-office Angular (`assur-zen-core`), avec un rôle approprié (ex. `ADMIN`). Même exigence de changement de mot de passe à la première connexion, et de notification par SMS.

## 2. Ce qui existe déjà et doit être réutilisé, pas dupliqué

| Brique existante | Rôle dans ce contrat |
|---|---|
| `mustChangePassword` (déjà sur `AuthUser`, déjà géré par `ChangePasswordComponent` + le guard qui redirige vers `/change-password`) | **Déjà fonctionnel.** Il suffit que le compte créé ait `mustChangePassword: true` — aucun développement frontend supplémentaire nécessaire pour forcer le changement de mot de passe à la première connexion. |
| `MAX_LOGIN_ATTEMPTS` / verrouillage de compte (livré, voir `docs/api-contracts/account-lockout.md`) | Protège déjà nativement contre le bruteforce d'un mot de passe par défaut connu (`assur@zen0`) partagé par tous les nouveaux comptes représentants — aucune mesure supplémentaire nécessaire de ce côté. |
| Référentiel Type Profil (`/type-profiles`, `TypeProfileRequest { libelle, active }`) | Source du déclencheur `REPRESENTANT` — voir §3.1 pour l'évolution proposée. |
| `PartenaireRequest` (`core/partenaire/models/partenaire.model.ts`) | Reçoit le nouveau champ `typeProfileId` — voir §3.1. **Ne contient aujourd'hui aucun champ téléphone** — à ajouter, voir §3.1. |
| `ProfileRecord`/`ProfileService` (`core/profile/`) | Cible du nouveau `POST /profiles` — voir §4.1. Contient déjà `userId`, `role` (string libre). |
| Système de modèles de communication (`fidelisation/campagnes/modeles`, déjà utilisé pour des SMS de bienvenue comme `BIENVENUE_SOUSCRIPTION`) | Réutilisé pour le SMS de bienvenue avec accès — voir §5, pas de nouveau système de templating à construire. |

## 3. Partie A — Comptes automatiques pour les partenaires `REPRESENTANT`

### 3.1 Évolutions de modèle nécessaires

**Sur `Partenaire`** — trois champs à ajouter :

```
PartenaireRequest {
  ...champs existants (type, nom, prenoms, objectifProduction, profileId, ...)
  typeProfileId?: number | null      // NOUVEAU — FK vers /type-profiles
  telephone: string                  // NOUVEAU — numéro de connexion (username du compte), absent aujourd'hui
  numeroWaveCommission?: string      // NOUVEAU — numéro Wave sur lequel le partenaire souhaite recevoir ses commissions
}
```

`numeroWaveCommission` est **distinct** de `telephone` : le partenaire peut vouloir recevoir ses commissions sur un autre numéro que celui utilisé pour se connecter (ex. un compte Wave partagé, un second téléphone dédié aux paiements). Saisi à la création du partenaire, demandé explicitement en plus du téléphone de connexion — pas pré-rempli automatiquement à partir de `telephone`.

**Canal de paiement des commissions** : exclusivement Wave, pour tous les partenaires — c'est une règle métier fixe, pas une valeur à stocker en base (pas besoin d'un champ "canal" puisqu'il n'y a qu'une seule valeur possible). **Point à vérifier avec le backend/produit** : le module Comptabilité → Commissions existant (`comptabilite/commissions`) enregistre aujourd'hui les paiements avec un libellé de référence libre (ex. "Virement bancaire", "Chèque n°147", "Virement Orange Money" observés en test) — si le canal devient exclusivement Wave, faut-il verrouiller/simplifier cette saisie pour les nouveaux paiements ? Hors périmètre strict de ce contrat (qui porte sur la création de comptes), mais à signaler puisque directement lié à `numeroWaveCommission`.

`typeProfileId` est distinct du champ `type` existant (`DIRECT`/`AGENT`/`APPORTEUR`) — les deux coexistent, ils ne se recouvrent pas.

**Sur `TypeProfile`** — proposition d'un indicateur explicite plutôt qu'un test sur le libellé `"REPRESENTANT"` codé en dur (fragile si quelqu'un renomme le libellé) :

```
TypeProfileRequest {
  libelle: string
  active: boolean
  declencheCreationCompte?: boolean   // NOUVEAU — coché uniquement sur l'entrée "REPRESENTANT"
}
```

Voir §6 pour la décision à trancher entre cette approche et un test direct sur le libellé.

### 3.2 Règles de création automatique

Déclenchée par le backend en side-effect de `POST /partenaires` et `PUT /partenaires/{id}` existants (pas de nouvel endpoint dédié pour ce flux) :

1. Si `typeProfileId` référence un Type Profil avec `declencheCreationCompte: true` **et** que `Partenaire.profileId` est vide (pas de compte déjà lié) :
   - Créer un `ProfileRecord` avec `username = telephone`, `password = "assur@zen0"` (hashé), `role = "USER"`, `mustChangePassword = true`.
   - Lier ce nouveau `profileId` au partenaire.
   - Envoyer le SMS de bienvenue (voir §5).
2. Si `Partenaire.profileId` est déjà renseigné → **ne rien recréer** (idempotent, évite d'écraser un compte existant à chaque modification du partenaire).
3. Si `telephone` est déjà utilisé comme `username` par un autre compte → conflit, voir §6 (comportement à trancher : bloquer la sauvegarde du partenaire, ou sauvegarder le partenaire et signaler l'échec de création de compte séparément ?).

### 3.3 Réponse `PartenaireResponse` enrichie (proposition)

```json
{
  "id": 42,
  "type": "AGENT",
  "typeProfileId": 3,
  "telephone": "0757873487",
  "profileId": 118,
  "compteCree": true,
  "...": "...autres champs existants inchangés"
}
```

`compteCree` (booléen, présent uniquement sur la réponse de création/modification) indique si cette sauvegarde vient de déclencher la création d'un compte — utile pour que le frontend affiche une confirmation ("Compte créé, SMS envoyé au 07XX...").

## 4. Partie B — Comptes classiques (back-office) — proposition d'origine, **remplacée par §4bis**

*(section conservée pour l'historique — voir §4bis pour ce qui a réellement été livré et implémenté)*

Proposition initiale : `POST /profiles` avec `username`/`fullName`/`email`/`phone`/`role`, mot de passe temporaire généré par le backend et renvoyé une fois. Le backend a livré une solution différente et plus complète — voir §4bis.

## 4bis. Ce qui a réellement été livré (vérifié via Swagger le 2026-08-11)

Le backend n'a **pas** ajouté de `POST /profiles` pour la création (`ProfileRequest` exige un `userId` déjà existant — cet endpoint sert à enrichir la fiche profil d'un compte qui existe déjà, pas à en créer un). À la place, il a livré un **module `/users` complet** :

| Endpoint | Rôle |
|---|---|
| `POST /users` | **"Créer un utilisateur et son profil en une seule opération"** — l'endpoint à utiliser pour ce contrat. |
| `GET /users` | Liste paginée, filtres `q`, `active`, `roles`, `typeProfileId`, `villePartenaireId`. |
| `GET /users/{id}` / `PUT /users/{id}` / `DELETE /users/{id}` | Détail / modification / suppression. |
| `PATCH /users/{id}/status` | Activer/désactiver un compte. |
| `PATCH /users/{id}/reset-password` | Réinitialiser le mot de passe. |
| `GET /users/{id}/login-history` | Historique de connexion. |
| `GET /users/roles` | Liste des rôles disponibles — **dynamique, pas besoin de coder `USER`/`ADMIN` en dur côté frontend.** |
| `GET /users/export` | Export. |

### Requête `POST /users` (`UserProfileRequest`) — champs réels

```json
{
  "username": "jkouassi",
  "password": null,
  "roles": "ADMIN",
  "typeProfileId": null,
  "firstname": "Jean",
  "lastname": "Kouassi",
  "phone": "0707070707",
  "email": "j.kouassi@assurzen.ci"
}
```

Champs requis côté backend : `firstname`, `lastname`, `username`. `password` optionnel (probablement généré si absent, à confirmer par le test en direct). Écarts avec la proposition d'origine : `fullName` → `firstname`/`lastname` séparés ; champ `roles` (pas `role`) pour l'affectation réelle ; `typeProfileId` disponible ici aussi (permet, en théorie, de créer un compte back-office ET un profil partenaire en une fois — hors périmètre de ce besoin, on laisse `typeProfileId` vide pour les comptes back-office classiques).

### Réponse (`UserProfileResponse`)

Contient notamment `userId`, `username`, `roles`, `userActive`, `profileId`, `firstname`, `lastname`, `phone`, `email`, **`temporaryPassword`** (conforme à la proposition — mot de passe généré renvoyé une fois) et **`notificationEnvoyee`** (conforme à la recommandation §5 — confirmation d'envoi du SMS).

### Impact sur §7 (frontend)

`core/profile/profile.service.ts` n'est **pas** le bon endroit pour la création — un nouveau service dédié `core/user/user.service.ts` doit être créé pour consommer `/users/*`, distinct de `ProfileService` existant.

`temporaryPassword` uniquement présent si généré par le backend (voir ci-dessus) — jamais renvoyé sur les lectures ultérieures (`GET /profiles/{id}`).

## 5. SMS de bienvenue (les deux flux)

Un SMS doit être envoyé à la création de tout compte (automatique ou manuel), avec deux textes distincts selon le public — **tranché** avec le produit, pas de message générique unique :
- **Flux A (partenaire)** : message de bienvenue dans le réseau partenaire AssurZen, orienté application mobile.
- **Flux B (back-office)** : message dédié ("compte back-office créé"), avec le lien vers ce back-office Angular plutôt que vers l'appli mobile.

Dans les deux cas : identifiant de connexion (`username`) et mot de passe initial.

**Recommandation** : réutiliser le système de modèles de communication déjà construit (`fidelisation/campagnes/modeles`, canal SMS) plutôt qu'un message codé en dur côté backend — cohérent avec `BIENVENUE_SOUSCRIPTION` déjà en place pour un cas similaire. Deux nouveaux modèles à créer : un pour l'accès mobile partenaire, un pour l'accès back-office. Variables nécessaires : `username`/`telephone`, `motDePasse`, éventuellement `nomPartenaire`.

Le champ de réponse `notificationEnvoyee: boolean` (déjà utilisé ailleurs dans l'API, ex. suivi sinistre) serait utile ici aussi pour confirmer côté frontend que le SMS est parti.

## 6. Points à trancher côté backend/produit

1. ~~**`declencheCreationCompte` sur Type Profil, ou test sur le libellé `"REPRESENTANT"` ?**~~ — **Tranché.** Confirmé : la création automatique de compte ne s'applique que lorsqu'il s'agit bien d'un partenaire (Type Profil `REPRESENTANT`). Le booléen `declencheCreationCompte` (§3.1) reste l'implémentation recommandée pour porter cette règle côté backend, plutôt qu'un test sur le libellé — au backend de confirmer s'il l'implémente ainsi ou autrement, tant que le comportement (scope strictement limité aux partenaires) est respecté.
2. ~~**Conflit de téléphone déjà utilisé comme `username`**~~ — **Tranché.** Le partenaire est sauvegardé quand même, l'échec de création de compte est signalé séparément (`compteCree: false` + message d'erreur dédié), **et l'admin doit pouvoir corriger le numéro de téléphone directement depuis la fiche partenaire pour relancer la création de compte** — pas de blocage définitif, un chemin de résolution simple doit exister dans l'écran `views/partenaire-affaire/`.
3. ~~**Mot de passe des comptes back-office**~~ — **Tranché.** Généré aléatoirement par compte (pas de valeur fixe comme pour les partenaires) — voir §4.1.
4. ~~**Valeurs de rôle valides**~~ — **Tranché.** On reste sur `USER`/`ADMIN` pour l'instant, pas d'autres rôles à prévoir dans le formulaire de création back-office.
5. ~~**Texte du SMS back-office**~~ — **Tranché.** Deux modèles distincts (pas un message générique) : un pour l'accès partenaire/mobile ("bienvenue dans le réseau partenaire"), un pour l'accès back-office ("compte back-office créé, connectez-vous sur [lien back-office]") — voir §5.
6. ~~**Téléphone du partenaire**~~ — **Tranché.** Nouveau champ sur `Partenaire`, comme proposé en §3.1. Confirmé par le produit : aucune source de vérité existante pour ce numéro ailleurs dans le modèle de données.

## 7. Ce que le frontend fera une fois l'API livrée

- `core/partenaire/models/partenaire.model.ts` : ajout de `typeProfileId`, `telephone`, `numeroWaveCommission`.
- `views/partenaire-affaire/` : ajout des champs Type Profil (select), Téléphone et Numéro Wave (commissions) au formulaire ; affichage d'une confirmation quand `compteCree: true` sur la réponse de sauvegarde ; en cas de conflit (`compteCree: false`), message d'erreur explicite + champ Téléphone modifiable directement pour corriger et relancer la création de compte sans quitter la fiche.
- `core/configuration/models/type-profile.model.ts` : ajout de `declencheCreationCompte` (si retenu) + case à cocher correspondante dans `views/configuration/type-profile/`.
- `core/profile/profile.service.ts` : nouvelle méthode `create()` pour `POST /profiles`.
- **Nouveau module** `views/utilisateurs/` (Administration → Utilisateurs) : liste des comptes back-office + formulaire de création (username, nom complet, email, téléphone, rôle), sur le modèle des écrans Administration déjà construits (`deverrouillage-compte`, `configuration-dynamique`).
- Aucun changement nécessaire sur le flux "changer le mot de passe à la première connexion" — déjà opérationnel.
- Hors périmètre de ce contrat mais à garder en tête : le module Comptabilité → Commissions (`comptabilite/commissions`) pourrait avoir besoin d'évoluer séparément si le canal de paiement Wave doit y être reflété (voir §3.1).

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
