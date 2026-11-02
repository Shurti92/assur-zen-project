package ci.assurzen.exception;

public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("Compte désactivé");
    }
}
