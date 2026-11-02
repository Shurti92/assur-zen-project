package ci.assurzen.dto;

import ci.assurzen.entities.TypeCompagnie;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompagnieRequest(
        @Size(max = 20) String codeCompagnie,
        @NotBlank @Size(max = 200) String nom,
        Boolean active,
        TypeCompagnie type
) {
}
