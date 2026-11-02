package ci.assurzen.dto;

import ci.assurzen.entities.AvantageType;

import java.math.BigDecimal;

public record AvantageResponse(
        Long id,
        String code,
        String nom,
        String description,
        AvantageType type,
        Long palierId,
        String palierLibelle,
        BigDecimal valeur,
        boolean active
) {
}
