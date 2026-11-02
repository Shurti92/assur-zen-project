package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LangueRequest(
        @NotBlank @Size(max = 10) String code,
        @NotBlank @Size(max = 100) String libelle,
        Boolean active
) {
}
