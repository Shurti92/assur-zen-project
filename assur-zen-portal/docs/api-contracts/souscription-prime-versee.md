# Contrat d'interface — « Prime versée ? » à l'enregistrement du contrat (partenaire ou commercial)

> ⏳ **Frontend livré le 2026-09-25 — 2 changements backend attendus.** Le formulaire de souscription
> et la modification de contrat (`PUT /contracts/{id}`) permettent désormais d'indiquer si la prime
> encaissée par l'intermédiaire (partenaire **ou** commercial) a déjà été versée à la compagnie.

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Ce qui fonctionne déjà

- `ContractRequest.primeReversementStatus` (`non_reverse` | `reverse`) est pris en compte par
  `POST /contracts` et `PUT /contracts/{id}` (`ContractsService.create()` / `update()`).
- Le frontend l'envoie dans le `PUT` de l'écran **Contrats → détail → Modifier**.

## 2. Changement attendu n°1 — `POST /souscriptions`

`ContractSubscriptionRequest` n'a pas de champ `primeReversementStatus`, et
`InsuredRegistrationService.toContractRequest()` passe `null` à cette position. Le frontend envoie
déjà le champ pour chaque contrat de la souscription (quand un partenaire ou un commercial est
sélectionné). Pour l'instant, Jackson l'ignore.

```java
public record ContractSubscriptionRequest(
        // ... champs existants ...
        Long commercialCommissionAmount,
        StatutReversementPrime primeReversementStatus // nouveau, optionnel
) {}
```

Et dans `toContractRequest()`, remplacer le `null` (avant-dernier argument) par
`s.primeReversementStatus()`.

Payload envoyé par le frontend (extrait) :

```json
{
  "contracts": [
    { "productId": 3, "partenaireId": 12, "premium": 150000, "primeReversementStatus": "reverse" }
  ]
}
```

## 3. Changement attendu n°2 — reversement de prime pour un commercial

Règle métier demandée : la question « prime versée ? » concerne aussi les **ventes directes faites
par un commercial** (`salesRepId` renseigné, pas de partenaire), qui encaisse la prime auprès du
client.

Aujourd'hui, le backend limite le reversement aux contrats liés à un partenaire :

| Emplacement | Comportement actuel | Attendu |
|---|---|---|
| `ContractsService.reverserPrime()` | 409 « n'est rattache a aucun partenaire » | Accepter si `partenaire != null` **ou** `salesRep != null` |
| `filterPrimeNonReversee()` (`GET /contracts/prime-non-reversee`) | `partenaire != null` uniquement | Inclure aussi les contrats avec `salesRep != null` |
| `create()` / `update()` | Déjà sans restriction | Inchangé |

Le frontend affiche déjà le statut « Prime reversée à la compagnie » dans le détail d'un contrat
quand il y a un partenaire ou un commercial.

## 4. Impact tant que ce n'est pas livré

- Pour une **souscription**, le choix est perdu : le contrat est créé en `non_reverse`. Il suffit
  ensuite de le corriger via **Modifier le contrat**, qui passe déjà par `PUT` et fonctionne.
- Pour un contrat **commercial** en `non_reverse`, il n'apparaît pas dans « Primes non reversées ».
  Il peut être passé en `reverse` via **Modifier le contrat**.
