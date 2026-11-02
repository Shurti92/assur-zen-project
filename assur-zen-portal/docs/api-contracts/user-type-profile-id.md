# Contrat d'interface — Exposer le Type Profil de l'utilisateur connecté (`User`)

> ✅ **Livré côté backend, confirmé via `/v3/api-docs` le 2026-08-27** — `typeProfileId` (optionnel) est bien apparu sur le schéma `User`. Le frontend était déjà écrit pour le consommer sans changement de code (`AuthUser.typeProfileId`, `core/auth/nav-permissions.service.ts`) — non testé en direct avec un vrai login `CO-FOUNDER` à cette date, à vérifier au premier compte réel de ce type.
>
> ~~Aucune API n'expose aujourd'hui le `typeProfileId` de l'utilisateur connecté.~~ Vérifié le 2026-08-18 via `GET /v3/api-docs` : le schéma `User` (renvoyé par `POST /auth/login` et `POST /auth/login/otp-verify`) n'avait pas de champ `typeProfileId`. `UserProfileResponse` (renvoyé par `GET /users`, la liste) non plus. Il n'existait même pas de `GET /users/{id}` pour aller le chercher a posteriori (seuls `PUT`/`DELETE /users/{id}` existaient). Pourtant `UserProfileRequest`/`UserProfileUpdateRequest` (les DTOs d'écriture de `POST`/`PUT /users`) avaient bien un champ `typeProfileId` — on pouvait donc **écrire** cette valeur à la création/modification d'un compte, mais jamais la **relire**.
>
> **Besoin métier** : restreindre le menu de navigation du back-office Angular selon le Type Profil de l'utilisateur connecté — les comptes de Type Profil `CO-FOUNDER` (id `2`) ne doivent pas voir les menus "Paramétrage" et "Administration", contrairement à `ADMIN` (id `1`) et `REPRESENTANT` (id `5`) qui gardent l'accès complet. Le frontend a besoin de connaître le `typeProfileId` de l'utilisateur **immédiatement après connexion**, sans appel supplémentaire si possible.
>
> **Contournement partiel déjà en place côté frontend, mais insuffisant** : `User.partenaireId` existe déjà et permet de faire `GET /partenaires/{id}` → `typeProfileId` — mais ça ne fonctionne que pour un compte créé automatiquement depuis une fiche Partenaire (aujourd'hui, seul le Type Profil `REPRESENTANT` a `declencheCreationCompte: true`). Un compte `CO-FOUNDER` est forcément créé manuellement via `POST /users` (Administration → Utilisateurs), et ce endpoint n'accepte même pas de `partenaireId` — donc ce contournement ne peut structurellement pas fonctionner pour lui. Cette demande concerne spécifiquement ce cas.
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Schéma actuel de `User`

```ts
interface User {
  id: string;
  fullName: string;
  email: string;
  role: string;               // "USER" | "ADMIN"
  sector?: string;
  avatarUrl?: string;
  mustChangePassword: boolean;
  // ... (bio, réseaux sociaux, adresse — sans rapport)
  twoFactorEnabled?: boolean;
  langueCode?: string;
  partenaireId?: number;      // présent seulement pour un compte auto-créé depuis un Partenaire
}
```

Renvoyé par `POST /auth/login` (directement, ou après `POST /auth/login/otp-verify` si un OTP est requis) dans `LoginResponse.user`.

## 2. Proposition de schéma

Ajouter un champ optionnel :

```ts
interface User {
  // ... champs existants inchangés
  typeProfileId?: number;     // nouveau — FK vers /type-profiles
}
```

Rempli avec le `typeProfileId` du compte utilisateur (celui déjà stocké côté backend via `UserProfileRequest.typeProfileId` à la création/modification, mais jamais relu jusqu'ici). `undefined`/absent si aucun Type Profil n'est assigné à ce compte.

## 3. Pourquoi sur `User` (connexion) plutôt qu'un nouvel endpoint

Le frontend a besoin de cette information dès l'affichage du menu de navigation, juste après connexion — un `GET /users/{id}` séparé impliquerait un aller-retour réseau supplémentaire à chaque chargement de session (y compris au rafraîchissement de page), pour une donnée qui ne change quasiment jamais en cours de session. L'ajouter directement sur `User` évite ce coût et reste cohérent avec `role`/`partenaireId`, déjà présents sur ce même objet pour un besoin similaire (piloter l'UI selon le profil du compte connecté).

Si un endpoint dédié existe déjà en interne côté backend pour lire un utilisateur (même sans façade REST aujourd'hui), l'alternative `GET /users/{id}` conviendrait aussi — l'important est qu'un moyen quelconque existe pour relire cette valeur.

## 4. Impact frontend dès livraison

Aucun changement de code nécessaire : `core/auth/models/auth.model.ts` (`AuthUser.typeProfileId`) et `core/auth/nav-permissions.service.ts` sont déjà écrits pour utiliser ce champ dès qu'il sera présent dans la réponse de connexion — actuellement `undefined`, ce qui fait retomber sur le contournement partiel (§ci-dessus) puis, en dernier recours, sur un accès complet par défaut (aucun menu masqué à tort tant que la donnée est absente).

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
