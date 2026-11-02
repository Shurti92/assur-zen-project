package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReplaceVehicleRequest(
        @NotNull Long oldVehiculeId,
        @NotNull Long newVehiculeId,
        @Size(max = 500) String reason
) {
}
