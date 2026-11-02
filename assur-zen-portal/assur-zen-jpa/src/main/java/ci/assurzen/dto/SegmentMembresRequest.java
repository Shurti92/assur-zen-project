package ci.assurzen.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Remplace integralement la liste des membres d'un segment STATIC. */
public record SegmentMembresRequest(
        @NotEmpty List<Long> insuredIds
) {
}
