package ci.assurzen.dto;

import java.time.Instant;

public record IpLoginAttemptResponse(
        String ipAddress,
        int failedAttempts,
        Instant blockedUntil,
        Instant lastAttemptAt
) {
}
