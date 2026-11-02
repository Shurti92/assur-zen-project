package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferVehicleRequest(
        @NotNull Long destinationFleetId,
        @Size(max = 500) String reason
) {
}
