# Retour frontend — Module Commercial (intégration terminée, 3 points à trancher/vérifier avant prod)

> 📋 **Contexte** : l'intégration frontend du module Commercial (livré le 2026-09-07) est terminée — dashboard self-service, objectifs, historique de performance, classement d'équipe, portefeuille de contrats self-scopé. Elle a été construite entièrement à partir de la prose de `module-commercial.md`, faute d'OpenAPI machine fourni pour ce module à ce stade. Trois points restent à trancher ou à vérifier avant la mise en production, détaillés ci-dessous.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Paiement partiel de commission pour un commercial interne — endpoint à confirmer/livrer, ou fonctionnalité à retirer

Le partenaire dispose d'une fonctionnalité "demander un paiement partiel" sur sa commission en attente (`GET`/`POST /partenaires/{id}/commissions/partial-payment-requests`, plafond 40%, 2 demandes/jour). `module-commercial.md` ne documente rien d'équivalent pour un commercial interne — son modèle de rémunération y est décrit comme salaire + bonus mensuel sur objectif, pas une commission par contrat avec possibilité d'avance.

Le frontend affiche pourtant déjà ce bouton pour le commercial interne (onglet "Portefeuille" de l'accueil), câblé par symétrie sur un endpoint hypothétique **non confirmé** :

```
GET  /commercials/{id}/commissions/partial-payment-requests
POST /commercials/{id}/commissions/partial-payment-requests   { "amount": number }
```

**Demande** : deux issues possibles, à trancher côté produit/backend :
- **Un vrai commercial interne touche-t-il une commission par contrat en plus de son salaire+bonus ?** Si oui, confirmer/livrer cet endpoint (même forme que son équivalent partenaire — `pendingBalance`, `requestsToday`, `maxRequestsPerDay`, `nextRequestAvailableAt`, `history[]` avec `requestedAmount`/`requestedAt`/`status`).
- Si non, nous dire explicitement que ce n'est pas prévu : nous retirerons la fonctionnalité de l'écran du commercial interne plutôt que de laisser un bouton qui échouera en silence contre le vrai backend.

## 2. Confirmer que le rôle `COMMERCIAL` n'est jamais attribué à un compte partenaire

Toute la bascule d'interface (nouveau module vs dashboard partenaire classique) repose côté frontend sur un seul signal : `user.role === 'commercial'` (normalisé depuis le rôle backend, casse indifférente) désigne exclusivement un vrai commercial interne. Nous avons volontairement arrêté de mapper l'ancien fallback `"user" → "commercial"` (cf. `docs/api-contracts/module-commercial.md` §1) pour éviter toute confusion avec ce nouveau rôle réel.

**Demande** : confirmer explicitement qu'un compte partenaire ne reçoit **jamais** le rôle `COMMERCIAL` (ni aujourd'hui, ni dans une évolution prévue) — cette hypothèse n'a pas pu être testée en direct faute d'environnement partagé à ce stade. Si un partenaire peut un jour porter ce rôle, il faudra un second signal distinctif (ex. présence/absence de `partenaireId` dans la réponse `/auth/login`) pour fiabiliser la bascule.

## 3. Vérifier les noms de champs exacts des 6 endpoints du module (Swagger/OpenAPI)

Les schémas de validation frontend (Zod) ont été construits à partir de la prose de `module-commercial.md`, pas d'un contrat machine — le risque de divergence sur les noms de champs exacts est réel. Champs actuellement attendus :

| Endpoint | Champs attendus côté frontend |
| --- | --- |
| `GET /commercials/me` | `id`, `fullName` (ou `nom`+`prenoms`), `matricule`, `villePartenaireNom`, `remuneration` |
| `GET /commercials/me/dashboard` | `objectif`, `realise`, `achievementRate` (0-100), `resteARealiser`, `bonusEstime`, `statut` |
| `GET /commercials/{id}/objectives` | `id`, `exerciceComptableLibelle`, `montant` |
| `GET /commercials/{id}/monthly-objectives` | `id`, `moisComptableLibelle`, `montant` |
| `GET /commercials/{id}/performance` | `id`, `moisComptableLibelle`, `objectif`, `realise`, `achievementRate` (0-100), `bonus`, `statut` |
| `GET /commercial-performance/ranking` | `commercialId`, `fullName`, `achievementRate` (0-100), `rang` |

**Demande** : dès que `/v3/api-docs` (Swagger) est accessible pour ce module, confirmer ou corriger ces noms de champs — un simple diff suffit, pas besoin de repasser par un cycle de spécification complet. Rappel : `achievementRate` est bien en 0-100 sur ces endpoints (confirmé dans votre message du 2026-09-07) — le frontend le convertit en 0-1 dans son mapping, cohérent avec `objectiveProgress`/`commissionRate` ailleurs dans l'app (`docs/API_CONTRACT.md` §1). Pas besoin de le changer côté backend, juste le garder en tête si vous ajustez la forme de la réponse.

---

## Réponse attendue

Merci de préciser, pour chaque point, s'il est pris en compte et sous quel délai estimé, afin de prioriser les chantiers frontend en conséquence. Le point 1 est le plus bloquant : c'est une vraie décision produit (le bouton est déjà visible en prod potentielle), les points 2 et 3 sont des vérifications qui peuvent attendre l'accès à un environnement partagé.

## Rappel

Intégration frontend complète disponible dans `src/core/entities/Commercial.ts`, `src/core/ports/ICommercialRepository.ts`, `src/infrastructure/repositories/CommercialRepository.ts`, `src/infrastructure/http/schemas.ts` (schémas `commercial*`). Mocks de démonstration (`VITE_USE_MOCKS=true`) disponibles via le compte de test `commercial-interne@gmail.com` / `1234` sur l'écran de connexion.
