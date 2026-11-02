package ci.assurzen.dto;

import ci.assurzen.entities.ReclamationStatut;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReclamationStatutRequest(
        @NotNull ReclamationStatut statut,
        @Size(max = 4000) String commentaire
) {
}
