package ci.assurzen.dto;

import ci.assurzen.entities.StatutCommercialPerformance;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommercialPerformanceStatusRequest(
        @NotNull StatutCommercialPerformance statut,
        @Size(max = 500) String motif
) {
}
