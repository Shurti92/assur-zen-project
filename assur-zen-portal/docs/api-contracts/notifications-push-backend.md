# Notifications Web Push — réponse backend (livré le 2026-09-26)

> ✅ **Livré en dev, validé de bout en bout sous Edge (WNS) le 2026-09-26.** Répond au contrat de l'équipe EP (`assurzen-ep/docs/api-contracts/notifications-push.md`).
> Web Push standard (RFC 8030) + chiffrement RFC 8291 + VAPID RFC 8292, **sans Firebase**.

Base URL gateway : `https://localhost:9190/api-assur-zen`. Toutes les routes exigent le JWT : l'utilisateur est **toujours** celui du jeton, jamais un id du payload.

## 1. Abonnements (appareil courant)

### `POST /notifications/push-subscriptions` → `201`
```json
{ "endpoint": "https://fcm.googleapis.com/fcm/send/…", "expirationTime": null,
  "keys": { "p256dh": "…", "auth": "…" } }
```
- Upsert par `endpoint`, qui est unique. Un endpoint déjà enregistré, même par un autre compte, est mis à jour, réactivé et **rattaché au compte connecté**.
- Plusieurs abonnements par utilisateur, un par navigateur ou appareil.
- `400` : `endpoint` non `https`, ou `keys`, `p256dh` ou `auth` absents ou mal formés. `401` : pas de jeton.
- Réponse : `{ "id", "active", "createdAt", "updatedAt" }`. Les clés ne sont jamais renvoyées.

### `DELETE /notifications/push-subscriptions` → `204`
`{ "endpoint": "https://…" }`. Désactive l'abonnement **uniquement s'il appartient au compte connecté**. Renvoie `204` même si l'endpoint est inconnu ou appartient à un autre compte ; son existence n'est pas révélée.

### `GET /notifications/push/public-key` → `200`
`{ "enabled": true, "publicKey": "B…" }`. `enabled=false` et `publicKey=null` si le push n'est pas configuré ou est coupé dans l'environnement. Permet au front de ne pas dupliquer la clé ; `VITE_VAPID_PUBLIC_KEY` reste possible.

## 2. Messages envoyés

Un push est envoyé à **tous les appareils actifs du partenaire** à la création de chaque `PartenaireNotification` :

| Type | `title` | `body` | SMS |
|---|---|---|---|
| `COMMISSION_GAGNEE` | Nouvelle commission disponible | Une nouvelle commission est disponible dans votre espace partenaire. | seulement si aucun appareil n'est atteint |
| `COMMISSION_DISPONIBLE` (rappel) | Commissions disponibles | Vous avez des commissions disponibles. Vous pouvez en demander le paiement depuis votre espace. | seulement si aucun appareil n'est atteint |
| `COMMISSION_VERSEE` | Commission versee | Votre commission a ete versee sur votre compte Wave. | toujours (confirmation de paiement) |

```json
{ "title": "…", "body": "…", "data": { "type": "COMMISSION_GAGNEE", "reference": "4521", "route": "/commissions" } }
```
- `reference` = id de la `PartenaireNotification`, qui sert d'anti-doublon avec `type`.
- Le message ne contient **aucun montant**, aucun numéro de téléphone ou Wave, aucun jeton.
- En-têtes : `TTL: 86400`, `Urgency: normal`.
- Le push est envoyé **après la validation de la transaction, en asynchrone**. Un échec n'annule jamais l'opération métier ni la notification dans l'application.
- Suivi par notification : `pushStatus` = `NONE` (aucun appareil), `PENDING`, `SENT` ou `FAILED`. Ce champ est ajouté aux réponses des notifications.

**Nettoyage :**
- `404` ou `410` du service push, ou clés du navigateur inutilisables → abonnement désactivé.
- `429`, `5xx`, délai dépassé → journalisé. L'abonnement n'est désactivé qu'après 5 échecs consécutifs (`webpush.max-failures`).

## 3. Centre de notifications de l'utilisateur connecté

Aucun id dans l'URL. Ces routes font la même chose que `/partenaires/{id}/notifications`, qui reste disponible.

| Méthode | Route | Réponse |
|---|---|---|
| `GET` | `/notifications?nonLuesSeulement=false&page=0&size=20` | `PageResponse<PartenaireNotificationResponse>` ; page vide si le compte n'est pas un partenaire |
| `GET` | `/notifications/unread-count` | `{ "count": 3 }` |
| `PUT` | `/notifications/{id}/read` | la notification ; `404` si elle n'appartient pas au compte |
| `PUT` | `/notifications/read-all` | `{ "count": 3 }` |

## 4. Configuration et déploiement — clés VAPID automatiques

**Rien à faire au déploiement** (depuis le 2026-09-26) : chaque environnement a sa propre paire de clés, créée automatiquement.

1. **Premier démarrage d'un WAR dans un environnement** : si `VAPID_PUBLIC_KEY` et `VAPID_PRIVATE_KEY` sont absentes de l'environnement, le backend génère une paire. Il l'enregistre en base, dans la configuration dynamique :
   - `WEBPUSH_VAPID_PUBLIC_KEY` en clair ;
   - `WEBPUSH_VAPID_PRIVATE_KEY` **chiffrée**, avec le même mécanisme que les identifiants SycaPay ;
   - les deux lignes ne sont pas modifiables depuis le back-office.

   Journal : `[WEBPUSH] paire VAPID generee et enregistree en base pour cet environnement`.
2. **Démarrages suivants** : la paire est relue en base, jamais régénérée. Journal : `cles VAPID chargees (base de donnees)`.
3. **Frontend** : il lit la clé publique via `GET /notifications/push/public-key`. Aucune clé à transmettre ni à configurer par environnement.
4. **Prérequis** : `APP_CONFIG_ENCRYPTION_SECRET` et `APP_CONFIG_ENCRYPTION_SALT` doivent être configurés sur le serveur (c'est le cas en prod depuis le 2026-09-24). Sans eux, **rien n'est généré** : la clé privée n'est jamais stockée en clair. Le push reste alors désactivé et les SMS partent comme avant.
5. **Priorité** : les variables `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` et `VAPID_SUBJECT` de l'environnement l'emportent sur la base. À utiliser quand une base a été copiée depuis un autre environnement, par exemple en local sur une copie de la prod, pour ne pas réutiliser les clés de la prod. `java deploy/GenerateVapidKeys.java` génère une paire au même format que `npx web-push generate-vapid-keys`. `VAPID_SUBJECT` vaut `https://assurzen.ci` par défaut.
6. **Base de données** : aucun script SQL. Hibernate crée la table `t_assur_zen_web_push_subscription` et les colonnes `push_status`, `push_sent_at` et `push_error` de `t_assur_zen_partenaire_notification` au démarrage.
7. **Coupure sans redéploiement** : mettre la clé dynamique `WEBPUSH_ENABLED` à `false`.
8. ⚠️ **Ne jamais supprimer ni modifier les lignes `WEBPUSH_VAPID_*`.** Une nouvelle paire invaliderait tous les abonnements : les utilisateurs devraient réactiver les notifications.

## 5. Méthodes internes disponibles pour d'autres modules

`WebPushNotificationService.sendToUser`, `sendToUsers`, `sendToRole` et `sendToPartner`. Chacune renvoie le nombre d'appareils atteints. Elles sont réservées au serveur : aucun endpoint ne permet d'envoyer une notification.
