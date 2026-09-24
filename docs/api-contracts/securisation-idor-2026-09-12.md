# Durcissement sécurité backend — impact sur le frontend (2026-09-12)

> ⚠️ **Changement de comportement, pas un bug** : suite à un audit de sécurité, plusieurs endpoints qui étaient ouverts à tout compte authentifié sont maintenant restreints. Certains écrans vont recevoir des `403`/`404` qu'ils ne recevaient pas avant. Ce document liste précisément quoi, pour qui, et ce qu'il faut vérifier côté écrans.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Assurés (`/insureds/**`) — changement le plus fréquent, à vérifier en priorité

**Avant** : n'importe quel compte connecté voyait/modifiait n'importe quel assuré par ID (faille de sécurité réelle, corrigée).

**Maintenant** : un compte non-admin (`COMMERCIAL`, `USER`) ne voit/modifie/supprime que les assurés liés à **ses propres contrats** (via `salesRepId`).

- `GET /insureds` (liste) : renvoie maintenant **moins de résultats** pour un non-admin — seulement ses propres clients, pas tout le portefeuille. `totalElements` va baisser en conséquence.
- `GET /insureds/{id}`, `PUT /insureds/{id}`, `DELETE /insureds/{id}` : **`404`** (pas `403`) si l'assuré ne lui appartient pas — volontaire, pour ne pas confirmer qu'un ID existe à quelqu'un qui n'y a pas droit.
- Pour un compte **ADMIN**, rien ne change.

**À vérifier côté écrans** : tout écran "fiche client" ou "liste clients" accessible à un commercial/partenaire non-admin doit gérer un `404` sur un ID qu'il ne peut plus voir (au lieu de la fiche complète d'avant), et une liste plus courte.

## 2. Sorties de fonds / prêts (`/fund-outflows/**`, `/loans/**`)

**Avant** : lecture ouverte à tout compte authentifié.
**Maintenant** : **`GET` réservé ADMIN**, `403` pour tout le reste. Les mutations (`POST`/`PUT`/`submit`/`approve`/etc.) l'étaient déjà.

**À vérifier** : si un écran non-admin affiche quoi que ce soit lié aux sorties de fonds/prêts, il va recevoir `403` sur tout. Aucun self-service partenaire n'existait pour ça à notre connaissance — à confirmer de votre côté si un écran y accède déjà.

## 3. Demandes de vignette moto (`/vignettes-moto/demandes/**`, `/vignettes-moto/tarification/**`) — MISE À JOUR, voir tout en bas

~~**Le plus gros changement** : ce module (livré le 2026-09-11) était conçu au départ ouvert à tout compte authentifié pour créer/soumettre ses propres demandes. **C'est maintenant entièrement réservé ADMIN** — création, consultation, documents, soumission, annulation, rejet, simulation de tarif comprises.~~

**Annulé, voir la section "Correction du 2026-09-12" en bas de ce document.** On avait supposé à tort que c'était un flux backoffice — vous nous avez confirmé que c'est un vrai self-service déjà livré. Le comportement final (owner-scoping, pas ADMIN-only) est décrit en bas.

## 4. Documents GED (`/ged/documents/**`, y compris téléchargement)

**Avant** : lecture/téléchargement ouverts à tout compte authentifié, sans vérification du lien avec le dossier concerné.
**Maintenant** : **réservé ADMIN**.

**À vérifier** : tout composant de visualisation/téléchargement de document accessible à un non-admin va recevoir `403`.

## 5. Ce qui ne change pas

Contrats (`/contracts/**`) — déjà correctement scopés par propriétaire depuis longtemps, aucun changement. Dashboards KPI — déjà ADMIN-only depuis leur livraison, aucun changement.

---

## Rappel

