package ci.assurzen.dto;

import java.time.Instant;

/** Erreur 429 avec delai avant la prochaine tentative (quota de codes SMS). */
public record RetryAfterErrorResponse(String code, String message, Instant timestamp, long retryAfterSeconds) {
}
