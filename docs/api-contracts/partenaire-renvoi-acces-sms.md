# Contrat d'interface — Renvoi automatique des accès (SMS) à un partenaire déjà créé

> 🐛 **Bug frontend corrigé le 2026-08-31** — signalé par l'utilisateur : « au niveau du partenaire quand on fait la réinitialisation le SMS ne sort pas ». Cause identifiée dans `confirmResendAccess()` : la recherche du compte de connexion (`GET /users?q={telephone}`, limitée à 5 résultats) retombait sur `page.content[0]` quand aucune correspondance exacte `username === telephone` n'était trouvée dans cette page — un fallback qui pouvait envoyer le SMS de renvoi d'accès à un **autre** compte que celui du partenaire visé si la recherche renvoyait plusieurs comptes ou un ordre différent. Vu du partenaire concerné, ça se traduit exactement par « le SMS ne part jamais ». Corrigé : correspondance exacte obligatoire (sinon message d'erreur explicite invitant à vérifier le téléphone), taille de recherche portée à 20 par prudence.
>
> ✅ **Livré et vérifié en direct le 2026-08-27** — `POST /users/{id}/resend-access` (Option B de ce contrat) existe et fonctionne : confirmé via `/v3/api-docs` (réponse `UserProfileResponse`, aucun mot de passe en clair) puis câblé côté frontend (`UserService.resendAccess()`, `views/partenaire-affaire/partenaire-affaire.component.ts` → `confirmResendAccess()`). Le message de confirmation avant l'action ("aucun SMS automatique pour l'instant") était devenu obsolète et a été corrigé pour refléter le vrai comportement.
>
> ~~Fonctionnalité absente aujourd'hui, à ajouter côté backend.~~ Confirmé le 2026-08-25 en inspectant `/v3/api-docs` : aucun endpoint ne renvoie les identifiants d'un compte existant. Un raccourci frontend avait été construit en attendant (`views/partenaire-affaire/`, bouton "Renvoyer les accès") mais il ne faisait que régénérer un mot de passe via `PATCH /users/{id}/reset-password` — l'admin devait ensuite **transmettre le mot de passe lui-même** (téléphone, autre canal). Aucun SMS n'était envoyé automatiquement.
>
> Demande de l'utilisateur (2026-08-25) : "y'a-t-il aussi la possibilité de renvoyer les paramètres accès d'un partenaire déjà créé à qui nous avons donné la possibilité d'accéder au frontend ?"
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Contexte

Quand un `Partenaire` a pour Type Profil `REPRESENTANT` (`declencheCreationCompte: true`), le backend crée automatiquement un compte de connexion mobile à l'enregistrement (`username` = téléphone, mot de passe généré, rôle `USER`) et envoie un SMS de bienvenue avec les accès — voir `docs/api-contracts/creation-comptes-utilisateurs.md`. Ce mécanisme ne se déclenche **qu'une seule fois, à la création**.

Si le partenaire perd ce SMS, oublie son mot de passe, ou change de téléphone, il n'existe aujourd'hui aucun moyen de lui renvoyer ses accès automatiquement — seule option : un admin réinitialise le mot de passe via `PATCH /users/{id}/reset-password` (qui exige désormais `newPassword` dans le corps, voir correctif du 2026-08-25) puis doit communiquer ce mot de passe au partenaire par ses propres moyens.

## 2. Proposition

Réutiliser exactement le même mécanisme de SMS que celui de la création de compte (mêmes modèles de communication, catégorie "Bienvenue"), déclenché à la demande plutôt qu'automatiquement à la création. Deux façons possibles d'exposer ça, au choix du backend :

**Option A — étendre l'endpoint existant.** Ajouter un paramètre optionnel à `PATCH /users/{id}/reset-password`, par ex. `notifierParSms: boolean` dans le corps de la requête. Si `true`, le backend génère lui-même le mot de passe temporaire (comme il le fait à la création) et l'envoie par SMS au `phone` de l'utilisateur, plutôt que d'accepter un `newPassword` fourni par le frontend.

**Option B — nouvel endpoint dédié.**
```
POST /api-assur-zen/users/{id}/resend-access
```
Régénère un mot de passe temporaire et renvoie le SMS de bienvenue (identifiant + mot de passe), sans paramètre. Plus explicite qu'un flag sur l'endpoint de reset.

Dans les deux cas :
- Réponse : ajouter `notificationEnvoyee: boolean` (même pattern déjà utilisé sur `UserProfileResponse`/`ReclamationResponse`/`SinistreSuiviResponse`), pour confirmer côté frontend que le SMS est bien parti.
- Le mot de passe généré ne doit **pas** être renvoyé dans la réponse HTTP dans ce cas précis (contrairement au reset "manuel" actuel) — puisqu'il part par SMS directement au titulaire du compte, l'admin n'a pas besoin de le voir.

## 3. Ce qui est déjà prêt côté frontend

- `views/partenaire-affaire/partenaire-affaire.component.ts` — bouton "Renvoyer les accès" déjà câblé sur le mécanisme manuel actuel (recherche du compte par téléphone via `GET /users?q=`, puis `PATCH /users/{id}/reset-password`). Bascule vers la nouvelle option (A ou B) sera un changement mineur une fois le endpoint livré — pas de refonte nécessaire.
- Message de confirmation déjà honnête sur la limite actuelle ("il faudra le transmettre vous-même au partenaire, aucun SMS automatique pour l'instant") — à mettre à jour une fois le SMS automatique disponible.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
