package ci.assurzen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PartialPaymentRequestCreateRequest(
        @NotNull
        @Positive
        Long amount
) {
}
