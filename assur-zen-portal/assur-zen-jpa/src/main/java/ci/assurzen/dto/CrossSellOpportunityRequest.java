package ci.assurzen.dto;

import ci.assurzen.entities.ReclamationPriorite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrossSellOpportunityRequest(
        @NotNull Long insuredId,
        Long contratId,
        Long produitActuelId,
        @NotNull Long produitRecommandeId,
        @NotNull @Min(0) @Max(100) Integer score,
        ReclamationPriorite priorite,
        @Size(max = 4000) String commentaire
) {
}
