package ci.assurzen.exception;

/** Erreur metier de l'API mobile : code stable (CLAIM_NOT_ALLOWED, FILE_TOO_LARGE...), statut HTTP et champ en cause. */
public class MobileApiException extends RuntimeException {

    private final int httpStatus;
    private final String code;
    private final String field;

    public MobileApiException(int httpStatus, String code, String message, String field) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.field = field;
    }

    public MobileApiException(int httpStatus, String code, String message) {
        this(httpStatus, code, message, null);
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
}
