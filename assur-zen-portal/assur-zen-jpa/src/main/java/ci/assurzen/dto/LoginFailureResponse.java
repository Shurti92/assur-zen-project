package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "LoginFailure")
public record LoginFailureResponse(
        String error,
        String message,
        Instant timestamp,
        Integer remainingAttempts,
        /** Meme valeur que `error` : cle unifiee `code` (2026-11-02), `error` conservee pour compatibilite. */
        String code
) {
    public LoginFailureResponse(String error, String message, Instant timestamp, Integer remainingAttempts) {
        this(error, message, timestamp, remainingAttempts, error);
    }
}
