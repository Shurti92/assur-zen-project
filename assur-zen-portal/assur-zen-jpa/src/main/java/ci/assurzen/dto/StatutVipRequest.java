package ci.assurzen.dto;

import ci.assurzen.entities.StatutVip;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatutVipRequest(
        @NotNull StatutVip statut,
        @Size(max = 300) String motif
) {
}
