package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PalierFideliteRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 100) String libelle,
        @NotNull Long seuilPoints,
        String avantages,
        @Size(max = 20) String couleur,
        @Size(max = 50) String icone,
        Boolean estVip,
        Boolean active
) {
}
