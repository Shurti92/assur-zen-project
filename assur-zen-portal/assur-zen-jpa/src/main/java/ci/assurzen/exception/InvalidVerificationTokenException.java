package ci.assurzen.exception;

public class InvalidVerificationTokenException extends RuntimeException {
    public InvalidVerificationTokenException() {
        super("La vérification du numéro a expiré ou est invalide. Recommencez la vérification par SMS.");
    }
}
