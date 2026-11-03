package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "AccountLocked")
public record AccountLockedResponse(
        String error,
        String message,
        Instant timestamp,
        Instant lockedUntil,
        Long remainingSeconds,
        /** Meme valeur que `error` : cle unifiee `code` (2026-11-02), `error` conservee pour compatibilite. */
        String code
) {
    public AccountLockedResponse(String error, String message, Instant timestamp, Instant lockedUntil, Long remainingSeconds) {
        this(error, message, timestamp, lockedUntil, remainingSeconds, error);
    }
}
