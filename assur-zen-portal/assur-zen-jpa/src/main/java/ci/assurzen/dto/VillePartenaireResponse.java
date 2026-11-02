package ci.assurzen.dto;

import java.time.Instant;

public record VillePartenaireResponse(
        Long id,
        String nomVille,
        String regions,
        boolean active,
        Instant creation,
        Instant lastUpdate
) {
}