Ces changements corrigent des failles de sécurité réelles (IDOR) trouvées lors d'un audit — pas des choix arbitraires. Si un de ces points bloque un écran déjà livré, prévenez-nous : soit on ajuste le périmètre (ex. donner l'accès au créateur plutôt qu'à ADMIN seul), soit l'écran doit être retiré pour les comptes non-admin.

---

## Correction du 2026-09-12 (même jour) — Vignette Moto revient en self-service par créateur

Suite à votre retour confirmant que Vignette Moto (livré le 2026-09-11) est un vrai flux self-service déjà en prod, pas un flux backoffice : le blocage ADMIN-only du point 3 ci-dessus est **annulé**, remplacé par le même principe d'"owner scoping" que les Assurés (point 1).

**Comportement final pour `/vignettes-moto/demandes/**`** :
- Un compte non-admin peut créer, consulter, soumettre, annuler et rejeter **ses propres** demandes (celles qu'il a créées) — tout fonctionne normalement, comme avant l'audit.
- `GET /vignettes-moto/demandes` (liste) : un non-admin ne voit que ses propres demandes.
- `GET /vignettes-moto/demandes/{id}` et toutes les actions sur `{id}` (soumettre/annuler/rejeter/documents) : **`404`** (pas `403`) si la demande appartient à quelqu'un d'autre.
- Un compte **ADMIN** garde un accès complet à toutes les demandes, comme avant.
- `POST /vignettes-moto/tarification/simuler` reste ouvert à tout compte authentifié (aucun changement, ce n'était pas concerné par l'audit).

**Ce qui reste ADMIN-only, inchangé** : `/vignettes-moto/tarifs` et `/vignettes-moto/livraisons` (paramétrage tarifaire, écrans backoffice).

**Nouveau point : téléchargement de documents joints à une demande.** Le `GET /ged/documents/{id}/download` générique reste ADMIN-only (comme le reste du module GED, point 4 ci-dessus) — ce n'est pas rouvert. À la place, un nouvel endpoint scopé a été ajouté, à utiliser depuis la fiche de demande :

```
GET /vignettes-moto/demandes/{id}/documents/{documentId}/download
```

Accessible au créateur de la demande `{id}` (ou à un admin) uniquement, et vérifie en plus que `documentId` est bien un document lié à **cette** demande précise (sinon `404`, même si le document existe par ailleurs). C'est cet endpoint qu'il faut utiliser pour le visualiseur/téléchargement de documents dans l'écran de suivi de demande, pas le générique GED.

**Réponse à votre question sur le masquage de l'écran** : plus la peine de le masquer — dès que ce correctif est déployé, le comportement redevient celui d'avant l'audit pour les comptes non-admin (plus de 403 imprévu), avec en plus le nouvel endpoint de téléchargement scopé à brancher pour les documents. Dites-nous si l'écran de suivi utilisait déjà `GET /ged/documents/{id}/download` directement — il faudra basculer vers le nouvel endpoint.

Point ouvert de notre côté, à confirmer si ça vous semble faux : le rejet (`PATCH /vignettes-moto/demandes/{id}/rejeter`) a été laissé accessible au créateur lui-même (comme annuler), pas réservé à un rôle "réviseur". Si côté métier rejeter sa propre demande n'a pas de sens, dites-le-nous et on le restreindra différemment.

---

## Réponse frontend (2026-09-12) — rejeter sa propre demande n'a pas de sens, à restreindre

Confirmé côté métier : un créateur ne doit pas pouvoir rejeter sa propre demande. Merci de restreindre `PATCH /vignettes-moto/demandes/{id}/rejeter` à un rôle ADMIN (ou "réviseur" si ce rôle existe déjà côté backend) — pas au créateur, contrairement à l'annulation (`annuler`) qui reste correcte telle quelle pour le créateur.

En attendant votre déploiement, le bouton "Rejeter" est déjà masqué côté frontend pour tout compte non-admin (masquage d'affichage uniquement, comme le reste — le vrai rempart sera votre 403/404 une fois la restriction en place).
