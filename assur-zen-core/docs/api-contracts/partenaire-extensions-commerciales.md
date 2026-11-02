# Contrat d'interface — Fréquence de paiement de commission & condition d'accès commercial (`PartenaireRequest`)

> 🆕 **Aucun de ces deux champs n'existe aujourd'hui.** Vérifié le 2026-08-14 dans le schéma réel `PartenaireRequest`/`PartenaireResponse` (`GET /v3/api-docs`) : les seules propriétés présentes sont `codePartenaire, type, nom, prenoms, objectifProduction, profileId, exerciceComptableId, active, villePartenaireId, typeProfileId, telephone, numeroWaveCommission` (+ `compteCree`/`compteCreationErreur` en lecture seule sur la réponse). Recherche étendue à tous les schémas existants : `periodiciteEcheances` (sur `ContractRequest`) est le seul champ de fréquence trouvé dans toute l'API, et il concerne les échéances de paiement d'un contrat, pas le versement de commission à un partenaire — confirmé qu'il n'y a rien à réutiliser.
>
> Deux besoins distincts demandés le même jour, tous deux portés par la fiche Partenaire :
> - **A.** Une fréquence de paiement de la commission par partenaire (4 valeurs : immédiat / chaque semaine / chaque 2 semaines / chaque mois).
> - **B.** Une question "Accès donné à l'application d'activité commerciale ?" (Oui/Non) qui devient une **seconde condition obligatoire**, en plus du Type Profil, pour que le compte de connexion automatique (voir `docs/api-contracts/creation-comptes-utilisateurs.md`, déjà livré) soit créé.
>
> ✅ **Les 2 points ouverts ont été tranchés avec le produit** : (A) le champ reste **purement informatif** pour l'instant — pas d'API de paiement de commission côté backend à ce jour, donc aucune automatisation attendue tant qu'elle n'existe pas. (B) si `accesActiviteCommerciale` repasse à `false` sur un partenaire qui avait déjà un compte créé, le backend doit **désactiver automatiquement le profil et le compte utilisateur associés** (pas seulement s'abstenir d'en créer un nouveau). Ce contrat est prêt à transmettre tel quel.
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. A — Fréquence de paiement de la commission

### 1.1 Besoin métier

Chaque partenaire doit pouvoir se voir assigner une fréquence de versement de sa commission, avec 4 valeurs possibles : immédiatement (dès que la commission est due), chaque semaine, chaque 2 semaines, chaque mois. Utilisé pour cadencer les paiements de commission (module Comptabilité générale → Commissions, déjà existant).

### 1.2 Proposition de schéma

Nouveau champ enum sur `PartenaireRequest`/`PartenaireResponse` :

```ts
frequencePaiementCommission?: 'IMMEDIAT' | 'HEBDOMADAIRE' | 'QUINZAINE' | 'MENSUELLE';
```

| Valeur | Libellé affiché |
|---|---|
| `IMMEDIAT` | Immédiatement |
| `HEBDOMADAIRE` | Chaque semaine |
| `QUINZAINE` | Chaque 2 semaines |
| `MENSUELLE` | Chaque mois |

Optionnel proposé par défaut (pas de valeur imposée à la création) — à trancher côté backend si une valeur par défaut métier est souhaitée (ex. `MENSUELLE`).

### 1.3 Décidé — champ purement informatif pour l'instant

Pas d'API de paiement de commission côté backend à ce jour, donc pas d'automatisation attendue : ce champ ne doit déclencher **aucun comportement serveur** (pas de job, pas de virement). Il sert uniquement à être stocké et affiché sur la fiche partenaire et dans le module Commissions. Si une automatisation est construite plus tard (le jour où une API de paiement existera), elle fera l'objet d'un contrat séparé — ne pas l'anticiper dans cette livraison.

## 2. B — Accès activité commerciale, nouvelle condition de création de compte

### 2.1 Besoin métier

Une nouvelle question sur la fiche partenaire : « Accès donné à l'application d'activité commerciale ? » (Oui/Non). Si la réponse est **Oui**, la création automatique du compte de connexion (mécanisme déjà livré, voir `docs/api-contracts/creation-comptes-utilisateurs.md` §1.A) doit se déclencher **uniquement si en plus** le Type Profil du partenaire est `REPRESENTANT` :

```
compte créé automatiquement  ⟺  accesActiviteCommerciale = true  ET  typeProfil.declencheCreationCompte = true
```

Aujourd'hui, seule la seconde condition existe côté backend (`TypeProfile.declencheCreationCompte`). Il faut donc que le backend ajoute la première condition en `ET` logique à son mécanisme de déclenchement existant — pas une simple addition de champ, un changement du comportement de création automatique.

### 2.2 Proposition de schéma

Nouveau champ sur `PartenaireRequest`/`PartenaireResponse` :

```ts
accesActiviteCommerciale?: boolean; // défaut false si absent
```

### 2.3 Impact sur le comportement existant déjà livré

`compteCree`/`compteCreationErreur` (déjà sur `PartenaireResponse`) restent inchangés dans leur forme — seule la condition de déclenchement change. Concrètement : un partenaire avec `typeProfileId` = REPRESENTANT mais `accesActiviteCommerciale = false` (ou absent) ne doit **plus** déclencher la création de compte, alors qu'aujourd'hui (Type Profil seul comme condition) il le ferait.

### 2.4 Décidé — désactivation en cascade du profil et du compte

Si un partenaire a déjà un compte créé (`compteCree: true`) et que `accesActiviteCommerciale` repasse à `false` (sur `PUT /partenaires/{id}`), le backend doit **désactiver automatiquement** :
- le compte utilisateur associé (`User.active = false`, ou équivalent — même effet que `PATCH /users/{id}/status` avec désactivation, déjà livré et utilisé côté frontend dans `views/utilisateurs/`) ;
- le profil associé (`UserProfile`/`ProfileRecord` créé en même temps que le compte via `POST /users`, voir `docs/api-contracts/creation-comptes-utilisateurs.md` §4bis).

Comportement attendu à l'inverse (repasse de `false` à `true` avec Type Profil toujours `REPRESENTANT`) : à clarifier au moment de l'implémentation si ce n'est pas déjà évident symétriquement (réactivation automatique, ou nécessite une action manuelle admin ?) — non tranché explicitement à ce stade, mais probablement symétrique par cohérence.

Aucune action frontend supplémentaire nécessaire pour cette désactivation en cascade : `views/utilisateurs/` affiche déjà le statut actif/inactif d'un compte tel que renvoyé par le backend, sans logique de désactivation à coder côté client.

---

## 3. Ce que le frontend fera dès livraison confirmée

- Formulaire Partenaire (`views/partenaire-affaire/`) : ajout d'un select "Fréquence de paiement de la commission" (4 valeurs ci-dessus) et d'un toggle "Accès à l'application d'activité commerciale".
- Aucun changement nécessaire côté `compteCree`/`compteCreationErreur` (déjà gérés) — seul le comportement serveur change, le frontend affiche déjà correctement les deux issues (succès/erreur).
- Le mécanisme reste "au cas où" tant que la livraison n'est pas confirmée : pas de champ ajouté au formulaire avant confirmation, pour ne pas laisser un utilisateur remplir des champs silencieusement ignorés par le backend.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
