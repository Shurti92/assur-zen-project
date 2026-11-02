package ci.assurzen.dto;

import java.time.Instant;

public record GedDocumentTypeResponse(
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
