package ci.assurzen.exception;

public class UnauthorizedLogoutException extends RuntimeException {

    public UnauthorizedLogoutException() {
        super("Non authentifié");
    }
}
