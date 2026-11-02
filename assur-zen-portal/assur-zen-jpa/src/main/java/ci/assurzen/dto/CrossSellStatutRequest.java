package ci.assurzen.dto;

import ci.assurzen.entities.CrossSellStatut;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrossSellStatutRequest(
        @NotNull CrossSellStatut statut,
        @Size(max = 4000) String motif
) {
}
