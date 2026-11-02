package ci.assurzen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record Credentials(
        @NotBlank
        @Schema(description = "Nom d'utilisateur", example = "0700000000")
        String username,

        @NotBlank
        @Schema(description = "Mot de passe", example = "monPropreMdp789")
        String password
) {
}
