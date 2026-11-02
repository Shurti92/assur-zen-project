package ci.assurzen.dto;

import java.time.Instant;

public record CodeParrainageResponse(
        Long id,
        String code,
        String lien,
        Long parrainId,
        String parrainNom,
        Long programmeId,
        String programmeNom,
        Integer nombreUtilisations,
        Integer maxUtilisations,
        Instant dateExpiration,
        boolean actif,
        Instant createdAt
) {
}
