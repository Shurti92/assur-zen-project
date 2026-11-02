package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttachContractRequest(
        @NotNull Long contractId,
        @NotNull Long vehiculeId,
        @Size(max = 500) String reason
) {
}
