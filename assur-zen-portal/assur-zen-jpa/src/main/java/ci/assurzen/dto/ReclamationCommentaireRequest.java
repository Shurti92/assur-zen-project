package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReclamationCommentaireRequest(
        @NotBlank @Size(max = 4000) String contenu
) {
}
