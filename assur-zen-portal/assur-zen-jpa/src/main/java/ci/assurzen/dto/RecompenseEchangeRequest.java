package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;

public record RecompenseEchangeRequest(
        @NotNull Long insuredId,
        @NotNull Long recompenseId
) {
}
