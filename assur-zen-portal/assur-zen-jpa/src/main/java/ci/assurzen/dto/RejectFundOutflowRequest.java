package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectFundOutflowRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
