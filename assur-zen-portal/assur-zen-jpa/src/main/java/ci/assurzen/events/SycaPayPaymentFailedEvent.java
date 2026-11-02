package ci.assurzen.events;

/** Publie quand une reconciliation SycaPay conclut a l'ECHEC d'un virement (2026-09-26) : la demande de
 * paiement eventuellement liee passe en echec et redevient relancable. Traite apres commit. */
public record SycaPayPaymentFailedEvent(Long sycapayTransactionId) {
}
