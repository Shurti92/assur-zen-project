package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "AccountLocked")
public record AccountLockedResponse(
        String error,
        String message,
        Instant timestamp,
        Instant lockedUntil,
        Long remainingSeconds
) {
}
