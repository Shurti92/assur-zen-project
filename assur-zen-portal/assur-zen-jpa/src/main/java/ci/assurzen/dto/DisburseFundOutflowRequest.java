package ci.assurzen.dto;

import ci.assurzen.entities.DisbursementMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DisburseFundOutflowRequest(
        @NotNull DisbursementMethod method,
        @Size(max = 100) String paymentReference
) {
}
