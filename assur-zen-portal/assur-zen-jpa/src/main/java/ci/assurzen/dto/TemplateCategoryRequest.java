package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TemplateCategoryRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 100) String libelle,
        @Size(max = 2000) String description,
        Boolean active
) {
}
