package ci.assurzen.exception;

public class PasswordChangeRequiredException extends RuntimeException {

    public PasswordChangeRequiredException() {
        super("Le mot de passe doit être changé avant de continuer");
    }
}
