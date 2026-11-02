# Rattachement d'un contrat mono existant à une flotte (2026-09-14)

## Contexte

Question remontée par le frontend (assur-zen-core) : jusqu'ici le seul chemin qui liait un
contrat à une flotte était la création d'un **nouveau** contrat directement depuis l'onglet
Véhicules d'une flotte (`POST /contracts` avec `vehiculeId` renseigné, lui-même issu d'un
véhicule déjà membre de la flotte). Il n'existait aucun moyen de prendre un contrat mono
**existant** (souscrit hors flotte, sans `vehiculeId`) et de le rattacher a posteriori à une
flotte.

Un chemin détourné existait déjà via `PUT /contracts/{id}` (accepte `vehiculeId` sans aucun
contrôle), mais sans garde-fou : pas de vérification qu'un véhicule n'est pas déjà lié à un
autre contrat actif, pas de gestion de l'appartenance à la flotte (deux FK indépendantes),
et un remplacement complet du contrat (tous les champs `@NotNull` à renvoyer). Décision produit :
construire un endpoint dédié plutôt que documenter ce chemin tel quel.

## Nouvel endpoint

```
POST /fleets/{fleetId}/contracts/attach
```
Réservé aux administrateurs (même convention que les autres mutations de véhicule de flotte).

**Body** (`AttachContractRequest`) :
```json
{
  "contractId": 123,
  "vehiculeId": 45,
  "reason": "Rattachement flotte suite migration"
}
```
`reason` est optionnel, transmis tel quel au mouvement `ADD` si le véhicule doit être ajouté à
la flotte (mêmes conventions que `POST /fleets/{fleetId}/vehicles`).

**Réponse** : `FleetContractResponse` (même forme que les entrées de `GET /fleets/{fleetId}/contracts`).

## Comportement

1. Le contrat ne doit **pas** déjà avoir de véhicule (`409` sinon — remplacer un véhicule déjà
   rattaché n'est pas ce flux, c'est un cas distinct non couvert ici).
2. Le véhicule ne doit **pas** déjà être rattaché à un autre contrat non annulé (`409` sinon).
3. Le propriétaire du véhicule doit correspondre à l'assuré du contrat (`409` sinon).
4. Deux cas selon l'état actuel du véhicule vis-à-vis des flottes :
   - **Le véhicule n'est membre d'aucune flotte** : il est ajouté à la flotte `{fleetId}` dans
     la même opération (réutilise `ajouterVehiculeAuCoeur`, donc les mêmes garde-fous que
     `POST /fleets/{fleetId}/vehicles` : flotte active, véhicule `ACTIF`, cohérence
     `FleetType`/`VehiculeType`, compteurs recalculés, mouvement `ADD` enregistré, SMS
     d'incorporation envoyé si un téléphone gestionnaire/client est disponible).
   - **Le véhicule est déjà membre actif de la flotte `{fleetId}`** : rien à faire côté flotte,
     le contrat est simplement lié.
   - **Le véhicule est actif dans une AUTRE flotte** : rejeté (`409`) — utiliser
     `POST /fleets/{sourceFleetId}/vehicles/{vehicleId}/transfer` d'abord si un transfert est
     voulu.
5. `contract.vehiculeId` est mis à jour ; le contrat apparaît alors dans
   `GET /fleets/{fleetId}/contracts` et `GET /fleets/{fleetId}/echeances` comme n'importe quel
   contrat créé directement depuis la flotte (les deux endpoints joignent uniquement sur
   `Vehicule.id`, sans distinction d'origine).
6. Fonctionne même si le contrat est `signed` (le verrou "champs sensibles" de
   `PUT /contracts/{id}` ne concerne pas `vehiculeId`, et n'est de toute façon pas ce chemin ici).

## Hors scope (volontairement)

- Remplacer le véhicule d'un contrat déjà rattaché à un autre véhicule.
- Cohérence type produit ↔ type véhicule : `Produit` n'a pas de notion AUTO/MOTO dans ce
  schéma (seul `Vehicule.type`/`Fleet.fleetType` en ont une), donc aucune règle à vérifier ici.

## Vérification

377+ tests `assur-zen-jpa` verts (compile + suite complète), 9 nouveaux tests dédiés dans
`FleetServiceTest` (contrat introuvable, contrat déjà rattaché, propriétaire incohérent,
véhicule déjà lié à un contrat actif, contrat annulé ignoré, véhicule déjà membre de la flotte,
véhicule dans une autre flotte, ajout+liaison en une opération).

E2E live confirmé (gateway local + `assur_zen_ci_db_test`, compte admin sans 2FA) : les 5
scénarios ci-dessus (ajout+liaison en une opération, contrat déjà rattaché, véhicule déjà lié à
un autre contrat, véhicule actif dans une autre flotte, véhicule déjà membre de la flotte cible)
tous confirmés avec de vraies requêtes HTTP, compteurs de flotte recalculés (1→1, 2→2),
mouvement `ADD` enregistré avec le motif fourni, aucun mouvement superflu créé quand le véhicule
était déjà membre, et entrée d'audit fonctionnel confirmée via `GET /audit-logs?module=FLOTTE`.
