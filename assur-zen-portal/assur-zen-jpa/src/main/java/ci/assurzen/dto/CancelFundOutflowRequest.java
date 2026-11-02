package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelFundOutflowRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
