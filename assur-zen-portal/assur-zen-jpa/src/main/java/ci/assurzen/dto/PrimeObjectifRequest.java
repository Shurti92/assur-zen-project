package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;

public record PrimeObjectifRequest(
        @NotNull
        Long partenaireId
) {
}
