# Rapport — L'action DISBURSE n'apparaît jamais dans l'historique d'une sortie de fonds

> 🐞 Bug confirmé en direct le 2026-09-03, en testant de bout en bout le nouveau module Sorties de fonds & Prêts.
>
> Suivi dans la mémoire projet "dépendances backend manquantes" (entrée 40).

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Ce qui est observé

`GET /fund-outflows/{id}/history` journalise correctement les transitions `CREATE`, `SUBMIT`, `REVIEW`, `APPROVE`, `AUTHORIZE`, `CANCEL` et `REJECT` — mais **jamais `DISBURSE`**, alors même que le décaissement a bien eu lieu (statut, `disbursedAt`, `disbursedBy` et `loanId` tous correctement renseignés sur la ressource elle-même).

## 2. Scénario de test (reproductible)

1. Sortie de fonds de type `LOAN` créée puis menée jusqu'au bout du workflow : `CREATE → SUBMIT → REVIEW → APPROVE → AUTHORIZE → DISBURSE` (id `3`, référence `DEC2026000504`).
2. `GET /fund-outflows/3` juste après le décaissement → confirme `status: "DISBURSED"`, `disbursedAt: "2026-09-03T19:28:31Z"`, `disbursedBy: "0757873487"`, `loanId: 2` (le prêt a bien été auto-créé).
3. `GET /fund-outflows/3/history` → renvoie `totalElements: 5`, avec uniquement `AUTHORIZE` (19:28:05), `APPROVE` (19:27:51), `REVIEW` (19:27:40), `SUBMIT` (19:27:28), `CREATE` (19:26:58). Aucune entrée `DISBURSE`, alors qu'elle devrait apparaître après `AUTHORIZE` (le décaissement a eu lieu à 19:28:31, soit 26 secondes plus tard).
4. Contre-exemple confirmant que les autres transitions fonctionnent bien : sur une autre sortie (id `4`), `CREATE` puis `CANCEL` apparaissent tous les deux correctement dans `/fund-outflows/4/history` (`totalElements: 2`).

## 3. Hypothèse

L'appel au service d'audit manque probablement dans le contrôleur/service qui traite `POST /fund-outflows/{id}/disburse` — les 6 autres actions de transition (y compris `CANCEL`, `REJECT`) semblent toutes appeler ce service correctement, seul `disburse()` en est apparemment dépourvu.

## 4. Impact

Non bloquant pour l'usage courant — le statut `DISBURSED` et les champs `disbursedAt`/`disbursedBy` restent visibles et fiables sur la fiche de la sortie de fonds elle-même. Mais l'historique/piste d'audit de cette action précise (qui a décaissé, à quelle heure exacte, dans le fil chronologique complet) est actuellement invisible, ce qui est gênant pour une action financière aussi sensible que le décaissement de fonds.

## 5. Demande

Merci de vérifier si l'appel au service d'audit a bien été ajouté dans le traitement de `POST /fund-outflows/{id}/disburse`, au même titre que pour les autres transitions du workflow.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
