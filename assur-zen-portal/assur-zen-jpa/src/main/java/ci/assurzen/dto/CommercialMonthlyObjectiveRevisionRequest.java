package ci.assurzen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Revision d'un objectif mensuel existant - motif obligatoire (§32/§33), trace via l'audit fonctionnel. */
public record CommercialMonthlyObjectiveRevisionRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal targetAmount,
        Integer targetCount,
        @NotBlank @Size(max = 500) String motif
) {
}
