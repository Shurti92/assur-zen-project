package ci.assurzen.dto;

import java.time.Instant;

public record ProductTypeResponse(
        Long id,
        String uuid,
        String code,
        String nom,
        String description,
        String icone,
        String couleur,
        Integer ordre,
        boolean actif,
        boolean systeme,
        long nombreProduits,
        Instant createdAt,
        Instant updatedAt
) {
}
