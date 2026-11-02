package ci.assurzen.exception;

public class InvalidOtpCodeException extends RuntimeException {

    public InvalidOtpCodeException(String message) {
        super(message);
    }
}
