package ci.assurzen.dto;

import java.time.Instant;

public record DailyLimitReachedErrorResponse(
        String code,
        String message,
        Instant nextAvailableAt
) {
}
