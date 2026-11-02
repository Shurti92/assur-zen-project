package ci.assurzen.dto;

import ci.assurzen.entities.ReclamationPriorite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CrossSellRuleRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        Long produitDeclencheurId,
        @NotNull Long produitRecommandeId,
        ReclamationPriorite priorite,
        @NotNull @Min(0) @Max(100) Integer poidsScore,
        Integer ordre,
        LocalDate dateDebut,
        LocalDate dateFin,
        Boolean actif
) {
}
