# Contrat d'interface — Montant déjà versé sur un contrat fractionné (`POST /souscriptions`)

> ✅ **Option A retenue et implémentée le 2026-08-21, compilation backend vérifiée — redémarrage + test en direct restant à faire.** Nouveau champ `montantDejaVerse` (Long, optionnel, défaut 0/null) ajouté sur `ContractSubscriptionRequest`, `ContractRequest` et `ContractResponse`, plus la colonne `montant_deja_verse` sur l'entité `Contract` (créée automatiquement par Hibernate, `ddl-auto: update`, aucune migration manuelle nécessaire). `EcheancierService.genererEcheancier()` déduit désormais ce montant de `premium` avant de répartir sur `nombreEcheances` — comportement inchangé si le champ est absent/nul. `InsuredRegistrationService.toContractRequest()` et l'import CSV (`ContractsService`) mis à jour en conséquence. Le frontend envoie déjà ce champ dans son payload dès que le formulaire renvoie `montantDejaVerse` (aucun changement frontend supplémentaire nécessaire — le champ existait déjà côté formulaire pour l'aperçu, cf. §2 ci-dessous, désormais réellement pris en compte côté serveur).
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Comportement actuel du backend

`EcheancierService.genererEcheancier()` (`assur-zen-jpa/src/main/java/ci/assurzen/services/EcheancierService.java`) répartit `contract.getPremium()` à parts égales sur `nombreEcheances`, le reliquat de la division entière étant porté par la dernière échéance :

```java
int n = contract.getNombreEcheances();
long total = contract.getPremium();
long base = total / n;
long remainder = total - (base * n);
```

Aucun champ n'existe aujourd'hui sur `ContractSubscriptionRequest` (`assur-zen-jpa/src/main/java/ci/assurzen/dto/ContractSubscriptionRequest.java`) ni sur l'entité `Contract` pour indiquer qu'une partie de la prime a déjà été reçue avant la génération de l'échéancier.

## 2. Ce que fait le frontend en attendant

Dans `souscription.component.ts` (`echeancesPreview()` / `montantRestantAFinancer()`), un nouveau champ de formulaire `montantDejaVerse` (défaut `0`, visible uniquement en mode `FRACTIONNE`) permet de saisir un montant déjà versé. Le frontend recalcule alors, **uniquement pour affichage**, un aperçu de l'échéancier sur `premium - montantDejaVerse` avec exactement le même algorithme de répartition que le backend (parts égales, reliquat sur la dernière échéance). Ce montant n'est **pas** inclus dans le payload envoyé à `POST /souscriptions` — `ContractSubscriptionRequest` ne le supporte pas.

Conséquence : si l'utilisateur saisit un montant déjà versé puis soumet le contrat, le backend génère quand même l'échéancier complet sur la prime totale, sans tenir compte de ce qui a été indiqué. L'aperçu affiché avant soumission ne correspond donc pas à ce qui sera réellement créé.

## 3. Proposition

Deux approches possibles, au choix du backend selon la règle métier voulue :

**Option A — nouveau champ sur `ContractSubscriptionRequest`, échéancier réduit d'autant :**

```java
public record ContractSubscriptionRequest(
    // ... champs existants ...
    Long montantDejaVerse // nouveau, optionnel, défaut 0
) {}
```

`genererEcheancier()` utiliserait alors `premium - montantDejaVerse` comme base de répartition (exactement le calcul déjà répliqué côté frontend dans ce PR).

**Option B — le montant déjà versé est enregistré comme un `Paiement` initial contre la/les première(s) échéance(s)**, générées sur la prime totale (comportement actuel inchangé), en réutilisant `PaiementService`/`POST /contracts/{id}/paiements` juste après la création du contrat. Cette option ne change pas `genererEcheancier()` mais ne recalcule pas non plus le montant des échéances restantes — elle marque simplement les premières comme partiellement/totalement payées à hauteur du montant versé.

L'option A correspond à ce que l'aperçu frontend calcule actuellement (répartition du **reste** sur le nombre d'échéances) ; l'option B correspond à un acompte classique sans recalcul des mensualités. Confirmer laquelle est attendue avant implémentation.

## 4. Impact utilisateur

Sans ce champ, "Montant déjà versé" reste un outil de simulation côté agent (utile pour donner une estimation orale au client pendant la saisie), mais ne doit pas être présenté comme garantissant que l'échéancier réellement créé reflète ce montant — à formuler ainsi dans toute communication interne.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
