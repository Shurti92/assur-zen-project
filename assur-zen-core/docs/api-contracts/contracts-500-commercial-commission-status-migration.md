# Rapport — `GET /contracts` et `GET /contracts/{id}` renvoient 500 sur tout contrat existant depuis la livraison "Commission commerciale interne"

> 🐞 **Bloquant** — trouvé en direct le 2026-09-08 en testant le paiement de commission commerciale (nécessitant de créer un contrat direct). Empêche toute lecture d'un contrat existant, pas seulement le nouveau flux de commission — impact bien au-delà du périmètre de la livraison du jour.
>
> Suivi dans la mémoire projet "dépendances backend manquantes" (entrée 47/48).

Base URL gateway : `https://localhost:9190/api-assur-zen` (via proxy `/api-assur-zen` en dev).

---

## 1. Ce qui est observé

Depuis la livraison de la commission commerciale interne (3 nouvelles colonnes sur `t_assur_zen_contracts`, `ddl-auto: update`), **toute lecture d'un contrat déjà existant en base échoue en 500** :

- `GET /contracts?page=0&size=1000` → `500 {"code":"INTERNAL_ERROR","message":"Une erreur inattendue est survenue"}`
- Même résultat avec `size=1` ou `size=10` — pas un problème de volume/pagination.
- `GET /contracts/{id}` pour des ids qui existent réellement en base (testé sur 4, 5, 6, 8, 9, 10, 11...) → **même 500**.
- `GET /contracts/{id}` pour des ids qui n'existent PAS (1, 2, 3, 7) → `404 "Contrat introuvable: {id}"` propre, comme attendu.

La distinction 404 propre vs 500 est le signal clé : la requête SQL elle-même s'exécute normalement (sinon même un id inexistant échouerait), le problème survient uniquement **au moment de mapper une ligne réellement présente en base** vers l'entité JPA.

## 2. Hypothèse (code revu côté `assur-zen-jpa`)

`Contract.java` (lignes ~160-172) :

```java
@Column(name = "commercial_commission_rate", precision = 8, scale = 4)
private BigDecimal commercialCommissionRate;

@NotNull
@Builder.Default
@Column(name = "commercial_commission_amount", nullable = false)
private Long commercialCommissionAmount = 0L;

@NotNull
@Enumerated(EnumType.STRING)
@Builder.Default
@Column(name = "commercial_commission_status", nullable = false, length = 20)
private CommissionStatus commercialCommissionStatus = CommissionStatus.pending;
```

`@Builder.Default` est une construction Lombok pure côté Java (valeur par défaut si on construit l'objet via le builder) — **elle n'a aucun effet sur le DDL généré par Hibernate**. Avec `ddl-auto: update` sur une table déjà peuplée, l'`ALTER TABLE ... ADD COLUMN ... NOT NULL` généré pour `commercial_commission_status` (varchar) n'a pas de valeur par défaut explicite au niveau SQL. Selon le mode strict de la base, les lignes déjà existantes se retrouvent probablement avec une chaîne vide (`''`) plutôt que `NULL` ou `'pending'` dans cette colonne.

Résultat : quand Hibernate hydrate une ligne existante et tente `CommissionStatus.valueOf("")` (mapping `@Enumerated(EnumType.STRING)`), ça lève une `IllegalArgumentException` — capturée par le handler générique et renvoyée en `500 INTERNAL_ERROR` sans détail. `commercial_commission_amount` (bigint) n'a probablement pas ce problème (une base non stricte le remplit à `0` par défaut, valeur Java valide).

## 3. Scénario de test (reproductible)

Testé depuis la console du navigateur, authentifié en admin :

```js
const token = localStorage.getItem('auth_access_token');
const auth = { Authorization: 'Bearer ' + token };
await fetch('/api-assur-zen/contracts?page=0&size=1', { headers: auth }).then(r => r.status); // 500
await fetch('/api-assur-zen/contracts/4', { headers: auth }).then(r => r.status);              // 500 (id existant)
await fetch('/api-assur-zen/contracts/1', { headers: auth }).then(r => r.status);              // 404 (id inexistant)
```

## 4. Impact

**Bloquant, transversal** — bien au-delà de la commission commerciale :
- Liste des contrats (`views/contrats/`) totalement inutilisable.
- Formulaire de souscription : le chargement des données de référence échoue globalement (`forkJoin` incluant la liste des contrats renouvelables), empêchant même la création d'un **nouveau** contrat.
- Dashboard, commissions partenaire, tout ce qui référence un contrat existant.

## 5. Demande

1. Vérifier en base le contenu réel de la colonne `commercial_commission_status` sur les contrats créés avant cette livraison (probable chaîne vide au lieu de `'pending'`).
2. Si confirmé : `UPDATE t_assur_zen_contracts SET commercial_commission_status = 'pending' WHERE commercial_commission_status IS NULL OR commercial_commission_status = '';` (ou équivalent), une fois en dev et une fois en recette/prod avant tout déploiement de cette fonctionnalité.
3. Pour éviter la récidive sur un futur ajout de colonne `NOT NULL` via `ddl-auto: update` sur une table déjà peuplée : soit fournir une valeur par défaut au niveau SQL (`@ColumnDefault("pending")` en plus de `@Column(nullable = false)`), soit accompagner la livraison d'un script de backfill explicite.

---

## Rappel

Suivi dans la mémoire projet "dépendances backend manquantes".
