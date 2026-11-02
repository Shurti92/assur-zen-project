package ci.assurzen.dto;

import java.time.Instant;

public record FundOutflowTypeResponse(
        Long id,
        String uuid,
        String code,
        String nom,
        String description,
        Integer ordre,
        boolean actif,
        boolean systeme,
        Instant createdAt,
        Instant updatedAt
) {
}
