package ci.assurzen.dto;

import ci.assurzen.entities.SinistreStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SinistreSuiviRequest(
        @NotNull SinistreStatus statut,
        @Size(max = 2000) String commentaire,
        Long montantIndemnise
) {
}
