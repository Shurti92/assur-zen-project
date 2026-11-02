package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevoquerLienSuiviRequest(
        @NotBlank @Size(max = 255) String motif
) {
}
