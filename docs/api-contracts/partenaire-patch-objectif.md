# Contrat d'interface — Modifier uniquement l'objectif de production d'un partenaire

> 💡 **Amélioration d'ergonomie demandée le 2026-08-30.** Aujourd'hui, changer `objectifProduction` sur un partenaire nécessite un `PUT /partenaires/{id}` complet (tous les champs, y compris ceux qui ne changent pas). Un endpoint dédié existe déjà pour exactement ce besoin sur les exercices comptables (`PATCH /exercices-comptables/{id}/objectif`) — cette demande propose de répliquer le même pattern sur les partenaires.
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Pourquoi

Un ajustement d'objectif de production est une opération courante et isolée (revue périodique des objectifs partenaires), qui n'a aucun rapport avec les autres champs du partenaire (identité, accès de connexion, commissions…). Passer par `PUT /partenaires/{id}` oblige à renvoyer l'intégralité du DTO à chaque fois, avec le risque d'écraser un champ par erreur si le frontend n'a pas rechargé l'état le plus récent entre-temps.

## 2. Endpoint proposé

```
PATCH /api-assur-zen/partenaires/{id}/objectif
Corps : { "objectifProduction": number }
Réponse : 200, PartenaireResponse complet à jour (même schéma que PUT/POST /partenaires)
```

Strictement le même pattern que l'existant :

```
PATCH /api-assur-zen/exercices-comptables/{id}/objectif
Corps : { "objectifGlobal": number }
```

## 3. Ce qui est déjà prêt côté frontend

Rien de bloquant en attendant — `PUT /partenaires/{id}` reste utilisé pour ce cas. Une fois l'endpoint livré, `PartenaireService.setObjectif(id, objectifProduction)` sera ajouté (miroir exact de `ExerciceComptableService.setObjectif()`) et pourra être branché sur un petit raccourci dans `views/partenaire-affaire/` (ex. une icône dédiée "Ajuster l'objectif" en plus du bouton "Modifier" complet) — aucun changement de structure nécessaire ailleurs.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
