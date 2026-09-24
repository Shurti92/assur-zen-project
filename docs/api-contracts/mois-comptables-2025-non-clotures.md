# Rapport — Les mois de l'exercice 2025 (déjà clôturé) sont tous restés "Ouvert" après le backfill

> 🐞 **Écart entre l'annonce et le comportement observé, non bloquant.** Confirmé le 2026-08-28 en testant la nouvelle fonctionnalité "découpage mensuel des exercices comptables" (annonce backend du même jour) dans le navigateur, juste après son intégration côté frontend.
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Ce qui était annoncé

> "Clôturer un exercice (auto ou manuel) clôture aussi en cascade tous ses mois encore ouverts."
>
> "Les exercices déjà existants (2025, 2026) ont aussi été backfillés, leurs 12 mois sont déjà disponibles."

## 2. Ce qui est observé

`GET /exercices-comptables/2` (Exercice 2025, `cloture: true` — clôturé bien avant cette fonctionnalité) renvoie ses 12 mois backfillés, mais **tous avec `cloture: false`** :

```
Janvier 2025   → OUVERT
Février 2025   → OUVERT
...
Décembre 2025  → OUVERT
```

Vérifié en direct dans le navigateur (modale "Mois — Exercice 2025"), les 12 lignes affichent bien le statut Ouvert malgré l'exercice parent clôturé.

À l'inverse, le comportement en direct sur l'exercice 2026 (non clôturé) est correct dans les deux sens : clôturer puis réouvrir le mois de Janvier 2026 via les nouveaux endpoints fonctionne, `200` à chaque appel, l'état est bien reflété immédiatement.

## 3. Cause probable, à confirmer

En cherchant la table portant l'état de clôture des mois dans le schéma MySQL (`assur_zen_ci_db`) pour comprendre l'écart, **aucune table ni colonne correspondante n'a été trouvée** (recherche sur toutes les tables/colonnes contenant `mois`, `month`, ou `cloture`, en ne trouvant que `t_assur_zen_exercice_comptable.cloture`, sans équivalent pour les mois). Deux hypothèses, à trancher côté backend :

1. **Le backfill n'a simplement pas rejoué la cascade de clôture** pour les exercices déjà clôturés au moment de la migration — un correctif ponctuel sur les données existantes suffirait (repasser `cloture: true` sur les mois des exercices déjà clôturés).
2. **L'état de clôture d'un mois n'est pas persisté du tout** (calculé ou gardé en mémoire plutôt qu'en base) — dans ce cas, plus préoccupant : un redémarrage du backend réinitialiserait silencieusement tous les mois à "Ouvert", y compris ceux clôturés manuellement en cours de session. Si c'est le cas, merci de nous le confirmer explicitement pour qu'on adapte le message affiché à l'admin (aujourd'hui le frontend annonce que la clôture est actée, sans réserve sur sa persistance).

## 4. Impact

Aucun impact fonctionnel bloquant à ce stade — la clôture d'un mois est purement structurelle/reporting et ne bloque aucune saisie (confirmé dans l'annonce initiale). Mais si l'hypothèse 2 est la bonne, la fonctionnalité de suivi mensuel perdrait tout son intérêt (rien ne serait durablement mémorisé), donc à clarifier avant qu'un utilisateur ne s'appuie dessus pour du reporting réel.

## 5. Ce qui est déjà prêt côté frontend

`views/configuration/exercice-comptable/` affiche déjà les 12 mois avec statut et actions clôturer/réouvrir — aucun changement de code nécessaire une fois la cause confirmée/corrigée côté backend, sauf si la réponse est "état non persistant", auquel cas un message d'avertissement sera ajouté dans la modale.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
