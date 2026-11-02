package ci.assurzen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProduitCommissionCommercialRequest(
        @NotNull Long produitId,
        @NotNull @DecimalMin(value = "0.0000") @Digits(integer = 10, fraction = 4) BigDecimal tauxCommission,
        @NotBlank @Size(max = 10) String libTauxCommission,
        Boolean active
) {
}
