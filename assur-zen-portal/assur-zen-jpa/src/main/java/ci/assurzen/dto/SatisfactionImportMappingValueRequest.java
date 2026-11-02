package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SatisfactionImportMappingValueRequest(
        @NotBlank @Size(max = 300) String valeurSource,
        @NotBlank @Size(max = 50) String valeurCible
) {
}
