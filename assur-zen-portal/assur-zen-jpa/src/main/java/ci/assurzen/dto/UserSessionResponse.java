package ci.assurzen.dto;

import java.time.Instant;

public record UserSessionResponse(
        Long id,
        Instant createdAt,
        boolean revoked
) {
}
