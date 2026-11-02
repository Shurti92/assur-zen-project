package ci.assurzen.events;

/** Publie quand une reconciliation (getStatusByOrderID) confirme qu'un virement SycaPay est reussi
 * (2026-09-23) : permet d'enregistrer apres coup le paiement de la commission (partenaire OU
 * commerciale) dont le virement etait reste PROCESSING/UNKNOWN. confirmePar = username de l'admin
 * qui a reconcilie (le traitement est asynchrone, hors de son SecurityContext). */
public record SycaPayPaymentConfirmedEvent(Long sycapayTransactionId, String confirmePar) {
}
