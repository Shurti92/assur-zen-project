package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TypeProfileRequest(
        @NotBlank @Size(max = 255) String libelle,
        Boolean active,
        Boolean declencheCreationCompte
) {
}
