package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrossSellParameterRequest(
        @NotBlank @Size(max = 50) String cle,
        @NotBlank @Size(max = 200) String valeur,
        @Size(max = 300) String description
) {
}
