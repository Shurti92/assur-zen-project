package ci.assurzen.exception;

public class InvalidCredentialsException extends RuntimeException {

    private final Integer remainingAttempts;

    public InvalidCredentialsException() {
        this(null);
    }

    /** remainingAttempts : nombre de tentatives restantes avant verrouillage (null si non applicable/calcule). */
    public InvalidCredentialsException(Integer remainingAttempts) {
        super("Identifiants invalides");
        this.remainingAttempts = remainingAttempts;
    }

    public Integer getRemainingAttempts() {
        return remainingAttempts;
    }
}
