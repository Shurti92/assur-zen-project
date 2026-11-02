# Rapport de bug — Renvoi de code OTP (`POST /auth/login/otp-resend`)

> 🐞 **Bug backend confirmé, pas encore corrigé.** Reproduit en direct le 2026-08-06 contre l'environnement de dev (`https://localhost:9190`). Frontend déjà adapté défensivement côté Angular (`views/pages/otp/otp.component.ts`) pour ne plus bloquer l'utilisateur dans une impasse, mais ça ne corrige pas la cause côté serveur.
>
> Suivi dans la mémoire projet "dépendances backend manquantes" (entrée 5, ajoutée 2026-08-06).

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Endpoint concerné

```
POST /api-assur-zen/auth/login/otp-resend
{ "challengeToken": "<token reçu de POST /auth/login>" }
```

D'après sa description Swagger ("Redemander le code OTP pour un challenge de login (2FA) en cours"), cet endpoint est censé pouvoir être appelé plusieurs fois pour un même challenge tant que celui-ci est actif — c'est tout l'intérêt d'un bouton "Renvoyer le code".

## 2. Comportement observé

Séquence testée en direct (login → écran OTP → clics successifs sur "Renvoyer") :

| # | Délai après le login | Résultat |
|---|---|---|
| 1 | ~15 s (challenge tout frais, code encore valide 4 min 30) | `429 Too Many Requests` |
| 2 | Immédiatement après (re-clic) | `429 Too Many Requests` |
| 3 | Après le cooldown de 60 s appliqué côté frontend | `500 Internal Server Error` |
| 4 | Nouvelle tentative | `500 Internal Server Error` |
| 5 | Nouvelle tentative | `500 Internal Server Error` |

Le `429` initial est un comportement de sécurité légitime (rate-limit) — aucun souci de ce côté. Le problème est la suite : **une fois le rate-limit "consommé" une première fois sur un challenge, toutes les tentatives suivantes échouent avec un `500`**, y compris bien après l'expiration du cooldown, et y compris si le code affiché côté frontend n'a pas encore expiré (le compte à rebours de 5 min était toujours actif lors des tentatives #3 à #5).

Aucun corps de réponse exploitable n'a pu être inspecté depuis le frontend (juste le code HTTP), donc la nature exacte de l'exception côté serveur n'est pas connue de notre côté.

## 3. Comportement attendu

L'un des deux, selon la logique métier voulue :

- **(a)** Le renvoi réussit normalement (`200`) et un nouveau code SMS est envoyé pour le même challenge, comme le suggère la description de l'endpoint.
- **(b)** Si le challenge est réellement devenu invalide pour une raison métier (ex. trop de renvois cumulés, challenge expiré), retourner une erreur **4xx explicite** (ex. `400`/`410` avec un message du type `"Challenge expiré, reconnectez-vous"`) — jamais un `500`.

Dans les deux cas, un `500` signale une exception non gérée côté serveur et ne devrait jamais être le comportement normal d'un endpoint applicatif.

## 4. Impact utilisateur

Une fois qu'un utilisateur déclenche le rate-limit une seule fois (par exemple en cliquant deux fois rapidement sur "Renvoyer" par réflexe), il n'a alors **plus aucun moyen de recevoir un nouveau code** pour cette tentative de connexion — la seule issue est de se reconnecter entièrement depuis `/login`. Le renvoi de code, censé être une fonctionnalité de confort, devient à sens unique.

## 5. Ce qui a déjà été fait côté frontend (`views/pages/otp/otp.component.ts`)

- Détection explicite du `429` : message honnête ("Trop de tentatives de renvoi. Réessayez dans 60 secondes...") + cooldown visible avant de permettre un nouveau clic, plutôt que de laisser l'utilisateur marteler le bouton.
- Pour toute autre erreur (dont le `500` observé) : affichage d'un message générique + deux actions claires, "Réessayer" et "Se reconnecter", au lieu d'un texte statique sans action.
- Le nom d'utilisateur est désormais pré-rempli sur `/login` au retour (`views/pages/login/login.component.ts`), pour réduire la friction quand la reconnexion complète reste la seule issue.

Ces ajustements limitent les dégâts côté UX mais ne remplacent pas un correctif serveur — sans lui, "Réessayer" échouera systématiquement avec un `500` tant que le bug persiste.

## 6. Reproduction

1. `POST /auth/login` avec des identifiants valides → récupérer le `challengeToken`.
2. `POST /auth/login/otp-resend` avec ce token → `429` (attendu, rate-limit).
3. Attendre ~60 s.
4. `POST /auth/login/otp-resend` à nouveau avec le **même** `challengeToken` → `500` (inattendu).
5. Répéter l'étape 4 → `500` de façon systématique.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes" (entrée 5, ajoutée 2026-08-06).
