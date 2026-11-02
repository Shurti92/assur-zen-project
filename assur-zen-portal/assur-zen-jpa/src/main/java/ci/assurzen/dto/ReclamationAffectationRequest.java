package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReclamationAffectationRequest(
        @NotNull Long gestionnaireId,
        @Size(max = 100) String service
) {
}
