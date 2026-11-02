# Contrat d'interface — Demande de paiement de commission (commercial interne)

> 🟠 **À livrer côté backend.** Proposé le 2026-09-25. Le frontend est déjà prêt : l'écran s'active tout seul dès que `GET /commercials/me/commissions/payment-requests` cesse de répondre `404` — aucun redéploiement frontend nécessaire.
>
> **Contexte** : depuis le module Commissions commerciales, un commercial interne voit sa commission sur affaires réalisées (`GET /commercials/me/commissions` — `totalPending`, `totalPaid`, détail par contrat). Il n'a en revanche **aucun moyen d'en demander le versement** : seul `POST /commercial-commissions/pay` existe, réservé aux administrateurs. Le partenaire, lui, dispose de `/partenaires/{id}/commissions/partial-payment-requests`, mais ce chemin est inutilisable pour un commercial (pas de `partenaireId`).

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Besoin métier

Le commercial connecté doit pouvoir demander, depuis son espace :

- un **paiement total** : tout son solde de commission disponible ;
- un **paiement partiel** : un montant **libre**, choisi par lui, entre 1 FCFA et son solde disponible. **Pas de plafond en pourcentage** (contrairement au partenaire et à ses 40 %), décision produit du 2026-09-25.

La demande est ensuite validée par un administrateur ou la finance, qui déclenche le versement effectif (même principe que `POST /commissions/partial-payment-requests/{id}/valider` côté partenaire).

## 2. Endpoints proposés (self-service, scopés par le `sub` du JWT)

Pas de `{id}` dans le chemin : le commercial est déduit du token, comme `/commercials/me/commissions`. Tout autre rôle que `COMMERCIAL` → `403`.

### `GET /commercials/me/commissions/payment-requests`

```json
{
  "availableBalance": 3000,
  "history": [
    {
      "id": 12,
      "type": "PARTIAL",
      "requestedAmount": 1000,
      "requestedAt": "2026-09-25T10:12:00Z",
      "status": "pending"
    }
  ]
}
```

- `availableBalance` = `totalPending` de `/commercials/me/commissions` **moins la somme des demandes encore `pending`**. Cela évite qu'un même montant soit demandé deux fois avant validation.
- `history` : trié par `requestedAt` décroissant, `[]` si aucune demande.
- `type` : `"TOTAL"` | `"PARTIAL"`.
- `status` : `"pending"` | `"paid"` | `"rejected"`, mêmes valeurs que les demandes partenaire.

### `POST /commercials/me/commissions/payment-requests`

Header optionnel **`Idempotency-Key`** (UUID v4), même sémantique que côté partenaire : un rejeu de la même clé renvoie la demande déjà créée. Le frontend l'envoie systématiquement.

```json
{ "type": "TOTAL" }
```

```json
{ "type": "PARTIAL", "amount": 1000 }
```

- `TOTAL` : `amount` est ignoré, et le serveur prend tout `availableBalance`.
- `PARTIAL` : `amount` est obligatoire, entier, avec `1 ≤ amount ≤ availableBalance`, **revalidé côté serveur**.

Réponse `201` : l'objet demande créé (même forme qu'un élément de `history`).

### Erreurs attendues (`{ "message", "code" }`, message en français affiché tel quel)

| HTTP | `code` | Cas |
|---|---|---|
| `409` | `NOTHING_TO_PAY` | `availableBalance` vaut 0 |
| `422` | `AMOUNT_EXCEEDS_BALANCE` | `amount` supérieur à `availableBalance` |
| `422` | `INVALID_AMOUNT` | `amount` absent, nul ou négatif pour `PARTIAL` |
| `403` | — | appelant sans le rôle `COMMERCIAL` |

## 3. Décisions produit (2026-09-25)

1. **Canal de versement — décidé le 2026-09-25** : le versement se fait sur le numéro de téléphone du commercial (`phone` de `CommercialResponse`). Aucun champ dédié de type `numeroWaveCommission` n'est à ajouter.
2. **Limite de fréquence — décidé** : aucune. Le commercial peut faire autant de demandes qu'il veut, dans la limite de `availableBalance`.
3. **Validation admin — décidé** : réutiliser l'écran et le flux de validation des demandes partenaire (`POST /commissions/partial-payment-requests/{id}/valider`), en l'étendant aux demandes des commerciaux plutôt que d'en créer un dédié.

Merci d'indiquer un délai de livraison estimé.

## 4. Côté frontend (déjà implémenté)

- `src/infrastructure/repositories/CommercialRepository.ts` : `getPaymentRequests()` renvoie `null` sur `404`, ce qui masque la fonctionnalité ; `requestPayment()` envoie la demande.
- `src/presentation/components/dashboard/CommercialPaymentRequestDrawer.tsx` : choix Total ou Partiel, saisie du montant, historique.
- Mocks MSW (`npm run dev:msw`) avec la forme ci-dessus, pour démontrer l'écran avant livraison.
