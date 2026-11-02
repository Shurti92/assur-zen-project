package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TraductionRequest(
        @NotBlank @Size(max = 200) String cle,
        @NotBlank @Size(max = 50) String scope,
        @NotBlank @Size(max = 10) String langueCode,
        @NotBlank String valeur
) {
}
