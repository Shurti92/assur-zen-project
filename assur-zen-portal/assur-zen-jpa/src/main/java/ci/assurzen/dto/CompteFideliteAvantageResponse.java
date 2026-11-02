package ci.assurzen.dto;

import java.time.Instant;

public record CompteFideliteAvantageResponse(
        Long id,
        Long avantageId,
        String avantageNom,
        String avantageType,
        Instant dateAttribution,
        Instant dateExpiration,
        boolean actif
) {
}
