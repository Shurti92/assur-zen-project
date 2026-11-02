package ci.assurzen.dto;

import ci.assurzen.entities.TypeCompagnie;

import java.time.Instant;

public record CompagnieResponse(
        Long id,
        String codeCompagnie,
        String nom,
        boolean active,
        TypeCompagnie type,
        Instant creation,
        Instant lastUpdate
) {
}
