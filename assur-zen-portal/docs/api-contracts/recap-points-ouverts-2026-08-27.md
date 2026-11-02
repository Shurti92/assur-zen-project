# Récapitulatif — 7 points ouverts côté API (relance backend, 2026-08-27, complété les 2026-09-01 et 2026-09-02)

> 📋 **Message de relance consolidé**, regroupant les points déjà documentés individuellement (voir les fichiers cités dans chaque section pour la reproduction complète et l'historique des échanges). Objectif : donner à l'équipe backend une vue d'ensemble unique à prioriser, plutôt que des fichiers dispersés.
>
> **Point 6 ajouté le 2026-09-01** (documents joints à un partenaire), **point 7 ajouté le 2026-09-02** (nouvelle preuve sur le `DELETE /contracts/{id}` déjà signalé au point 1) — les points 1 à 5 datent du message initial du 2026-08-27, certains ont depuis évolué (voir la mémoire projet "dépendances backend manquantes" pour le statut le plus à jour de chacun) mais ne sont pas réécrits ici pour garder ce fichier comme trace fidèle du message envoyé.
>
> ✅ **Les 6 points confirmés résolus/livrés par le backend le 2026-09-01** (rapport reçu le jour même, vérifié en direct par le backend contre le gateway réel — voir aussi `partenaire-documents.md` et la mémoire projet pour le détail à jour de chaque point) : DELETE contrats/cross-sell (non reproductible / soft-disable volontaire), SMS commission (en prod depuis fin août), renvoi d'accès SMS (`POST /users/{id}/resend-access`, en prod), `PUT /partenaires/{id}` 500→403 (corrigé), `typeProfileId` (en prod, sur `user.typeProfileId` au login), documents partenaire (3 endpoints, conformes au contrat). **Bonus livré la même livraison, hors périmètre de ce mail** : suppression de réclamation (`DELETE /reclamations/{id}`, rejetée en `409` si commentaire/pièce jointe/origine NPS auto), plus deux modules entièrement nouveaux non demandés ici — gestion de flotte auto/moto et import CSV/Excel de véhicules (voir contrats dédiés à rédiger si on donne suite).
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. `DELETE` qui répond `204` sans rien supprimer réellement — 3 occurrences du même symptôme

Confirmé sur trois modules différents :

- `DELETE /contracts/{id}` → `204`, mais le contrat reste visible dans `GET /contracts` juste après (ex. id `46`, toujours en base avec son écriture comptable liée, référence corrompue `"2,03E+13"`).
- `DELETE /cross-sell/regles/{id}` → `204`, mais `GET /cross-sell/regles` renvoie toujours l'enregistrement intact (ex. ids `3`/`4`, `TEST01`/`TEST01-COPIE`). **Nuance** : le résumé Swagger de cet endpoint dit maintenant explicitement "Désactiver une règle (réservé aux administrateurs/managers)" — ce comportement pourrait donc être un soft-disable volontaire pour ce cas précis, à confirmer.
- `/reclamations/{id}` : aucune méthode `DELETE` n'existe du tout (voir `reclamations-delete.md` pour la proposition d'endpoint). Un enregistrement de test `id 8` / `REC2026000143` reste à purger manuellement en base en attendant.

Trois modules, même symptôme exact → une cause commune est probable côté backend (intercepteur générique renvoyant `204` par défaut, ou handler `DELETE` non implémenté qui tombe sur un stub) plutôt que trois bugs isolés indépendants.

**Demande** : soit un vrai `DELETE` (physique ou soft-delete cohérent excluant l'enregistrement des listes), soit au minimum ne plus renvoyer `204` quand rien n'a changé. Si `cross-sell/regles` est un soft-disable volontaire, le confirmer explicitement pour qu'on le documente comme tel côté frontend.

## 2. SMS de confirmation au partenaire lors du paiement d'une commission

`POST /commissions/pay` n'a aucun moyen de notifier le partenaire que sa commission a été versée. Détail complet et proposition de contenu SMS dans `commissions-notification-sms.md` :
- Réutiliser le `telephone` du partenaire (`PartenaireResponse.telephone`).
- Un SMS agrégé par appel (pas un par contrat) — montant total, nombre de contrats, référence.
- Ajouter `notificationEnvoyee: boolean` sur `CommissionPaymentResponse`, même pattern déjà utilisé sur `UserProfileResponse`/`ReclamationResponse`/`SinistreSuiviResponse`.

## 3. Renvoi automatique des accès (SMS) à un partenaire déjà créé

Aucun endpoint ne permet de renvoyer les identifiants d'un compte existant — seule la création initiale déclenche un SMS. Un contournement manuel est en place côté front (régénération de mot de passe via `PATCH /users/{id}/reset-password`, l'admin transmet lui-même le mot de passe), mais ce n'est pas satisfaisant à terme. Deux options proposées dans `partenaire-renvoi-acces-sms.md` :

- **Option A** — étendre `PATCH /users/{id}/reset-password` avec un flag optionnel `notifierParSms: boolean`.
- **Option B** — nouvel endpoint dédié `POST /users/{id}/resend-access`.

Dans les deux cas : ajouter `notificationEnvoyee: boolean` en réponse, et ne pas renvoyer le mot de passe en clair au frontend puisqu'il partirait directement par SMS au titulaire.

## 4. `PUT /partenaires/{id}` renvoie `500` puis `403` lors de la conversion DIRECT → REPRESENTANT, alors que la conversion réussit réellement

