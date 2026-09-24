# Rapport — Aucune écriture comptable générée lors du paiement d'une prime d'objectif (ni lors de la souscription testée)

> ✅ **FAUX POSITIF — retracté le 2026-08-30, quelques heures après l'envoi.** Cause identifiée : le backend local a été redémarré en cours de session sans recharger la variable `DB_URL` du `.env`, le faisant basculer silencieusement de `assur_zen_ci_db` vers `assur_zen_ci_db_test` (défaut du profil `test`) — sans que je le remarque, puisque les deux bases contiennent des jeux de données de test similaires. Le test navigateur complet (exercice, partenaire, contrat, cycle de prime) a bien été exécuté contre `assur_zen_ci_db_test`, mais ma vérification SQL après coup ciblait encore `assur_zen_ci_db` par réflexe — d'où l'illusion qu'aucune écriture n'existait. Reconfirmé directement dans `assur_zen_ci_db_test` : `ECR2026000318` (`PRIME_OBJECTIF`, 2026-08-30 10:50:27, exactement l'horodatage du paiement) existe bien, avec deux lignes parfaitement équilibrées — `658100 Primes objectif partenaires` débit 3 900 / `512000 Banque` crédit 3 900. Le compte dédié existe aussi (`658100`, type CHARGE), conforme à l'annonce. **Le backend n'avait aucun bug — l'erreur était entièrement de mon côté (bonne bascule d'environnement non repérée).**
>
> 🐞 ~~Écart entre l'annonce et le comportement observé, à clarifier.~~ Confirmé le 2026-08-30 en testant le cycle complet du module Prime d'objectif de bout en bout (voir `docs/api-contracts/prime-objectif.md`) — analyse initiale erronée, conservée ci-dessous pour l'historique.
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Ce qui était annoncé

> "Effets de bord à la transition PAYEE : écriture comptable générée automatiquement (nouveau compte dédié, distinct des primes d'assurance) et SMS de confirmation envoyé au partenaire."

## 2. Scénario de test (reproductible)

1. Exercice comptable de test créé (`Test Cycle Prime Objectif`, id 7, objectif 50 000 FCFA).
2. Partenaire de test créé (`TestCyclePrimeObjectif Cycle`, code `AZCI2026000311`, `primeObjectifActivee: true`, `exerciceComptableId: 7`).
3. Souscription d'un contrat réel pour ce partenaire (`POST /souscriptions` → `201`), prime 70 000 FCFA, statut `Signé` — `objectifRealise` du partenaire passe bien à 70 000 (140% de réalisation, confirmé côté écran).
4. Exercice clôturé (`POST /exercices-comptables/7/cloturer` → `200`).
5. `GET /partenaires/{id}/prime-objectif/simulation` → `eligible: true`, `montantSimule: 3900` (conforme à la formule : 5%×50 000 + 7%×20 000 = 3 900 FCFA — **formule confirmée correcte**).
6. `POST /primes-objectif` → `201` (référence `PRO2026000315`).
7. `PATCH /primes-objectif/3/statut` `{statut: VALIDEE}` → `200`.
8. `PATCH /primes-objectif/3/statut` `{statut: PAYEE}` → `200`.

Toute la machine à états et le calcul fonctionnent parfaitement — voir `docs/api-contracts/prime-objectif.md` mis à jour en conséquence.

## 3. Ce qui est observé (l'écart)

Après l'étape 8 (`PAYEE`), inspection directe de la base (`t_assur_zen_ecriture_comptable`) : **aucune nouvelle écriture n'a été créée**. La dernière ligne de la table date du 2026-08-08 (`ECR2026000168`). Ni le paiement de la prime, ni même la souscription du contrat de test à l'étape 3 (`type_operation: SOUSCRIPTION` attendu) n'ont généré d'écriture.

Recherche complémentaire dans le plan comptable (`t_assur_zen_compte_comptable`) : aucun compte dédié aux primes d'objectif trouvé (seul `706000 — Primes emises`, PRODUIT, existe — pas de compte pour ce nouveau type de charge).

## 4. Deux hypothèses, à trancher côté backend

1. **Régression/panne environnementale locale** (le plus probable au vu du point 3 : même la souscription, une fonctionnalité déjà ancienne et éprouvée, n'a pas généré d'écriture) — un composant asynchrone (listener JMS ?) ne tournerait pas sur l'instance testée. Dans ce cas, le module Prime d'objectif n'est probablement pas en cause.
2. **Le nouveau compte dédié n'a pas encore été créé dans le plan comptable**, et la génération d'écriture échoue silencieusement (sans faire échouer le `PATCH .../statut`, qui répond `200` malgré tout).

## 5. Demande

Merci de vérifier sur votre environnement si :
- une souscription de contrat classique génère bien une écriture aujourd'hui (élimine/confirme l'hypothèse 1) ;
- le compte comptable dédié aux primes d'objectif existe bien dans le plan comptable ;
- le `PATCH /primes-objectif/{id}/statut` vers `PAYEE` échoue silencieusement quelque part dans la génération d'écriture (log serveur à vérifier autour de l'appel `PATCH /primes-objectif/3/statut` du 2026-08-30).

## 6. Impact

Non bloquant pour la mécanique du module (workflow, calcul, statuts tous corrects et déjà exploitables), mais la traçabilité comptable de la prime payée manque tant que ce n'est pas corrigé.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
