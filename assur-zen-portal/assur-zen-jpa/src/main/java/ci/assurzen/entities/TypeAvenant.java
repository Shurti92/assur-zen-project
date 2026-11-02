package ci.assurzen.entities;

/**
 * Taxonomie complete des types d'avenant sur un contrat signe (classification metier fournie le
 * 2026-08-23, pas une invention technique). Chaque valeur porte sa CategorieAvenant pour eviter toute
 * combinaison invalide type/categorie a la saisie.
 */
public enum TypeAvenant {

    // AVENANT_ADMINISTRATIF - modifient les informations du contrat ou des parties, sans toucher au
    // risque ni aux garanties.
    MODIFICATION_ASSURE(CategorieAvenant.ADMINISTRATIF),
    MODIFICATION_SOUSCRIPTEUR(CategorieAvenant.ADMINISTRATIF),
    MODIFICATION_COORDONNEES(CategorieAvenant.ADMINISTRATIF),
    MODIFICATION_ADRESSE(CategorieAvenant.ADMINISTRATIF),
    MODIFICATION_BENEFICIAIRE(CategorieAvenant.ADMINISTRATIF),
    CORRECTION_INFORMATION(CategorieAvenant.ADMINISTRATIF),
    CHANGEMENT_INTERMEDIAIRE(CategorieAvenant.ADMINISTRATIF),
    CHANGEMENT_MODE_PAIEMENT(CategorieAvenant.ADMINISTRATIF),
    CHANGEMENT_FREQUENCE_PAIEMENT(CategorieAvenant.ADMINISTRATIF),

    // AVENANT_TECHNIQUE - modifient le risque, le bien assure ou les garanties du contrat.
    MODIFICATION_RISQUE(CategorieAvenant.TECHNIQUE),
    MODIFICATION_BIEN_ASSURE(CategorieAvenant.TECHNIQUE),
    REMPLACEMENT_BIEN(CategorieAvenant.TECHNIQUE),
    AJOUT_BIEN(CategorieAvenant.TECHNIQUE),
    RETRAIT_BIEN(CategorieAvenant.TECHNIQUE),
    AJOUT_GARANTIE(CategorieAvenant.TECHNIQUE),
    SUPPRESSION_GARANTIE(CategorieAvenant.TECHNIQUE),
    MODIFICATION_GARANTIE(CategorieAvenant.TECHNIQUE),
    MODIFICATION_FRANCHISE(CategorieAvenant.TECHNIQUE),
    MODIFICATION_PLAFOND(CategorieAvenant.TECHNIQUE),
    MODIFICATION_USAGE(CategorieAvenant.TECHNIQUE),
    MODIFICATION_ZONE(CategorieAvenant.TECHNIQUE),
    MODIFICATION_CONDUCTEUR(CategorieAvenant.TECHNIQUE),
    MODIFICATION_CARACTERISTIQUE(CategorieAvenant.TECHNIQUE),
    EXTENSION_TERRITORIALE(CategorieAvenant.TECHNIQUE),
    REDUCTION_TERRITORIALE(CategorieAvenant.TECHNIQUE),

    // AVENANT_FINANCIER - concernent la prime, les taxes, les commissions, les remises ou tout
    // mouvement financier lie au contrat.
    MODIFICATION_PRIME(CategorieAvenant.FINANCIER),
    SURPRIME(CategorieAvenant.FINANCIER),
    REMISE(CategorieAvenant.FINANCIER),
    ANNULATION_REMISE(CategorieAvenant.FINANCIER),
    PRIME_COMPLEMENTAIRE(CategorieAvenant.FINANCIER),
    REDUCTION_PRIME(CategorieAvenant.FINANCIER),
    REMBOURSEMENT_PRIME(CategorieAvenant.FINANCIER),
    REGULARISATION_PRIME(CategorieAvenant.FINANCIER),
    MODIFICATION_TAXE(CategorieAvenant.FINANCIER),
    REGULARISATION_FINANCIERE(CategorieAvenant.FINANCIER),
    ANNULATION_OPERATION_FINANCIERE(CategorieAvenant.FINANCIER),
    TRANSFERT_CREDIT(CategorieAvenant.FINANCIER);

    private final CategorieAvenant categorie;

    TypeAvenant(CategorieAvenant categorie) {
        this.categorie = categorie;
    }

    public CategorieAvenant getCategorie() {
        return categorie;
    }
}
