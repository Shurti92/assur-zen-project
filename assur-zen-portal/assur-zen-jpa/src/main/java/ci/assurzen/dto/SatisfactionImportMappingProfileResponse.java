package ci.assurzen.dto;

import java.time.Instant;

public record SatisfactionImportMappingProfileResponse(
        Long id,
        String uuid,
        String code,
        String nom,
        String description,
        boolean active,
        long nbMappings,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
}
