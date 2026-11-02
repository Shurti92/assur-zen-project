package ci.assurzen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnqueteReponseRequest(
        @NotNull @Min(0) @Max(10) Integer scoreNps,
        @Size(max = 4000) String commentaire
) {
}
