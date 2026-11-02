package ci.assurzen.entities;

/** Statut de verification d'un document partenaire par AssurZen (2026-10-04). */
public enum StatutValidationDocument {
    /** Fourni par le partenaire, pas encore verifie (et documents anterieurs a ce statut). */
    EN_ATTENTE,
    VALIDE,
    /** Refuse avec un motif : le partenaire doit en fournir un nouveau. */
    REFUSE
}
