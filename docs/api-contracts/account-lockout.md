# Contrat d'interface — Verrouillage de compte après échecs de connexion (`POST /auth/login`)

> ✅ **Entièrement livré côté backend et câblé côté frontend, confirmé le 2026-08-10 en deux passes.** `POST /auth/login` renvoie `401` (`{error, message, timestamp, remainingAttempts}`) sur mot de passe incorrect et `423` (même enveloppe + `lockedUntil`, `remainingSeconds`) une fois le seuil `MAX_LOGIN_ATTEMPTS` atteint. Le déverrouillage manuel admin (§4.2) est livré : `POST /auth/accounts/{username}/unlock` (`ROLE_ADMIN` requis, `403` sinon). Nouvelle config dynamique `ACCOUNT_LOCKOUT_DURATION_MINUTES` (défaut 15 min, module SECURITY). **Non livré** : la liste des comptes verrouillés (§4.3) — restée optionnelle comme proposé.
>
> **Historique de la vérification, pour mémoire** : une première vérification directe (09:59) a capturé le champ d'erreur sous le nom `code` ; l'équipe backend a ensuite transmis un artifact de réponse au contrat (10:48+) indiquant `error`, et une revérification directe (18:13) a confirmé `error` — le backend a donc changé ce nom de champ entre les deux mesures, dans la même journée. Le contenu d'un document externe (même officiel) ne doit jamais être pris pour argent comptant sans revérification contre l'API réelle — c'est ce qui a été fait ici avant de corriger le code.
>
> Frontend : `views/pages/login/login.component.ts` (§9, compte à rebours live basé sur `remainingSeconds`) et `views/deverrouillage-compte/` (§10, écran Administration → Déverrouillage de compte, distingue le `403` d'un identifiant erroné).
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Besoin métier

Bloquer temporairement un compte après un nombre configurable de tentatives de connexion échouées consécutives, pour se protéger contre le bruteforce de mot de passe. Le seuil doit être piloté par la configuration dynamique **`MAX_LOGIN_ATTEMPTS`** déjà en place, pas une valeur codée en dur côté backend — c'est précisément le cas d'usage pour lequel le module Configuration Dynamique a été construit (modification à chaud, sans redémarrage serveur).

## 2. Ce qui existe déjà côté backend et doit être réutilisé, pas dupliqué

| Brique existante | Rôle dans ce contrat |
|---|---|
| `GET /configurations/MAX_LOGIN_ATTEMPTS` (module Configuration Dynamique) | Source de vérité du seuil. À lire dynamiquement à chaque tentative (pas en cache au démarrage), avec un repli sur `5` si la clé est absente/désactivée. |
| Audit Trail Engine (`/audit-logs`, `HistoriqueModificationResponse`) | Cible naturelle pour tracer les verrouillages/déverrouillages (voir §5). Déjà utilisé pour `LOGIN`/`LOGOUT` (actions déjà présentes dans l'enum `action`). |
| `POST /auth/login`, `POST /auth/login/otp-verify` | Endpoints existants à faire évoluer — voir §4.1, aucun endpoint renommé ni cassé. |

## 3. Fonctionnement attendu

### 3.1 Comptage des échecs

- Un compteur d'échecs par compte (`username`), incrémenté à chaque réponse d'échec de `POST /auth/login` due à un **mot de passe incorrect**.
- Remise à zéro dès qu'une connexion réussit — que ce soit directement (`200` avec tokens) ou après un challenge OTP réussi (`POST /auth/login/otp-verify` → `200`).
- **Question ouverte, recommandation ci-dessous** : est-ce que les échecs de code OTP comptent dans le même compteur ? Voir §7.

### 3.2 Seuil

- Lu dynamiquement depuis `MAX_LOGIN_ATTEMPTS` à chaque tentative. Exemple avec la valeur actuelle (`5`) : la 5ᵉ tentative échouée consécutive déclenche le verrouillage.

### 3.3 Verrouillage

- À la tentative qui atteint le seuil, le compte passe en état **verrouillé** pour une durée limitée.
- Durée recommandée : nouvelle clé de configuration dynamique dédiée, par exemple `ACCOUNT_LOCKOUT_DURATION_MINUTES` (même module `SECURITY`, même mécanisme que `MAX_LOGIN_ATTEMPTS`), valeur par défaut suggérée **15 minutes**. Cohérent avec l'existant plutôt qu'une valeur codée en dur.
- Déverrouillage automatique à l'expiration du délai — pas d'action requise de l'utilisateur au-delà d'attendre.
- En complément, un **déverrouillage manuel par un administrateur** est recommandé (voir §4.2) : un utilisateur légitime bloqué par erreur ne devrait pas être obligé d'attendre le délai complet.

### 3.4 Comportement précis attendu par tentative

| Tentative | Comportement attendu |
|---|---|
| 1 à N-1 (mot de passe incorrect, compte pas encore verrouillé) | `401`, comme aujourd'hui, idéalement enrichi d'un compteur de tentatives restantes (voir §4.1) |
| Nᵉ tentative (celle qui atteint le seuil) | `423 Locked` directement — pas un `401` "dernière chance" |
| Toute tentative pendant la fenêtre de verrouillage | `423 Locked` **immédiatement, sans vérifier le mot de passe fourni** — évite de laisser un attaquant continuer à deviner pendant le blocage, et évite de réinitialiser silencieusement le minuteur |
| Connexion réussie après expiration du délai | `200` normal, compteur remis à zéro |

## 4. Contrat API proposé

### 4.1 `POST /auth/login` — réponses enrichies

Le `200` (succès direct ou challenge OTP) ne change pas.

**Échec, compte pas encore verrouillé — `401`, corps enrichi :**

```json
{
  "error": "INVALID_CREDENTIALS",
  "message": "Identifiant ou mot de passe incorrect.",
  "remainingAttempts": 2
}
```

`remainingAttempts` est un bonus UX (permet d'afficher "Il vous reste 2 tentative(s) avant blocage") — si trop coûteux à calculer côté backend pour cette première version, le champ peut être omis, le frontend se rabattra sur le message générique actuel.

**Compte verrouillé (tentative qui déclenche le blocage, ou tentative pendant le blocage) — nouveau statut `423 Locked` :**

```json
{
  "error": "ACCOUNT_LOCKED",
  "message": "Compte temporairement bloqué suite à trop de tentatives de connexion échouées. Réessayez dans 15 minutes.",
  "lockedUntil": "2026-08-10T14:35:00Z",
  "remainingSeconds": 892
}
```

`lockedUntil` (ISO 8601 UTC) et `remainingSeconds` sont redondants par design — le frontend utilisera l'un ou l'autre selon ce qui est le plus simple à consommer, les deux peuvent être fournis sans coût significatif.

### 4.2 Déverrouillage manuel (admin) — recommandé, pas strictement bloquant pour un premier livrable

```
POST /auth/accounts/{username}/unlock
```

- Réservé à un rôle administrateur (à définir selon le système d'autorisation backend).
- Réponse `204 No Content`.
- Effet : remet le compteur d'échecs à zéro et lève le verrouillage immédiatement, indépendamment du `lockedUntil` en cours.

### 4.3 Lister les comptes actuellement verrouillés — optionnel, v2

```
GET /auth/accounts/locked
```

Utile pour un futur écran de support/monitoring côté Administration, mais pas indispensable pour la première livraison — peut être reporté sans bloquer le reste du contrat.

## 5. Intégration avec l'Audit Trail Engine (déjà en place)

`/audit-logs` existe déjà et journalise déjà les actions `LOGIN`/`LOGOUT`. Recommandation : journaliser aussi le verrouillage et le déverrouillage comme événements `HistoriqueModificationResponse` — `module: "SECURITY"`, `entityName: "Utilisateur"`, `entityId: <username ou userId>`, `statut: "ECHEC"` pour le verrouillage. Deux options pour le champ `action` :

- réutiliser `SUSPEND` (verrouillage) / `RESTORE` (déverrouillage) parmi les 18 valeurs déjà supportées par l'enum, sans rien ajouter au schéma existant ;
- ou ajouter deux valeurs explicites (`ACCOUNT_LOCKED`/`ACCOUNT_UNLOCKED`) si une lecture plus directe des journaux est préférée côté backend.

Choix laissé à l'équipe backend — les deux options fonctionnent avec le frontend existant (`shared/audit-history/`), qui affiche déjà `action` tel quel.

## 6. Notification utilisateur — optionnel, v2

Prévenir l'utilisateur par SMS/email au moment du verrouillage serait une amélioration UX naturelle (l'app a déjà une infrastructure SMS pour l'OTP et les rappels d'échéance), mais n'est pas nécessaire pour un premier livrable — le message d'erreur à l'écran de connexion suffit dans un premier temps.

## 7. Points à trancher côté backend/produit avant implémentation

1. **Les échecs de code OTP comptent-ils dans le même compteur que les échecs de mot de passe ?** Recommandation : **non**, pour cette première version — cibler uniquement `POST /auth/login` (mot de passe), car (a) c'est ce que décrit littéralement le libellé actuel de `MAX_LOGIN_ATTEMPTS` ("tentatives de connexion avant blocage"), et (b) le canal OTP a déjà sa propre protection (rate-limit sur `/auth/login/otp-resend`). Peut être élargi en v2 si le besoin se confirme.
2. **Durée du blocage : nouvelle config dynamique dédiée ou valeur fixe ?** Recommandation : nouvelle clé `ACCOUNT_LOCKOUT_DURATION_MINUTES`, cohérent avec l'existant.
3. **Déverrouillage manuel admin (§4.2) : inclus dans cette livraison ou reporté ?** Recommandation : inclus — c'est un besoin support réel (utilisateur légitime bloqué qui ne peut pas attendre).
4. **`remainingAttempts` sur les `401` (§4.1) : faisable simplement ?** Si non, pas bloquant — le frontend se contente du message générique actuel dans ce cas.
5. **Verrouillage par compte uniquement, ou aussi par IP ?** Le présent contrat ne couvre que le verrouillage par compte (protection contre le bruteforce ciblé sur un utilisateur). Une protection par IP (contre un attaquant qui teste de nombreux comptes) est un besoin distinct, hors périmètre ici — à évaluer séparément si jugé nécessaire.

## 8. Ce que le frontend fera une fois l'API livrée

- `views/pages/login/login.component.ts` : le bloc `error:` du `login()` (actuellement un message générique unique, sans inspection du statut HTTP) sera étendu pour distinguer `401` (message + `remainingAttempts` si présent) et `423` (message dédié + compte à rebours basé sur `lockedUntil`/`remainingSeconds`, formulaire désactivé jusqu'à expiration).
- `core/auth/models/auth.model.ts` : ajout d'interfaces `LoginFailureResponse`/`AccountLockedResponse` pour typer ces deux corps d'erreur.
- Aucun nouvel écran nécessaire pour le MVP utilisateur final. Si les endpoints admin (§4.2/§4.3) sont livrés, un écran minimal pourra être ajouté plus tard sous **Administration**, sur le modèle de ce qui a déjà été fait pour l'Audit Trail Engine.

## 9. Ce qui a réellement été câblé côté frontend (2026-08-10)

Schéma réel confirmé par appel direct (identifiant inexistant, sans risque) :

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Identifiants invalides",
  "timestamp": "2026-08-10T09:59:55.454984Z",
  "remainingAttempts": null
}
```

`remainingAttempts` vaut `null` pour un identifiant inexistant (le backend ne dévoile jamais si le compte existe — bon réflexe sécurité) ; vaut vraisemblablement un nombre pour un compte réel avec mauvais mot de passe (non vérifié en direct, pour ne pas risquer de verrouiller un vrai compte).

- `core/auth/models/auth.model.ts` : nouvelle interface `LoginFailureResponse` (`code, message, timestamp, remainingAttempts`).
- `views/pages/login/login.component.ts` : le callback `error` de `login()` distingue maintenant `HttpErrorResponse.status` :
  - `423` → alerte `warning`, affiche `body.message` du backend tel quel (déjà en français, déjà formulé correctement) ;
  - `401` → alerte `danger`, affiche `body.message` + `"(il reste N tentative(s) avant blocage du compte)"` si `remainingAttempts` est un nombre ;
  - tout autre statut → message générique inchangé.
- Pas de compte à rebours basé sur une heure de déverrouillage (`lockedUntil`) : ce champ n'a pas été confirmé dans la réponse réelle, donc pas construit dessus. Si le backend confirme son existence, un compte à rebours pourra être ajouté facilement.
- Vérifié en direct : cas `401` réel (identifiant inexistant) affiche bien "Identifiants invalides" ; cas `423` et `401` avec tentatives restantes vérifiés par pilotage direct des signaux du composant (rendu confirmé, sans déclencher de vrai verrouillage sur un compte réel).

## 10. Déverrouillage manuel admin — câblé (2026-08-10)

`POST /auth/accounts/{username}/unlock` confirmé via `/v3/api-docs` (`security: bearerAuth`, `200` documenté côté Swagger — l'artifact backend §5 mentionne `204 No Content` avec `403` si non-admin ; écart non résolu entre les deux sources, sans impact frontend puisque `AuthService.unlockAccount()` ignore le corps et le code exact de succès).

- `core/auth/auth.service.ts` : nouvelle méthode `unlockAccount(username)`.
- `views/deverrouillage-compte/` (nouveau, route `administration/deverrouillage-compte`, nav sous Administration) : écran minimal à un seul champ (nom d'utilisateur) + confirmation avant l'appel, message de succès/erreur. Pas de liste des comptes verrouillés (l'API ne le permet pas, voir §4.3) — l'utilisateur doit connaître le compte à déverrouiller (ex. repéré dans Interceptors → Audit sur une entrée `POST /auth/login` en `423`).

## 11. Correction post-réponse backend (2026-08-10, plus tard le même jour)

L'équipe backend a transmis un artifact "Réponse au contrat" confirmant la livraison complète et signalant que le champ d'erreur s'appelle `error` (pas `code`) sur les réponses `401`/`423` de `POST /auth/login` — un écart avec ce qui avait été capturé en §9. **Revérifié en direct avant toute modification de code** (`fetch` direct, identifiant inexistant, sans risque) : confirmé, le champ est bien `error` au moment de cette revérification (18:13), alors qu'il était `code` lors de la toute première vérification (09:59) — le backend a donc changé ce détail entre les deux mesures. Corrections apportées :

- `core/auth/models/auth.model.ts` : `LoginFailureResponse.code` → `LoginFailureResponse.error` ; ajout de `lockedUntil?: string` et `remainingSeconds?: number` (confirmés présents sur `423` par l'artifact, non vérifiés en direct pour ne pas verrouiller un vrai compte — mais le champ `remainingSeconds` est maintenant exploité côté frontend avec dégradation gracieuse si absent, voir ci-dessous).
- `views/pages/login/login.component.ts` : nouveau compte à rebours live (`lockedRemainingSeconds`, pattern `setInterval` identique à celui déjà utilisé dans `otp.component.ts`) affiché sous le message de verrouillage ("Nouvelle tentative possible dans MM:SS"), bouton "Se connecter" désactivé tant que le compte est verrouillé. Alimenté uniquement si `remainingSeconds` est présent dans la réponse — sinon comportement identique à avant (pas de compte à rebours, juste le message backend).
- `views/deverrouillage-compte/deverrouillage-compte.component.ts` : distingue maintenant `403` (droits insuffisants, message dédié) d'un échec générique (identifiant probablement erroné) — confirmé par l'artifact §5 que `403` est le comportement réel sans jeton admin valide.

**Leçon retenue** : un document reçu (artifact, mail, contrat) — même transmis comme "officiel" par l'équipe backend — reste une affirmation à vérifier, pas un fait acquis, surtout quand il contredit une mesure directe déjà faite. La revérification en direct ici a confirmé le document, mais ç'aurait pu être l'inverse.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
