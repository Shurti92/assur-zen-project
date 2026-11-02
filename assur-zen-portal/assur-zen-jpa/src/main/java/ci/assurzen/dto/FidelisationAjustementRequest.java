package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FidelisationAjustementRequest(
        @NotNull Long insuredId,
        @NotNull Long points,
        @NotBlank @Size(max = 300) String motif
) {
}
