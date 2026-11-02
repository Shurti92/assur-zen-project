package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RemoveVehicleRequest(
        @NotNull Long vehiculeId,
        @Size(max = 500) String reason
) {
}
