package ci.assurzen.dto;

import ci.assurzen.entities.ReclamationCanal;
import ci.assurzen.entities.ReclamationPriorite;
import ci.assurzen.entities.ReclamationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReclamationRequest(
        @NotNull Long insuredId,
        Long contratId,
        Long produitId,
        Long sinistreId,
        @NotNull ReclamationCanal canal,
        @NotNull ReclamationType type,
        ReclamationPriorite priorite,
        @NotBlank @Size(max = 4000) String description
) {
}
