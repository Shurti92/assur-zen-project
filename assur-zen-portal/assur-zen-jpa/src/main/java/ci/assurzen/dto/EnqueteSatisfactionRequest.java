package ci.assurzen.dto;

import ci.assurzen.entities.EnqueteContexte;
import jakarta.validation.constraints.NotNull;

public record EnqueteSatisfactionRequest(
        @NotNull EnqueteContexte contexte,
        @NotNull Long insuredId,
        Long contratId,
        Long sinistreId
) {
}
