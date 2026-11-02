package ci.assurzen.dto;

import ci.assurzen.entities.CommercialObjectiveType;

import java.math.BigDecimal;
import java.time.Instant;

public record CommercialObjectiveResponse(
        Long id,
        Long commercialId,
        Long exerciceComptableId,
        String exerciceLibelle,
        CommercialObjectiveType objectiveType,
        BigDecimal targetAmount,
        Integer targetCount,
        Instant createdAt,
        Instant updatedAt
) {
}
