package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;

public record ParrainageInscriptionRequest(
        @NotNull Long filleulInsuredId
) {
}
