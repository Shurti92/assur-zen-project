package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;

public record ParrainageContratRequest(
        @NotNull Long contratId
) {
}
