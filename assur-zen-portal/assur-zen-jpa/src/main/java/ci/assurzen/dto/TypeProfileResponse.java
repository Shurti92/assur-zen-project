package ci.assurzen.dto;

import java.time.Instant;

public record TypeProfileResponse(
        Long id,
        String libelle,
        boolean active,
        boolean declencheCreationCompte,
        Instant creation,
        Instant lastUpdate
) {
}
