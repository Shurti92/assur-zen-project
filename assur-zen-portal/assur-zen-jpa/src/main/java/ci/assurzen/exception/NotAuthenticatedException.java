package ci.assurzen.exception;

public class NotAuthenticatedException extends RuntimeException {

    public NotAuthenticatedException() {
        super("Non authentifié");
    }
}
