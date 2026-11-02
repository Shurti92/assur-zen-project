package ci.assurzen.exception;

public class OtpResendTooSoonException extends RuntimeException {

    private final long remainingSeconds;

    public OtpResendTooSoonException(long remainingSeconds) {
        super("Veuillez patienter " + remainingSeconds + " seconde(s) avant de redemander un code");
        this.remainingSeconds = remainingSeconds;
    }

    /** Secondes avant de pouvoir redemander un code - expose dans le 429 (2026-10-05). */
    public long getRemainingSeconds() {
        return remainingSeconds;
    }
}
