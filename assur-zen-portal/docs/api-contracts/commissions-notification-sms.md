# Contrat d'interface — SMS de confirmation au partenaire lors du paiement d'une commission

> ✅ **Livré côté backend, confirmé via `/v3/api-docs` le 2026-08-27** — `CommissionPaymentResponse.notificationEnvoyee` (boolean) existe désormais. Non encore consommé côté frontend (`core/commissions/models/commission.model.ts`/`commission.service.ts` à mettre à jour pour l'afficher, ex. chip "SMS envoyé"/"SMS non envoyé" comme pour les suivis de sinistre) — aucun test live du SMS réel effectué à cette date (nécessiterait de payer une vraie commission de test).
>
> ~~Fonctionnalité absente aujourd'hui, à ajouter côté backend.~~ Confirmé le 2026-08-25 en inspectant `POST /commissions/pay` dans `https://localhost:9190/v3/api-docs` : ni la requête (`CommissionPaymentRequest`) ni la réponse (`CommissionPaymentResponse`) n'avaient de champ lié à une notification, et aucun endpoint générique d'envoi de SMS n'existait dans l'API (recherché sur tous les chemins contenant `sms`/`notif`, aucun résultat en dehors du système de campagnes marketing).
>
> Demande de l'utilisateur (2026-08-25) : "Quand la commission est payée, est-il possible d'envoyer un SMS au numéro partenaire indiqué ?"
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Endpoint concerné

```
POST /api-assur-zen/commissions/pay
{ "contractIds": [...], "reference": "..." }
```

Réponse actuelle : `CommissionPaymentResponse[]` — un objet par contrat payé (`id, contractId, policyNumber, partenaireId, partenaireNom, amount, paidByUserId, paidByUsername, reference, paidAt`). Aucun champ de notification.

## 2. Pourquoi c'est un manque

Le frontend (`views/commissions/commissions.component.*`) permet de sélectionner plusieurs contrats d'un même partenaire et de payer leur commission en une fois (`Payer la sélection`). Aujourd'hui, rien n'informe le partenaire que son argent a été versé — il doit consulter lui-même son historique. D'autres flux métier de l'application envoient déjà un SMS automatique à un événement similaire (ex. création de compte, avancement d'un dossier sinistre via `/sinistres/{id}/suivis`) — le paiement d'une commission est un événement du même ordre d'importance pour le partenaire.

## 3. Ce qui existe déjà et peut être réutilisé

- **Numéro à notifier** : `PartenaireResponse.telephone` (ajouté au DTO Partenaire le 2026-08-17, déjà utilisé pour d'autres échanges). Si un canal WAVE-only distinct est utilisé pour le paiement réel, `telephone` reste le bon champ pour un SMS (indépendant de `numeroWaveCommission`, qui est un numéro de réception d'argent, pas de messagerie).
- **Pattern déjà établi côté schéma** : `notificationEnvoyee: boolean` existe déjà sur 3 DTOs de réponse (`UserProfileResponse`, `ReclamationResponse`, `SinistreSuiviResponse`) pour confirmer côté frontend qu'un SMS est bien parti, sans exposer le contenu ni le fournisseur SMS. Même pattern proposé ici.
- **Modèles de communication** (`/templates`, catégorie "Bienvenue"/"Relance"/etc. déjà en place, ex. `RAPPEL_ECHEANCE`) : une nouvelle catégorie de modèle (ex. `COMMISSION_PAYEE`) pourrait porter le texte du SMS, avec des variables comme `{{montant}}`, `{{nombreContrats}}`, `{{reference}}`, cohérent avec le système existant plutôt qu'un texte codé en dur côté backend.

## 4. Proposition

1. À chaque paiement réussi via `POST /commissions/pay`, envoyer un SMS au `telephone` du partenaire concerné (un seul SMS agrégé si plusieurs contrats du même partenaire sont payés en un seul appel — pas un SMS par contrat).
2. Contenu suggéré : montant total payé, nombre de contrats concernés, référence du paiement si fournie (ex. *"AssurZen : votre commission de 45 000 FCFA (3 contrats) a été versée. Réf : virement du 25/08."*).
3. Ajouter `notificationEnvoyee: boolean` sur `CommissionPaymentResponse`, à `true`/`false` selon le succès réel de l'envoi (même sémantique que `SinistreSuiviResponse.notificationEnvoyee`) — permet au frontend d'afficher une confirmation ou une alerte si le SMS a échoué, sans bloquer le paiement lui-même (le paiement doit rester acquis même si l'envoi SMS échoue).
4. Si un `numeroWaveCommission` distinct existe et diverge de `telephone`, préciser lequel des deux doit recevoir le SMS — supposé être `telephone` par défaut dans cette proposition, à confirmer côté backend.

## 5. Ce qui est déjà prêt côté frontend

Rien de spécifique encore — `commission.model.ts`/`commission.service.ts` seront mis à jour pour lire `notificationEnvoyee` et l'afficher (ex. un chip "SMS envoyé"/"SMS non envoyé" à côté du montant payé, comme c'est déjà fait pour les suivis de sinistre) dès que le champ existera côté backend. Pas de développement bloquant en attendant.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
