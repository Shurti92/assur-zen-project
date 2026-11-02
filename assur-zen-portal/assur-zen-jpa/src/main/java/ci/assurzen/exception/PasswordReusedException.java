package ci.assurzen.exception;

public class PasswordReusedException extends RuntimeException {

    public PasswordReusedException() {
        super("Ce mot de passe a deja ete utilise recemment, choisissez-en un autre");
    }
}
