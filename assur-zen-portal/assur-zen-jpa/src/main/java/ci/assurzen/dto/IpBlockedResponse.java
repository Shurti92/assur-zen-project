package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "IpBlocked")
public record IpBlockedResponse(
        String error,
        String message,
        Instant timestamp,
        Instant blockedUntil,
        Long remainingSeconds
) {
}
