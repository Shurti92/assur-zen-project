package ci.assurzen.dto;

import ci.assurzen.entities.StatutCommercial;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommercialStatusRequest(
        @NotNull StatutCommercial statut,
        @Size(max = 500) String motif
) {
}
