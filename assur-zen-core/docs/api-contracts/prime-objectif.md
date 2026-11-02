# Contrat d'interface — Prime d'objectif (surprime de dépassement) pour les partenaires

> 🆕 **Nouvelle fonctionnalité demandée le 2026-08-28.** Le système sait déjà fixer un objectif de production à un partenaire (`Partenaire.objectifProduction`) et calculer sa réalisation (`objectifRealise`, `tauxRealisation`, tous deux déjà backend-computed). Cette demande ajoute une **prime** ("surprime") versée au partenaire quand il atteint ou dépasse son objectif, avec des paliers de taux croissants selon le niveau de dépassement.
>
> Décisions déjà tranchées côté métier (validées explicitement, pas de zone d'ombre à deviner) :
> - Seuils : **100-109% → 5%**, **110-124% → 6%**, **≥125% → 7%**.
> - Base de calcul : le taux du palier haut (6% ou 7%) s'applique **uniquement au dépassement** (réalisé − objectif), en plus des 5% de base sur l'objectif — voir formule exacte en §3.
> - Paramétrage : **taux globaux à l'application**, mais **activation individuelle par partenaire** (chaque partenaire a un interrupteur "Prime d'objectif activée", pas de taux personnalisé par partenaire).
> - Workflow : **demande à valider par l'admin** — même schéma que le "versement anticipé de commission" déjà en place (`t_assur_zen_commission_partial_payment_request`) : le partenaire/agent demande, statut `EN_ATTENTE` → `VALIDEE` → `PAYEE` (ou `REJETEE`).
> - **Blocage métier explicite** : impossible de demander la prime tant que l'exercice comptable concerné n'est pas `cloture: true` (`ExerciceComptableResponse.cloture`, déjà existant).
>
> Suivi dans la mémoire projet "dépendances backend manquantes".

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Nouveau champ sur `Partenaire`

```
PartenaireRequest / PartenaireResponse
  primeObjectifActivee: boolean   // défaut false — interrupteur par partenaire, indépendant de accesActiviteCommerciale
```

Affiché et modifiable dans le même formulaire que `frequencePaiementCommission`/`numeroWaveCommission` (section "Commissions").

## 2. Taux globaux — réutiliser le module "Configuration sans redéploiement" existant

Plutôt qu'une nouvelle table de paramètres, ces 5 valeurs sont de bons candidats pour le module `/configurations` déjà livré (CRUD + historique + `valueType: DECIMAL/INTEGER` déjà supportés) :

| `configKey` proposé | `valueType` | Valeur par défaut | Rôle |
|---|---|---|---|
| `PRIME_OBJECTIF_SEUIL_BASE` | INTEGER | `100` | % de réalisation minimum pour déclencher la prime de base |
| `PRIME_OBJECTIF_TAUX_BASE` | DECIMAL | `5` | Taux (%) appliqué à l'objectif une fois le seuil de base atteint |
| `PRIME_OBJECTIF_SEUIL_PALIER2` | INTEGER | `110` | % de réalisation à partir duquel le taux de dépassement passe à 6% |
| `PRIME_OBJECTIF_TAUX_PALIER2` | DECIMAL | `6` | Taux (%) appliqué au dépassement entre le palier 2 et le palier 3 |
| `PRIME_OBJECTIF_SEUIL_PALIER3` | INTEGER | `125` | % de réalisation à partir duquel le taux de dépassement passe à 7% |
| `PRIME_OBJECTIF_TAUX_PALIER3` | DECIMAL | `7` | Taux (%) appliqué au dépassement au-delà du palier 3 |

`module`/`category` à la discrétion du backend selon les valeurs déjà définies dans l'enum (ex. `COMMISSIONS` ou équivalent) — compléter l'enum si aucune catégorie existante ne convient. **Avantage** : aucune nouvelle UI d'administration à construire côté frontend, le module Configuration existant (`views/configuration-dynamique/`) suffit déjà pour éditer ces 6 clés, avec historique/audit gratuit.

## 3. Formule de calcul exacte

Soit `objectif = Partenaire.objectifProduction`, `realise = Partenaire.objectifRealise`, `taux = realise / objectif * 100`.

```
si taux < SEUIL_BASE (100)                        → prime = 0
si SEUIL_BASE ≤ taux < SEUIL_PALIER2 (110)         → prime = TAUX_BASE% × objectif
si SEUIL_PALIER2 ≤ taux < SEUIL_PALIER3 (125)      → prime = TAUX_BASE% × objectif + TAUX_PALIER2% × (realise − objectif)
si taux ≥ SEUIL_PALIER3 (125)                      → prime = TAUX_BASE% × objectif + TAUX_PALIER3% × (realise − objectif)
```

**Exemples chiffrés** (objectif = 1 000 000 FCFA) :
- Réalisé 1 050 000 (105%) → prime = 5% × 1 000 000 = **50 000 FCFA**
- Réalisé 1 200 000 (120%) → prime = 5% × 1 000 000 + 6% × 200 000 = 50 000 + 12 000 = **62 000 FCFA**
- Réalisé 1 400 000 (140%) → prime = 5% × 1 000 000 + 7% × 400 000 = 50 000 + 28 000 = **78 000 FCFA**

## 4. Nouveau module : demandes de prime d'objectif

Même vocabulaire de statut que l'échange de récompenses fidélité (`EN_ATTENTE`/`VALIDEE`/`PAYEE`/`REJETEE`) plutôt que réinventer un enum, pour cohérence avec le reste de l'API.

```
PrimeObjectifRequest (corps de création)
  partenaireId: number
  exerciceComptableId: number

PrimeObjectifResponse
  id: number
  reference: string                 // ex. PRO2026000001, même style que CTR.../PRN...
  partenaireId: number
  partenaireNom: string
  exerciceComptableId: number
  exerciceComptableLibelle: string
  objectifProduction: number        // snapshot au moment de la demande
  objectifRealise: number           // snapshot
  tauxRealisation: number           // snapshot, en %
  palierAtteint: 'BASE' | 'PALIER2' | 'PALIER3'
  tauxApplique: number              // taux de dépassement appliqué (0, 6 ou 7 — 5 est toujours inclus dans montantPrime)
  montantPrimeBase: number          // 5% × objectif
  montantPrimeDepassement: number   // 0 si palier BASE
  montantPrimeTotal: number         // somme des deux
  statut: 'EN_ATTENTE' | 'VALIDEE' | 'PAYEE' | 'REJETEE'
  motifRejet: string | null
  demandeParUserId: number
  demandeAt: string (datetime)
  valideParUserId: number | null
  valideAt: string | null
  payeAt: string | null
```

### Endpoints proposés

| Méthode | Chemin | Usage |
|---|---|---|
| `GET` | `/partenaires/{id}/prime-objectif/simulation?exerciceComptableId=` | **Aperçu sans créer de demande** — calcule et renvoie un `PrimeObjectifResponse` "virtuel" (statut absent) pour affichage avant demande. Doit fonctionner même si l'exercice n'est pas encore clôturé (simple simulation), contrairement à la création réelle. |
| `POST` | `/primes-objectif` | Crée une demande réelle. **Doit refuser explicitement** (voir §5) si les conditions ne sont pas réunies. |
| `GET` | `/primes-objectif` | Liste paginée, filtres `partenaireId`/`exerciceComptableId`/`statut`. |
| `GET` | `/primes-objectif/{id}` | Détail. |
| `PATCH` | `/primes-objectif/{id}/statut` | Corps `{ statut: 'VALIDEE'|'PAYEE'|'REJETEE', motif?: string }` — transition de statut par un admin. |

## 5. Règles de refus explicites à la création (`POST /primes-objectif`)

Merci de renvoyer un statut HTTP **4xx explicite avec un message métier clair** dans chacun de ces cas plutôt qu'un succès silencieux ou un `500` générique (on a déjà eu plusieurs incidents sur ce point précis sur d'autres endpoints, voir `docs/api-contracts/recap-points-ouverts-2026-08-27.md` §1 et §4) :

