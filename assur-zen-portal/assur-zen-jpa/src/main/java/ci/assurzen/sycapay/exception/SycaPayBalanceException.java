package ci.assurzen.sycapay.exception;

public class SycaPayBalanceException extends SycaPayException {

    public SycaPayBalanceException(String message) {
        super(message);
    }

    public SycaPayBalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
