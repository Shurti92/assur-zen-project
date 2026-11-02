package ci.assurzen.dto;

import java.time.Instant;
import java.util.List;

public record PartialPaymentStatusResponse(
        Long pendingBalance,
        long requestsToday,
        int maxRequestsPerDay,
        Instant nextRequestAvailableAt,
        List<PartialPaymentRequestResponse> history
) {
}
