# Contrat d'interface — Suppression d'une réclamation (`DELETE /reclamations/{id}`)

> ⚠️ **Endpoint manquant, pas encore proposé côté backend.** Confirmé absent le 2026-08-06 en listant tous les chemins `/reclamations` dans `https://localhost:9190/v3/api-docs` (aucune méthode `DELETE`). Aucun frontend n'est branché dessus puisqu'il n'existe pas.
>
> **Demande immédiate associée** : un enregistrement de test créé pendant la vérification du module (`REC2026000143`, id `8`) doit être supprimé manuellement en base — voir §4.
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Endpoints existants aujourd'hui sur `/reclamations`

D'après le Swagger actuel (152 endpoints au total, module Réclamations à jour du 2026-08-06) :

| Méthode | Chemin | Usage |
|---|---|---|
| `GET` | `/reclamations` | Liste paginée + filtres |
| `POST` | `/reclamations` | Création |
| `GET` | `/reclamations/{id}` | Détail |
| `PUT` | `/reclamations/{id}` | Modification |
| `PATCH` | `/reclamations/{id}/statut` | Changement de statut |
| `PATCH` | `/reclamations/{id}/affectation` | Affectation à un gestionnaire |
| `POST` | `/reclamations/{id}/cloture` | Clôture avec motif |
| `GET`/`POST` | `/reclamations/{id}/commentaires` | Commentaires |
| `GET`/`POST` | `/reclamations/{id}/pieces-jointes` | Pièces jointes |
| `GET` | `/reclamations/{id}/pieces-jointes/{pieceId}` | Téléchargement d'une pièce jointe |
| `GET` | `/reclamations/statistiques` | Statistiques globales |

Il n'existe **aucune méthode `DELETE`** sur `/reclamations/{id}` ni sur aucun de ses sous-chemins.

## 2. Pourquoi c'est un manque

Le frontend (`views/fidelisation/satisfaction/reclamations/`) permet de créer des réclamations, y compris pendant des phases de test/recette. Sans endpoint de suppression, un enregistrement créé par erreur (test, doublon, saisie erronée) reste indéfiniment visible dans la liste et compté dans `/reclamations/statistiques` et `/satisfaction-dashboard/*` — il n'y a aucun moyen de le retirer autrement qu'en le faisant "disparaître" fonctionnellement via un statut `ANNULEE`, ce qui n'est pas la même chose qu'une vraie suppression (l'enregistrement reste dans les compteurs "total").

## 3. Proposition

```
DELETE /api-assur-zen/reclamations/{id}
```

- Réponse `204 No Content` en cas de succès.
- Réponse `404` si l'id n'existe pas.
- Suggestion : restreindre la suppression aux réclamations sans historique métier significatif (ex. `creeeAutomatiquement: false` et aucune pièce jointe/commentaire), ou la réserver à un rôle admin — à la discrétion du backend selon les règles métier voulues. Une suppression logique (soft-delete, champ `supprime`/`deletedAt` masqué des listes) serait tout aussi acceptable qu'une suppression physique, du moment qu'elle retire l'enregistrement des compteurs et de la liste.

## 4. Action immédiate demandée (hors endpoint)

En attendant cet endpoint, merci de supprimer manuellement en base l'enregistrement de test suivant, créé lors de la recette du module Réclamations :

- **id** : `8`
- **numero** : `REC2026000143`
- **insuredId** : `1` (Marie Yao)
- **statut actuel** : `FERMEE`
- **description** : "Test de vérification du module Réclamations — assuré non recontacté après relance."
- Commentaire associé : "Client recontacté, dossier clôturé avec accord."
- Pas de pièce jointe.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