Bug confirmé et caractérisé dans `partenaire-conversion-representant-500.md` : le profil et le compte de connexion sont bien créés en base malgré la réponse d'erreur — c'est la réponse HTTP qui ment, pas la donnée.

**Effet de bord sérieux** : le `403` du second essai fait perdre la session admin côté frontend (un `403` sur route authentifiée est volontairement traité comme session expirée, séquelle d'un incident antérieur où le backend renvoyait `403` au lieu de `401` pour un token rejeté) — avec un risque de soumission automatique d'un mot de passe obsolète par l'autofill du navigateur, consommant une tentative du quota anti-brute-force.

**Demande** :
1. Que `PUT` réponde `200` avec l'objet à jour quand la mutation réussit réellement — pas `500`.
2. Qu'un second appel identique après un premier succès soit idempotent (retourne l'état actuel sans dupliquer profil/compte) ou renvoie un `409 Conflict` explicite, plutôt qu'un `500`/`403`.
3. Vérifier les logs serveur autour de ces deux appels rapprochés (`PUT /partenaires/2`, 2026-08-25) pour isoler l'exception exacte — probablement dans la construction de la réponse après coup (sérialisation, ou un second traitement redondant qui échoue), pas dans la mutation elle-même qui s'exécute visiblement bien.

## 5. `typeProfileId` de l'utilisateur connecté toujours pas lisible

`User` (renvoyé par `POST /auth/login`) et `UserProfileResponse` (`GET /users`) n'exposent pas `typeProfileId`, alors que ce champ est bien écrit à la création/modification via `UserProfileRequest`/`UserProfileUpdateRequest`. Il n'existe même pas de `GET /users/{id}` pour aller le relire a posteriori (seuls `PUT`/`DELETE` existent à ce chemin).

Détail et proposition de schéma dans `user-type-profile-id.md` : ajouter `typeProfileId?: number` directement sur `User`, pour piloter côté frontend la restriction des menus Paramétrage/Administration selon le Type Profil — notamment `CO-FOUNDER` (id `2`), qui doit avoir un accès restreint contrairement à `ADMIN`/`REPRESENTANT`.

Le frontend est déjà prêt à consommer ce champ dès qu'il apparaît (`AuthUser.typeProfileId`, `core/auth/nav-permissions.service.ts`) — aucun développement supplémentaire nécessaire côté front à ce moment-là.

## 6. Documents joints à un partenaire (contrat d'apporteur, CNI, ...) — nouvel endpoint à ajouter

Demande explicite de l'utilisateur (2026-09-01) : pouvoir joindre des documents administratifs à la fiche d'un partenaire (contrat d'apporteur, copie CNI, RIB, justificatif de domicile...) et les reconsulter à tout moment après coup. Vérifié en direct sur `/v3/api-docs` (279 endpoints au total) : **aucun endpoint de ce type n'existe aujourd'hui** sous `/partenaires/{id}/...`.

Détail complet dans `partenaire-documents.md`. Reprend exactement le même schéma que deux mécanismes déjà en place et fonctionnels (`/sinistres/{id}/photos`, `/reclamations/{id}/pieces-jointes`) — rien de nouveau à valider côté conception :

```
GET  /partenaires/{id}/documents              → liste des documents joints
POST /partenaires/{id}/documents              → upload d'un document (multipart : "file" + "type")
GET  /partenaires/{id}/documents/{documentId} → téléchargement du fichier (blob)
```

Catégories proposées pour `type` (string, extensible) : `CONTRAT_APPORTEUR`, `CNI`, `RIB`, `JUSTIFICATIF_DOMICILE`, `AUTRE`.

Réponse (`PartenaireDocumentResponse`), même forme sur les 3 endpoints :
```json
{
  "id": 12,
  "partenaireId": 42,
  "type": "CNI",
  "fileName": "cni_kouassi.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 184320,
  "uploadedBy": "Administrateur AssurZen Core",
  "createdAt": "2026-09-01T10:15:00Z"
}
```

Pas de `DELETE` dans cette première version (cohérent avec les deux mécanismes cités en référence, à ajouter plus tard si besoin confirmé). Frontend déjà prêt à consommer ces 3 endpoints dès leur livraison.

## 7. `DELETE /contracts/{id}` toujours défaillant — nouvelle preuve, 2026-09-02 (renforce le point 1)

Reproduit à nouveau en direct : `DELETE /contracts/64` et `DELETE /contracts/65` → `500` (pas même le `204` menteur observé précédemment sur l'id `46` — une variante du même symptôme, ou une régression). Les deux contrats restent visibles dans `GET /contracts/{id}` juste après.

Ces deux contrats sont des enregistrements de test créés le jour même pour diagnostiquer un bug frontend (mauvais champ envoyé sur `salesRepId` — **déjà corrigé côté frontend**, aucune action backend nécessaire sur ce point précis) et n'ont pas pu être purgés en base. Ajoutés à la liste des enregistrements de test en attente de purge manuelle, aux côtés de `REC2026000143` (point 1).

**Demande** : reprend celle du point 1 — un vrai `DELETE` fonctionnel sur `/contracts/{id}`, ou au minimum un code d'erreur qui reflète l'échec réel plutôt qu'un `500` opaque.

---

## Réponse attendue

Merci de préciser, pour chaque point, s'il est pris en compte et sous quel délai estimé, afin de prioriser les chantiers frontend en conséquence.

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes". Détail complet de chaque point dans son fichier dédié (`reclamations-delete.md`, `commissions-notification-sms.md`, `partenaire-renvoi-acces-sms.md`, `partenaire-conversion-representant-500.md`, `user-type-profile-id.md`, `partenaire-documents.md`).
