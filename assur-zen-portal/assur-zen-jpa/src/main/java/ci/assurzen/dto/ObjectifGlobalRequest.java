package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ObjectifGlobalRequest(
        @NotNull @PositiveOrZero BigDecimal objectifGlobal
) {
}
