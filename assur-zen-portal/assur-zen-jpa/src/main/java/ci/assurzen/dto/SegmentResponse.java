package ci.assurzen.dto;

import ci.assurzen.entities.SegmentType;

import java.time.Instant;

public record SegmentResponse(
        Long id,
        String uuid,
        String code,
        String nom,
        String description,
        SegmentType type,
        Integer priorite,
        String couleur,
        boolean active,
        long nbRegles,
        long nbMembres,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
}
