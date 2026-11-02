package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VillePartenaireRequest(
        @NotBlank
        @Size(max = 200)
        String nomVille,

        @NotBlank
        @Size(max = 200)
        String regions,

        Boolean active
) {
}
