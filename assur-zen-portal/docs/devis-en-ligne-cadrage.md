# Devis en ligne : cadrage (03/11/2026)

Périmètre décidé : **Moto (assurance) et Auto / VTC**, calcul par **grille paramétrable** (saisie dans le portail back-office), utilisé par l'**application mobile** et les **commerciaux** (back-office). Pas de site web public à ce stade.

## Existant réutilisable
- `Produit` (nom, type, actif), `Vehicule` (type, puissance fiscale, cylindrée, mise en circulation), `Contract` (prime, durée, commissions, commercial `salesRep`).
- Modèle de grille paramétrable déjà éprouvé : `TarifVignetteMoto` / `CategorieCylindree` / `CategorieAgeMoto` + écran admin.
- Catalogue mobile (`/public/products`) et contenu produit (`MobileProductContent`).
- Aucune entité devis, aucune grille moto/auto/VTC.

## Modèle proposé
- `GrilleTarifaire` : produit, formule, critères (JSON borné ou colonnes), prime, durée, période de validité, actif. Une ligne = une combinaison de critères.
- `Devis` : référence `DEV-AAAA-NNNNNN`, produit, formule, critères saisis, prime calculée, taxes/accessoires, durée, date de validité (30 j par défaut, configurable), statut `CALCULE | ENVOYE | ACCEPTE | EXPIRE | CONVERTI`, prospect (nom, téléphone) ou compte mobile, commercial éventuel, canal `MOBILE | BACKOFFICE`.
- Conversion : un devis accepté crée la demande de souscription / le contrat (flux existant), conserve le lien devis → contrat.

## API envisagée
Mobile (public ou client) :
- `GET /public/quotes/products` : produits cotables + critères attendus (pilote le formulaire).
- `POST /public/quotes/simulate` : calcul sans enregistrement (prime, détail, formules).
- `POST /mobile/quotes` : enregistre le devis du client connecté ; `GET /mobile/quotes[/{ref}]` ; `POST /mobile/quotes/{ref}/accept`.

Back-office (ADMIN, COMMERCIAL) :
- `GET/PUT /admin/quotes/grids` (grilles), `GET /admin/quotes` (liste, filtres), `POST /admin/quotes` (devis pour un prospect), `POST /admin/quotes/{id}/convert`.

## Règles à valider avant de coder (bloquantes)
1. **Critères de tarification** par produit.
   - Moto : cylindrée, usage, âge du véhicule, âge du conducteur ?
   - Auto : puissance fiscale, énergie, usage (VTC inclus), valeur vénale, âge du véhicule ?
2. **Formules et garanties** : liste exacte (ex. Tiers, Tiers complet, Tous risques) et ce que chacune ajoute.
3. **Grille** : qui fournit les montants ? Sans fichier de tarifs, je livre le moteur et l'écran de saisie avec des grilles vides ou de démonstration.
4. **Composition de la prime** : prime nette + accessoires + taxes + frais ; durées proposées (1, 3, 6, 12 mois) et prorata.
5. **Validité du devis** (durée) et règle de réajustement si la grille change entre devis et souscription.
6. **Réduction commerciale** : un commercial peut-il remise ? plafond ?
7. **Paiement** : le devis accepté mène-t-il au paiement mobile (simulé aujourd'hui) ou à une validation par un conseiller ?

## Découpage proposé
- Lot 1 : grilles + moteur de calcul + `simulate` (testable sans stockage).
- Lot 2 : devis enregistré, statuts, expiration planifiée, endpoints mobile.
- Lot 3 : endpoints back-office, remise commerciale, conversion en souscription.
- Documentation de contrat d'API dans `docs/api-contracts/devis-en-ligne.md`, communiquée aux sessions mobile et back-office après test.