1. `Partenaire.primeObjectifActivee = false` → `409 Conflict`, message "La prime d'objectif n'est pas activée pour ce partenaire."
2. `ExerciceComptable.cloture = false` pour l'exercice visé → `409 Conflict`, message "L'exercice comptable doit être clôturé avant de pouvoir demander cette prime."
3. `tauxRealisation < SEUIL_BASE` (objectif non atteint) → `409 Conflict`, message "L'objectif n'est pas atteint pour cet exercice — aucune prime due."
4. Une demande existe déjà pour ce couple `(partenaireId, exerciceComptableId)` avec un statut autre que `REJETEE` → `409 Conflict`, message "Une demande de prime existe déjà pour ce partenaire sur cet exercice." (évite les doublons, comme `idempotency_key` sur le versement anticipé de commission).

## 6. Notification (optionnel, à la discrétion du backend)

Pas explicitement demandé par l'utilisateur, mais cohérent avec le reste de l'API (`notificationEnvoyee` déjà présent sur plusieurs DTOs) : si un SMS de confirmation est envisagé au moment du paiement (`statut → PAYEE`), ajouter `notificationEnvoyee: boolean` sur `PrimeObjectifResponse` selon le même pattern. Aucun développement frontend bloquant si ce champ n'existe pas — à ajouter plus tard sans casser la compatibilité.

## 7. Ce qui est déjà prêt côté frontend, en attente de l'API

Rien encore construit (blocage complet sur cette API) — le plan une fois l'API livrée : toggle `primeObjectifActivee` dans le formulaire Partenaire (`views/partenaire-affaire/`), nouvelle page "Primes d'objectif" (liste + simulation + workflow de validation, réutilisant les patterns déjà établis pour le versement anticipé de commission et l'échange de récompenses fidélité).

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
