package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReclamationClotureRequest(
        @NotBlank @Size(max = 2000) String motif
) {
}
