package ci.assurzen.dto;

import ci.assurzen.entities.DisbursementMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateLoanRepaymentRequest(
        @NotNull @Positive Long amount,
        @NotNull LocalDate paymentDate,
        @NotNull DisbursementMethod paymentMethod,
        @Size(max = 100) String paymentReference,
        @Size(max = 500) String comment
) {
}
