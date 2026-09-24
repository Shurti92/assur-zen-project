# Rapport — 409 CONCURRENT_MODIFICATION reproductible sur /commercial-performance/dashboard

> 🐞 Bug confirmé en direct le 2026-09-07, en testant de bout en bout le nouveau module Commercial
> (force de vente).
>
> Suivi dans la mémoire projet "dépendances backend manquantes" (entrée 46).

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Ce qui est observé

`GET /commercial-performance/dashboard?moisComptableId=X` renvoie de façon **reproductible à 100%**
un `409` quand il est appelé **en parallèle** de `GET /commercial-performance/ranking?moisComptableId=X`
(même mois comptable) :

```json
{
  "code": "CONCURRENT_MODIFICATION",
  "message": "Cette ressource a ete modifiee entre-temps, veuillez recharger et reessayer"
}
```

`ranking` réussit toujours (`200`) dans ce même scénario — seul `dashboard` échoue.

Ce sont deux endpoints `GET`, documentés comme de la lecture pure dans le contrat de départ
(`docs/api-contracts/module-commercial.md`, tableau "dashboard management global" /
"classement"). Un client qui affiche les deux widgets sur un même écran (cas d'usage naturel : un
tableau de bord équipe avec son classement juste en dessous) les appellera quasi systématiquement
en parallèle, donc échouera quasi systématiquement sur cette page tant que le contournement
frontend décrit plus bas reste en place.

## 2. Scénario de test (reproductible)

Testé directement dans la console du navigateur (`fetch()`, donc hors HttpClient Angular — élimine
toute cause côté frontend) :

```js
const [dash, rank] = await Promise.all([
  fetch('/api-assur-zen/commercial-performance/dashboard?moisComptableId=9', { headers: authHeader }),
  fetch('/api-assur-zen/commercial-performance/ranking?moisComptableId=9', { headers: authHeader }),
]);
// dash.status === 409, rank.status === 200
```

- **En parallèle (`Promise.all`)** : reproduit **3/3** fois — `dashboard` toujours `409`, `ranking`
  toujours `200`.
- **En séquentiel** (`dashboard` attendu, puis `ranking` lancé après) : **4/4** fois, les deux
  renvoient `200`.
- `dashboard` appelé seul, plusieurs fois de suite (sans aucune concurrence) : toujours `200`, y
  compris juste après un appel qui avait précédemment échoué en `409` — donc pas un problème de
  données corrompues, uniquement de concurrence entre les deux endpoints.

Environnement du test : 1 commercial (`COM2026000616`), `moisComptableId=9` (Septembre 2026), objectif
mensuel de 650 000 FCFA défini juste avant le test.

## 3. Hypothèse

Les deux endpoints semblent écrire sur une même ressource sous-jacente (verrou optimiste — probablement
une matérialisation/mise à jour paresseuse de l'agrégat `CommercialPerformance` du mois, déclenchée à
la lecture) au lieu d'être de purs `GET` en lecture seule. Deux requêtes simultanées sur le même mois
se percutent alors sur ce verrou, et l'une des deux (systématiquement `dashboard` dans nos tests)
perd la course.

## 4. Impact

Bloquant pour l'usage normal d'un tableau de bord qui affiche KPI équipe + classement sur le même
écran : le premier chargement échoue de façon quasi certaine si les deux appels partent ensemble.

**Contournement frontend déjà appliqué** (`commercial.component.ts`, `fetchDashboard()`) : les deux
appels sont maintenant enchaînés (`ranking` n'est lancé qu'après la réponse — succès ou échec — de
`dashboard`) plutôt que lancés en parallèle. Ça fonctionne, mais ajoute une latence perceptible (deux
allers-retours réseau au lieu d'un) et n'est qu'un contournement — tout autre client (l'appli
"force de vente" par exemple, si son propre dashboard croise un jour ces deux endpoints) est
exposé au même problème.

## 5. Demande

Merci de vérifier pourquoi `GET /commercial-performance/dashboard` déclenche une écriture avec verrou
optimiste, et si possible de le rendre véritablement idempotent en lecture (ou, à défaut, de retenter
automatiquement en interne en cas de conflit sur la matérialisation de l'agrégat plutôt que de
remonter le `409` au client).

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
