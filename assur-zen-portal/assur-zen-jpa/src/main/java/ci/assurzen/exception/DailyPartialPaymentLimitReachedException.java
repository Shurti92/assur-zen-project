package ci.assurzen.exception;

import java.time.Instant;

public class DailyPartialPaymentLimitReachedException extends RuntimeException {

    private final Instant nextAvailableAt;

    public DailyPartialPaymentLimitReachedException(int maxRequestsPerDay, Instant nextAvailableAt) {
        super("Limite quotidienne de " + maxRequestsPerDay + " demandes atteinte");
        this.nextAvailableAt = nextAvailableAt;
    }

    public Instant getNextAvailableAt() {
        return nextAvailableAt;
    }
}
