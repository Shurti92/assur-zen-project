package ci.assurzen.entities;

/**
 * Ensemble contraint de champs interrogeables par les regles de segment (plutot que du texte
 * libre arbitraire) : type-safety + surface d'attaque nulle, au prix de devoir etendre cet enum
 * pour exposer un nouveau champ metier au moteur de segmentation.
 */
public enum SegmentRuleField {
    PRODUIT,
    COMPAGNIE,
    PRIME,
    DATE_ECHEANCE,
    DATE_EFFET,
    STATUT_CONTRAT,
    MODE_PAIEMENT,
    VILLE_CLIENT,
    TYPE_CLIENT,
    NB_SINISTRES,
    NB_CONTRATS,
    PALIER_FIDELITE
}
