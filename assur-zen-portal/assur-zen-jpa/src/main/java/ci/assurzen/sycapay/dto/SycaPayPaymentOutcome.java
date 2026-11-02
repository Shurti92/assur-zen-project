package ci.assurzen.sycapay.dto;

import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;

/**
 * Resultat d'une tentative de paiement, retourne par SycaPayPaymentService a l'appelant
 * (CommercialCommissionService). status=null + rejected=true signifie qu'aucune tentative n'a ete
 * faite (contrat deja en cours de traitement ou deja paye - garde d'idempotence declenchee AVANT tout
 * appel reseau).
 */
public record SycaPayPaymentOutcome(
        boolean rejected,
        SycaPayTransactionStatus status,
        Long transactionId,
        String transactionReference,
        String message
) {
    public static SycaPayPaymentOutcome rejected(String message) {
        return new SycaPayPaymentOutcome(true, null, null, null, message);
    }

    public static SycaPayPaymentOutcome of(SycaPayTransactionStatus status, Long transactionId, String transactionReference, String message) {
        return new SycaPayPaymentOutcome(false, status, transactionId, transactionReference, message);
    }

    public boolean isSuccess() {
        return !rejected && status == SycaPayTransactionStatus.SUCCESS;
    }
}
