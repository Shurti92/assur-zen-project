package ci.assurzen.exception;

/** Erreur metier d'un paiement mobile : code stable (PAYABLE_NOT_FOUND, AMOUNT_MISMATCH...) + statut HTTP. */
public class MobilePaymentException extends RuntimeException {

    private final int httpStatus;
    private final String code;
    private final String field;
    private final Long retryAfterSeconds;

    public MobilePaymentException(int httpStatus, String code, String message, String field, Long retryAfterSeconds) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.field = field;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public MobilePaymentException(int httpStatus, String code, String message) {
        this(httpStatus, code, message, null, null);
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
