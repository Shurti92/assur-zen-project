package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;

public record TraductionUpdateRequest(
        @NotBlank String valeur
) {
}
