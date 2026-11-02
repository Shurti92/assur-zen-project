package ci.assurzen.entities;

public enum PartialPaymentRequestStatus {
    pending,
    paid,
    /** Virement SycaPay lance, resultat pas encore confirme (PENDING/PROCESSING/UNKNOWN) - 2026-09-26. */
    processing,
    /** Virement SycaPay en echec : montant toujours reserve, la demande peut etre relancee (valider). */
    failed
}
