# Rapport de bug — `/v3/api-docs` (endpoint public, sans authentification) enregistré comme une connexion dans l'historique de connexions

> 🐞 **Bug backend confirmé le 2026-08-25**, avec un effet de bord potentiellement grave : invalidation silencieuse de la session d'un administrateur en plein travail.
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190` (les appels concernés visent `/v3/api-docs`, PAS `/api-assur-zen/...`).

---

## 1. Constat

Sur `GET /historique-connexions` (module livré le 2026-08-24), l'historique du compte admin `0757873487` sur les dernières 24h montre 13 entrées, dont **7 avec `navigateur: "curl 8"` / `os: "Other"`** :

| Date/heure | Utilisateur | Navigateur |
|---|---|---|
| 25/08/2026 08:48:01 | 0757873487 | curl 8 |
| 25/08/2026 08:41:27 | 0757873487 | curl 8 |
| 25/08/2026 05:35:10 | 0757873487 | curl 8 |
| 25/08/2026 05:24:45 | **0705206698** | curl 8 |
| 25/08/2026 05:23:21 | 0757873487 | curl 8 |
| 25/08/2026 05:03:51 | 0757873487 | curl 8 |
| 25/08/2026 05:02:33 | 0757873487 | curl 8 |
| 24/08/2026 20:52:14 | 0757873487 | curl 8 |

Ces horaires correspondent **exactement**, à la minute près, à des appels `curl -s -k https://localhost:9190/v3/api-docs` faits depuis la machine de développement pour inspecter le schéma OpenAPI — un usage courant et légitime pendant le développement du frontend. **Aucun de ces appels n'envoyait de identifiant, de mot de passe, ni de header `Authorization`** : c'est une simple requête `GET` anonyme vers un endpoint de documentation qui devrait être public.

## 2. Pourquoi c'est un bug

1. `GET /v3/api-docs` n'est pas (et ne devrait pas être) un endpoint d'authentification — il ne devrait générer strictement aucune entrée dans un historique de connexions.
2. Le compte auquel ces requêtes anonymes sont attribuées (`0757873487`, `0705206698`) semble correspondre au **dernier utilisateur authentifié sur le poste**, ce qui suggère un contexte de sécurité (session/utilisateur courant) partagé ou mal isolé entre les requêtes — un vrai souci d'isolation, indépendamment du symptôme visible dans l'historique.
3. **Effet de bord suspecté, à confirmer côté backend** : si le mécanisme qui enregistre une "connexion" est couplé à une émission/rotation de token ou à une politique de session unique par compte, alors chacun de ces faux positifs a pu **invalider la session légitime de l'administrateur dans son navigateur**. Symptôme rapporté côté utilisateur le même jour : *"malgré les accès bons la connexion échoue, je suis obligé de réinitialiser mon compte à chaque fois"* — cohérent avec des sessions invalidées de façon répétée et imprévisible, sans lien réel avec le mot de passe.

## 3. Reproduction

```
curl -s -k https://localhost:9190/v3/api-docs
```

Sans aucun header d'authentification. Vérifier ensuite `GET /historique-connexions?userId=<id>` (ou l'écran équivalent) : une nouvelle entrée apparaît, attribuée à un compte utilisateur, alors qu'aucune authentification n'a eu lieu.

## 4. Comportement attendu

- `GET /v3/api-docs` ne doit générer aucune entrée dans l'historique de connexions — ce n'est pas un événement d'authentification.
- Si un mécanisme partagé (filtre de sécurité, contexte de session) attribue par erreur cette requête anonyme à "l'utilisateur courant", il faut vérifier qu'aucune requête non authentifiée ne peut jamais toucher à l'état de session d'un autre utilisateur (risque d'isolation plus large que le seul historique de connexions).
- Confirmer ou infirmer si une "connexion" enregistrée déclenche une rotation/invalidation de token ailleurs dans le système — si oui, corriger en priorité vu l'impact direct sur la capacité d'un administrateur à rester connecté.

## 5. Action immédiate

En attendant le correctif, je n'utiliserai plus `curl` vers `/v3/api-docs` (ni aucun autre appel direct au gateway) pendant le développement, pour ne pas continuer à perturber les sessions actives — j'inspecterai le schéma autrement (fichiers déjà récupérés, ou demande directe à l'équipe backend).

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
