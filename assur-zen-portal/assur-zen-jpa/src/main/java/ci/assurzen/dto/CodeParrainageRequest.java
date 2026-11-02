package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CodeParrainageRequest(
        @NotNull Long parrainInsuredId,
        @NotNull Long programmeId,
        Integer maxUtilisations,
        Instant dateExpiration
) {
}
