package ci.assurzen.sycapay.exception;

public class SycaPayAuthenticationException extends SycaPayException {

    public SycaPayAuthenticationException(String message) {
        super(message);
    }

    public SycaPayAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
