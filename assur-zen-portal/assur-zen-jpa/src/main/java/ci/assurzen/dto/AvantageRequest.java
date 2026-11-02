package ci.assurzen.dto;

import ci.assurzen.entities.AvantageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AvantageRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        @NotNull AvantageType type,
        Long palierId,
        BigDecimal valeur,
        Boolean active
) {
}
