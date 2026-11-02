package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductTypeRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        @Size(max = 50) String icone,
        @Size(max = 20) String couleur,
        Integer ordre,
        Boolean actif
) {
}
