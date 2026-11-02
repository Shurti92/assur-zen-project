# Enregistrement de moto en flotte + corrections associées (2026-09-13)

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. `POST /vehicules` / `PUT /vehicules/{id}` — nouveaux champs, immatriculation optionnelle

`VehiculeRequest` change :

- **`immatriculation` devient optionnelle** (était obligatoire). Une moto neuve ou en attente de plaque officielle peut maintenant être enregistrée sans, en s'appuyant sur `numeroChassis` comme identifiant. Valeur `null` en réponse si absente (jamais une chaîne vide).
- **Nouveau champ `cylindree`** (entier, optionnel) — pertinent pour les motos, mais pas techniquement restreint à `type: "MOTO"` au niveau backend.

```json
{
  "immatriculation": null,
  "type": "MOTO",
  "marque": "Yamaha",
  "modele": "Crypton",
  "numeroChassis": "CH-2026-001",
  "dateMiseEnCirculation": "2024-01-15",
  "cylindree": 110,
  "couleur": null,
  "proprietaireId": 3,
  "statut": "ACTIF"
}
```

`VehiculeResponse` renvoie désormais aussi `cylindree`.

**À vérifier côté écran** : le formulaire de création véhicule/moto doit accepter une immatriculation vide (ne plus la rendre obligatoire côté validation front), et exposer le champ cylindrée (au moins pour les motos).

> ✅ **Migration SQL prod confirmée exécutée le 2026-09-13** (`ALTER TABLE t_assur_zen_vehicules MODIFY COLUMN immatriculation VARCHAR(20) NULL;`) — vérifié en direct : création d'une moto sans immatriculation → `201` en prod. L'immatriculation optionnelle fonctionne maintenant en recette **et** en prod.

## 2. `POST /fleets/{fleetId}/vehicles` — cohérence type flotte / type véhicule (nouveau contrôle)

Une **flotte 100% moto** (`fleetType: "MOTO"`) était déjà créable, mais rien n'empêchait techniquement d'y incorporer un véhicule `AUTO` (et inversement). C'est maintenant bloqué :

- `fleetType: "AUTO"` → accepte uniquement `vehiculeType: "AUTO"`
- `fleetType: "MOTO"` → accepte uniquement `vehiculeType: "MOTO"`
- `fleetType: "MIXED"` → accepte les deux

Sinon : `409 CONFLICT`, message `"Une flotte {TYPE} n'accepte pas un vehicule de type {AUTO|MOTO}"`.

**Même règle sur le transfert** : `POST /fleets/{sourceFleetId}/vehicles/{vehicleId}/transfer` applique le même contrôle sur la flotte de **destination** (trouvé en bug le jour même — le transfert bypassait initialement la règle — corrigé et testé en direct dans les deux sens avant la livraison).

**Rappel (déjà existant, pas nouveau)** : chaque ajout envoie déjà un SMS au gestionnaire de la flotte (`Fleet.managerPhone`, ou au client à défaut) — `notificationEnvoyee` dans la réponse indique si l'envoi a été tenté.

## 3. Import CSV/XLSX en masse — mêmes changements

Template (`GET /fleets/{fleetId}/vehicles/import/template`) mis à jour :
- Colonne `immatriculation` devient optionnelle dans le fichier (cellule vide acceptée, plusieurs lignes sans immatriculation dans le même fichier ne se comptent plus comme doublons entre elles).
- Nouvelle colonne optionnelle `cylindree`.

Colonnes du template, dans l'ordre : `immatriculation, type, marque, modele, numero_chassis, date_mise_en_circulation, puissance_fiscale, cylindree, couleur, statut`.

## 4. `POST /contracts` — le cycle de rappel SMS d'échéance se déclenche maintenant pour TOUS les contrats

