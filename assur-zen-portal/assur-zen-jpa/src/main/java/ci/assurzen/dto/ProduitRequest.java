package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProduitRequest(
        @NotBlank @Size(max = 200) String nomProduit,
        Boolean active,
        Long productTypeId
) {
}
