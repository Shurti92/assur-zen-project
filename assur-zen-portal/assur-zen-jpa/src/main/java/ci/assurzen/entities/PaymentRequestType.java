package ci.assurzen.entities;

/** Demande de paiement de commission d'un commercial interne (2026-09-25) : tout le solde disponible
 * (TOTAL) ou un montant libre choisi par le commercial (PARTIAL). */
public enum PaymentRequestType {
    TOTAL,
    PARTIAL
}
