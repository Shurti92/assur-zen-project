package ci.assurzen.sycapay.exception;

/** Leve quand un paiement est demande pour un contrat qui a deja une transaction SycaPay active
 * (PENDING/PROCESSING/SUCCESS) ou non encore reconciliee (UNKNOWN) - garde d'idempotence (section 10
 * de la specification). Jamais levee pour un contrat en FAILED : ce cas doit passer par une nouvelle
 * tentative EXPLICITE (retry), jamais par un nouvel appel silencieux de pay(). */
public class SycaPayDuplicatePaymentException extends SycaPayPaymentException {

    public SycaPayDuplicatePaymentException(String message) {
        super(message);
    }
}
