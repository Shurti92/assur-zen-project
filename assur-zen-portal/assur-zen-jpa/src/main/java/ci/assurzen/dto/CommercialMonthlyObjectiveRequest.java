package ci.assurzen.dto;

import ci.assurzen.entities.CommercialObjectiveType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CommercialMonthlyObjectiveRequest(
        @NotNull Long moisComptableId,
        @NotNull CommercialObjectiveType objectiveType,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal targetAmount,
        Integer targetCount
) {
}
