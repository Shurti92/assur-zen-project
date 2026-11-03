package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "Error")
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        /** Champ en cause pour VALIDATION_FAILED et les erreurs de saisie (2026-11-02), absent sinon. */
        String field
) {
    public ErrorResponse(String code, String message, Instant timestamp) {
        this(code, message, timestamp, null);
    }
}
