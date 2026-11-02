package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;

public record CrossSellAffectationRequest(
        @NotNull Long commercialId
) {
}
