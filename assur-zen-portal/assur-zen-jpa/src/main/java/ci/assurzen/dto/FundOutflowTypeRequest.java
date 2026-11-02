package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FundOutflowTypeRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        Integer ordre,
        Boolean actif
) {
}
