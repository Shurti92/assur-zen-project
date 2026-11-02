package ci.assurzen.dto;

import java.time.Instant;

public record TraductionResponse(
        Long id,
        String cle,
        String scope,
        String langueCode,
        String valeur,
        Instant lastUpdate
) {
}
