package ci.assurzen.dto;

import ci.assurzen.entities.GedConfidentialityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGedDocumentRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String description,
        Long categoryId,
        GedConfidentialityLevel confidentialityLevel
) {
}
