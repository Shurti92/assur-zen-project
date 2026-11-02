package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FraisLivraisonZoneRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 100) String libelle,
        @NotNull @PositiveOrZero BigDecimal montant
) {
}
