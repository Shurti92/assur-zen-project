# Contrat d'interface — Rattachement d'une souscription à un assuré existant (`POST /souscriptions`)

> ✅ **Entièrement livré côté backend et câblé côté frontend, vérifié en direct le 2026-08-13.** Le champ proposé (`insuredId` optionnel) a été ajouté par le backend, mais sur `InsuredRequest` (schéma désormais réutilisé par `InsuredRegistrationRequest.insured`) plutôt que sur un `InsuredSubscriptionRequest` dédié — écart de nommage sans impact fonctionnel, confirmé via `GET /v3/api-docs` avant tout test. Frontend câblé dans `souscription.component.ts` (`submit()` envoie `insuredId: this.selectedExistingInsured()?.id`). **Vérifié de bout en bout dans le navigateur, pas seulement via le schéma** : recherche de l'assurée existante "Traore Fatoumata" (id `4`) → sélection → nouvelle souscription (Assurance Auto, 100 000 FCFA) → `POST /souscriptions` → `201` → contrat `CTR2026000249` créé et rattaché à l'assurée `id 4` (confirmé : sa fiche affiche bien 4 contrats après coup, `GET /insureds?q=Fatoumata` ne renvoie toujours qu'un seul résultat, aucun doublon créé).
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Schéma actuel de `POST /souscriptions`

```ts
interface InsuredSubscriptionRequest {
  nom: string;
  prenoms: string;
  clientPhone?: string;
  clientCity?: string;
  clientEmail?: string;
  typeClient?: 'PARTICULIER' | 'ENTREPRISE';
}

interface InsuredRegistrationRequest {
  insured: InsuredSubscriptionRequest;
  contracts: ContractSubscriptionRequest[];
}
```

Aucun champ `insuredId` (ou équivalent) n'existe sur `InsuredSubscriptionRequest`. Le comportement du backend à réception (crée-t-il toujours un nouvel assuré ? dédoublonne-t-il par téléphone en interne ?) n'est pas documenté côté Swagger et n'a pas été vérifié en direct — cette page suppose le pire cas (création systématique) par prudence.

## 2. Pourquoi c'est un manque

Un client déjà présent en base (visible via `GET /insureds?q=...`) qui souscrit un nouveau produit doit aujourd'hui repasser par une saisie complète de son identité. Le frontend permet désormais de rechercher cet assuré et de pré-remplir les champs à partir de sa fiche existante (`InsuredResponse`), ce qui réduit la ressaisie et les fautes de frappe — mais à la soumission, ces mêmes champs texte repartent bruts vers `POST /souscriptions`, sans aucun lien explicite vers l'enregistrement d'origine. Si le backend crée un nouvel assuré à chaque appel (comportement non confirmé), cette fonctionnalité amortit le symptôme (moins de fautes de frappe) sans résoudre le problème de fond (toujours un doublon en base).

## 3. Proposition

Ajouter un champ optionnel sur `InsuredSubscriptionRequest` :

```ts
interface InsuredSubscriptionRequest {
  insuredId?: number; // nouveau — si présent, ignorer nom/prenoms/... et rattacher directement
  nom: string;
  prenoms: string;
  clientPhone?: string;
  clientCity?: string;
  clientEmail?: string;
  typeClient?: 'PARTICULIER' | 'ENTREPRISE';
}
```

Comportement suggéré : si `insuredId` est fourni et correspond à un assuré existant, le backend rattache les `contracts` à cet assuré sans en créer un nouveau (les autres champs peuvent alors être ignorés, ou utilisés pour mettre à jour la fiche existante — au choix du backend selon la règle métier voulue). Si `insuredId` est absent ou invalide, comportement actuel inchangé (création à partir des champs texte).

## 4. Impact utilisateur

Sans ce champ, la recherche d'assuré existant reste un confort de saisie, pas une garantie d'unicité — à formuler ainsi dans toute communication interne pour ne pas laisser croire que le doublon est déjà résolu côté données.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
