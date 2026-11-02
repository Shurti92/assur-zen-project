package ci.assurzen.dto;

import ci.assurzen.entities.BeneficiaireParrainage;
import ci.assurzen.entities.TypeRecompenseParrainage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegleRecompenseParrainageRequest(
        @NotNull Long programmeId,
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        @NotNull TypeRecompenseParrainage typeRecompense,
        @NotNull BeneficiaireParrainage beneficiaire,
        Long montantFixe,
        Double pourcentage,
        Integer points,
        Long primeMinimum,
        Long plafondRecompense,
        Integer priorite,
        Boolean actif
) {
}
