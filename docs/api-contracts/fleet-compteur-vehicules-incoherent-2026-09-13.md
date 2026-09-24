# Rapport — Compteur `activeVehicles`/`totalVehicles` d'une flotte incohérent avec les véhicules réellement rattachés

> 🐞 Bug confirmé en direct le 2026-09-13, en testant la refonte de la fiche détail flotte et la nouvelle livraison "moto sans plaque".
>
> Suivi dans la mémoire projet "dépendances backend manquantes" (entrée du 2026-09-13).

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Ce qui est observé

Sur une même flotte, trois sources de vérité se contredisent :

1. `GET /fleets` (et `GET /fleets/{id}`) — champs dénormalisés `activeVehicles`/`totalVehicles` de `FleetResponse`.
2. `GET /fleets/{id}/vehicles` — liste des rattachements réels (table de jonction / mouvements).
3. `Vehicule.currentFleetId` (visible via `GET /vehicules`) — pointeur porté par le véhicule lui-même.

Ces trois sources ne racontent pas la même histoire, et le compteur (1) ne se met à jour dans aucun des deux scénarios testés ci-dessous.

## 2. Scénario de test (reproductible)

Flotte de test : `FLT2026000707` ("Flotte Motos E2E", type `MOTO`, statut `ACTIVE`).

**État initial (avant toute action de cette session) :**
- `GET /fleets` → `activeVehicles: 1, totalVehicles: 1` pour cette flotte.
- `GET /fleets/{id}/vehicles` → **liste vide** (`content: []`, `totalElements: 0`).
- `GET /vehicules` → le véhicule `Yamaha Crypton` (moto, châssis `E2E-MOTO-CH-001`, sans immatriculation) a `currentFleetId` pointant vers cette flotte.

Donc dès le départ : le compteur dit "1", `/vehicles` dit "0", et le registre véhicules dit "1" (mais un véhicule différent de ce que `/vehicles` pourrait montrer s'il n'était pas vide).

**Action réalisée** : ajout d'un second véhicule moto (`Kawasaki Ninja`, châssis `E2E-MOTO-CH-999`, sans immatriculation non plus — cas volontairement testé pour la nouvelle fonctionnalité "moto sans plaque") via le flux normal de l'UI, qui appelle `POST /fleets/{id}/vehicles`. Réponse `2xx`, aucune erreur.

**État après ajout :**
- `GET /fleets/{id}/vehicles` → **une seule ligne** apparaît, celle du véhicule qui vient d'être ajouté (`E2E-MOTO-CH-999`). Toujours aucune trace de `E2E-MOTO-CH-001`.
- `GET /vehicules` → **les deux véhicules** (`E2E-MOTO-CH-001` et `E2E-MOTO-CH-999`) ont désormais `currentFleetId` pointant vers `FLT2026000707`.
- `GET /fleets` → `activeVehicles: 1, totalVehicles: 1` — **inchangé**, alors qu'un rattachement réel venait d'avoir lieu avec succès.

## 3. Hypothèses

- **Le compteur `activeVehicles`/`totalVehicles` de `Fleet` n'est pas recalculé par `POST /fleets/{id}/vehicles`.** Sa valeur ("1") ne correspond ni au nombre de lignes dans la table de jonction (0 puis 1) ni au nombre de véhicules dont `currentFleetId` pointe vers cette flotte (1 puis 2) — elle semble figée depuis un état antérieur (création de la flotte ou import initial), sans être recalculée par aucune des deux actions testées.
- **`E2E-MOTO-CH-001` a son `currentFleetId` positionné sur cette flotte sans ligne correspondante dans `/fleets/{id}/vehicles`** — probablement une donnée de test injectée directement en base (hors du flux `POST /fleets/{id}/vehicles`) plutôt qu'un bug généralisé de cet endpoint ; à confirmer côté backend. Si ce véhicule a bien été rattaché via l'API normale à un moment donné, alors l'endpoint d'ajout ne crée pas systématiquement la ligne de jonction, ce qui serait plus grave.

## 4. Impact

- La fiche détail d'une flotte (nouvellement refondue) affiche un total de véhicules qui ne correspond pas à ce que l'onglet "Véhicules" liste réellement juste en dessous — source de confusion immédiate pour un utilisateur qui compare les deux à l'écran.
- Impossible de se fier à `activeVehicles`/`totalVehicles` pour un indicateur (ex. barre d'occupation) tant que ce n'est pas corrigé : ce champ peut sous-compter les véhicules réellement actifs.
- Risque plus large sur `PATCH /fleets/{id}/activate` et toute règle métier qui s'appuierait sur ce compteur pour décider si une flotte "a des véhicules" ou non.

## 5. Demande

1. Confirmer si `Fleet.activeVehicles`/`totalVehicles` sont censés être recalculés à chaque `POST /fleets/{id}/vehicles` (ajout), `.../remove`, `.../replace` — et corriger si ce n'est pas le cas.
2. Vérifier l'intégrité des données existantes : au moins un véhicule (`E2E-MOTO-CH-001` sur `FLT2026000707`) a `currentFleetId` renseigné sans ligne correspondante dans `/fleets/{id}/vehicles` — clarifier si c'est un résidu de données de test à ignorer, ou un vrai chemin de code (import CSV ancien ? affectation directe ?) qui contourne la création de la ligne de jonction.
3. Préciser la source de vérité voulue à terme (compteur dénormalisé vs. calcul à la volée depuis `/fleets/{id}/vehicles` vs. depuis `Vehicule.currentFleetId`) pour que le frontend sache laquelle afficher sans risque de divergence future.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