**Bug corrigé, pas une nouvelle fonctionnalité côté contrat lui-même** : jusqu'ici, les rappels SMS avant échéance (avis à 45/15/5 jours, SMS de fin de contrat) n'étaient déclenchés que par le flux `POST /souscriptions` (création d'un nouvel assuré + contrat en même temps). Un contrat créé via `POST /contracts` — le cas le plus courant pour rattacher un contrat à un client déjà existant, notamment un véhicule/moto de flotte — ne recevait **aucun** rappel avant cette correction.

**Impact visible** : des SMS d'échéance vont maintenant partir pour des contrats qui n'en recevaient pas avant, dès qu'ils ont une `dateEcheance` et un téléphone client renseigné. Rien à changer côté écran (aucun champ de requête/réponse modifié), mais bon à savoir si un client signale recevoir un SMS de rappel qu'il n'attendait pas — c'est le comportement corrigé, pas une anomalie.

**Ce qui n'est pas concerné** : le SMS d'incorporation en flotte (point 2 ci-dessus, déjà existant) et le cycle de relance de paiement fractionné (`relance_echeance_paiement`, déjà en place pour les contrats en mode `FRACTIONNE`).

---

## 5. SMS d'incorporation unitaire enrichi (type + marque/modèle + n° de police)

`POST /fleets/{fleetId}/vehicles` (ajout unitaire uniquement, pas l'import en masse) envoie désormais un
SMS plus détaillé au gestionnaire (template `VEHICULE_FLOTTE_DETAIL`, remplace `VEHICULE_INCORPORE_FLOTTE`
qui reste utilisé pour l'import en masse) :

```
AssurZen : moto Yamaha Crypton incorpore a la flotte Flotte Motos E2E (FLT2026000707).
AssurZen : moto Kawasaki Ninja incorpore a la flotte Flotte Motos E2E (FLT2026000707) - police CTR2026000747.
```

Le n° de police n'apparaît que si un contrat existe déjà pour ce véhicule au moment de l'incorporation
(lecture simple, aucun contrat n'est créé ni requis à l'incorporation elle-même).

## 6. Nouvel endpoint : avis d'échéance par flotte

`GET /fleets/{fleetId}/echeances` — liste les contrats (et leurs SMS d'échéance programmés/envoyés) des
véhicules/motos **actuellement** incorporés dans la flotte (pas l'historique des véhicules retirés/transférés,
contrairement à `GET /fleets/{fleetId}/contracts`).

```json
[
  {
    "contractId": 69, "policyNumber": "CTR2026000743", "status": "signed",
    "vehiculeId": 20, "immatriculation": null, "vehiculeMarque": "Yamaha", "vehiculeModele": "Crypton", "vehiculeType": "MOTO",
    "dateEffet": "2026-09-13", "dateEcheance": "2027-09-13",
    "rappels": [
      { "typeMessage": "avis_echeance", "dateEnvoi": "2027-07-30", "statutEnvoi": "en_attente", "message": "..." },
      { "typeMessage": "avis_echeance", "dateEnvoi": "2027-08-29", "statutEnvoi": "en_attente", "message": "..." },
      { "typeMessage": "avis_echeance", "dateEnvoi": "2027-09-08", "statutEnvoi": "en_attente", "message": "..." },
      { "typeMessage": "fin_contrat", "dateEnvoi": "2027-09-13", "statutEnvoi": "en_attente", "message": "..." }
    ]
  }
]
```

Un véhicule/moto incorporé mais sans contrat (encore) créé n'apparaît simplement pas dans cette liste.

> ✅ **Déployé en prod le 2026-09-13.** Découverte au passage : la catégorie `FLOTTE` et le template
> `VEHICULE_INCORPORE_FLOTTE` original **n'existaient pas non plus en prod** (`data.sql` ne tourne jamais
> en prod, `sql.init.mode: never`) — le SMS d'incorporation flotte n'a donc probablement jamais fonctionné
> en prod depuis sa livraison le 2026-09-08 (échec silencieux, `sendFromTemplate` renvoie juste `false`).
> Catégorie + les deux templates (`VEHICULE_INCORPORE_FLOTTE` et `VEHICULE_FLOTTE_DETAIL`) créés et activés
> en prod via `POST /template-categories` puis `POST /templates` + `PATCH /templates/{id}/enable`.

## 7. Deux bugs de crash corrigés (trouvés en testant tout le cycle de vie flotte en direct)

- **Import en masse en 500 si la flotte cible n'avait pas de `managerPhone`** (`POST /fleets/{fleetId}/vehicles/import/{importId}/confirm`) — bug technique côté session base de données, préexistant depuis la livraison initiale du module (pas lié aux changements du jour). Corrigé : l'import fonctionne normalement avec ou sans `managerPhone` renseigné sur la flotte.
- **`GET /fleets/{fleetId}/vehicles` et `GET /fleets/{fleetId}/movements` en 500 si un véhicule référencé dans l'historique de la flotte avait depuis été supprimé** (`DELETE /vehicules/{id}`, possible une fois le véhicule retiré de toute flotte active). Corrigé : la ligne concernée s'affiche désormais avec `vehiculeId`, `immatriculation` et `vehiculeType` à `null` au lieu de faire planter tout l'appel — à afficher côté écran comme "Véhicule supprimé" plutôt qu'un champ vide (déjà fait côté frontend, vérifié en direct sur `FLT2026000722`).

## Résumé pour la flotte 100% moto

Le workflow complet est maintenant : `POST /vehicules` (type MOTO, immatriculation optionnelle, cylindrée) → `POST /fleets` (fleetType MOTO) → `POST /fleets/{id}/vehicles` (SMS au gestionnaire, rejet si type incohérent) → `POST /contracts` (dateEffet/dateEcheance propres à ce véhicule, cycle SMS d'échéance automatique). Testé de bout en bout en direct le 2026-09-13.
