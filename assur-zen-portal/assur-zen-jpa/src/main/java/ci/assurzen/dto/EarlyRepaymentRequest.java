package ci.assurzen.dto;

import ci.assurzen.entities.DisbursementMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EarlyRepaymentRequest(
        @NotNull LocalDate paymentDate,
        @NotNull DisbursementMethod paymentMethod,
        @Size(max = 100) String paymentReference,
        @Size(max = 500) String comment
) {
}
