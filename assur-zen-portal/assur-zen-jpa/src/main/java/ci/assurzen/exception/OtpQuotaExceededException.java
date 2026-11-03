package ci.assurzen.exception;

/** Plus de 3 demandes de code par heure pour un meme numero (mobile, 2026-11-02). */
public class OtpQuotaExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public OtpQuotaExceededException(long retryAfterSeconds) {
        super("Trop de demandes de code pour ce numéro. Réessayez dans " + Math.max(1, (retryAfterSeconds + 59) / 60) + " minute(s).");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
