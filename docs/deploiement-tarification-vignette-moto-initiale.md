# Tarification Vignette Moto — insertion initiale en recette / prod

**Statut** : ✅ Recette faite le 2026-09-13 (6 tarifs + 2 zones, vérifié via simulation de référence).
✅ Prod faite le 2026-09-13 (6 tarifs + 2 zones, vérifié via simulation de référence).

> Cette donnée n'est **pas** auto-seedée par le déploiement (ni via `data.sql`, ni via une migration) — voir
> `securisation-idor-2026-09-12.md` / mémoire projet pour le contexte. À exécuter **une fois par
> environnement**, avec un compte ADMIN, après chaque déploiement initial du module Vignette Moto sur un
> environnement qui ne l'a pas encore.

Reproduit exactement les 6 tarifs + 2 zones actuellement en base de test (créés le 2026-09-11), tels que
fournis à l'origine.

Remplacer `BASE_URL` et `ADMIN_TOKEN` (bearer token d'un compte ADMIN obtenu via `POST /auth/login`) avant
exécution. Chaque appel doit renvoyer `201 Created`.

> ⚠️ **Piège vérifié en recette (2026-09-13)** : `categorieAge` et `categorieCylindree` ont un mapping
> JSON différent du nom Java de l'enum (`@JsonProperty` dans `CategorieAgeMoto`/`CategorieCylindree`).
> Utiliser exactement les valeurs JSON ci-dessous (`"1_4_ANS"`, `"5_10_ANS"`, `"11_ANS_ET_PLUS"`,
> `"MOINS_125_CM3"`, `"125_CM3_ET_PLUS"`) — **pas** `"AGE_1_4_ANS"` ni `"CM3_125_ET_PLUS"` (noms Java
> bruts), qui renvoient `400 MALFORMED_REQUEST_BODY`. Les payloads ci-dessous ont été corrigés et
> re-testés avec succès en recette.

```bash
BASE_URL="https://<host>/api-assur-zen"
ADMIN_TOKEN="<token admin>"

# --- Tarifs (POST /vignettes-moto/tarifs) ---

curl -X POST "$BASE_URL/vignettes-moto/tarifs" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{
  "categorieAge":"1_4_ANS","categorieCylindree":"MOINS_125_CM3",
  "montantVignette":5000,"fraisOperation":2000,
  "dateDebutValidite":"2026-01-01"
}'

curl -X POST "$BASE_URL/vignettes-moto/tarifs" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{
  "categorieAge":"1_4_ANS","categorieCylindree":"125_CM3_ET_PLUS",
  "montantVignette":12000,"fraisOperation":3500,
  "dateDebutValidite":"2026-01-01"
}'

curl -X POST "$BASE_URL/vignettes-moto/tarifs" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{
  "categorieAge":"5_10_ANS","categorieCylindree":"MOINS_125_CM3",
  "montantVignette":3750,"fraisOperation":1500,
  "dateDebutValidite":"2026-01-01"
}'

curl -X POST "$BASE_URL/vignettes-moto/tarifs" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{
  "categorieAge":"5_10_ANS","categorieCylindree":"125_CM3_ET_PLUS",
  "montantVignette":9000,"fraisOperation":3000,
  "dateDebutValidite":"2026-01-01"
}'

curl -X POST "$BASE_URL/vignettes-moto/tarifs" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{
  "categorieAge":"11_ANS_ET_PLUS","categorieCylindree":"MOINS_125_CM3",
  "montantVignette":3500,"fraisOperation":1000,
  "dateDebutValidite":"2026-01-01"
}'

curl -X POST "$BASE_URL/vignettes-moto/tarifs" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{
  "categorieAge":"11_ANS_ET_PLUS","categorieCylindree":"125_CM3_ET_PLUS",
  "montantVignette":6000,"fraisOperation":2500,
  "dateDebutValidite":"2026-01-01"
}'

# --- Zones de livraison (POST /vignettes-moto/livraisons) ---

curl -X POST "$BASE_URL/vignettes-moto/livraisons" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{
  "code":"ABIDJAN","libelle":"Abidjan","montant":1500
}'

curl -X POST "$BASE_URL/vignettes-moto/livraisons" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{
  "code":"HORS_ABIDJAN","libelle":"Hors Abidjan","montant":2000
}'
```

## Vérification après exécution

```
GET /vignettes-moto/tarifs   -> doit renvoyer 6 tarifs actifs
GET /vignettes-moto/livraisons -> doit renvoyer 2 zones actives
```

Puis un `POST /vignettes-moto/tarification/simuler` avec `datePremiereMiseCirculation=2020-05-15`,
`cylindree=110`, `zoneLivraison=ABIDJAN` doit renvoyer `montantTotal=6750` (le cas de référence utilisé
pendant les tests du module).

## Si les valeurs changent avant le déploiement

Ce fichier reflète les valeurs données au moment de l'implémentation (2026-09-11). Si de nouveaux
montants sont décidés avant le déploiement en recette/prod, mettre à jour ce fichier en conséquence avant
de l'exécuter — ne pas déployer les anciennes valeurs par erreur.
