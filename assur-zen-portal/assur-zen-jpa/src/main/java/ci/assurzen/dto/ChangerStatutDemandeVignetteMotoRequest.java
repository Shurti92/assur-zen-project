package ci.assurzen.dto;

import ci.assurzen.entities.StatutDemandeVignetteMoto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangerStatutDemandeVignetteMotoRequest(
        @NotNull StatutDemandeVignetteMoto statut,
        @Size(max = 500) String motif,
        @Size(max = 50) String referenceOperation
) {
}
