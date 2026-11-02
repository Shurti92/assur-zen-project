package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SatisfactionImportMappingProfileRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 2000) String description,
        Boolean active
) {
}
