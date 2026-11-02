package ci.assurzen.dto;

import ci.assurzen.entities.CommercialObjectiveType;

import java.math.BigDecimal;
import java.time.Instant;

public record CommercialMonthlyObjectiveResponse(
        Long id,
        Long commercialId,
        Long moisComptableId,
        String moisLibelle,
        Integer moisNumero,
        CommercialObjectiveType objectiveType,
        BigDecimal targetAmount,
        Integer targetCount,
        Instant createdAt,
        Instant updatedAt
) {
}
