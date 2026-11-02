package ci.assurzen.dto;

import ci.assurzen.entities.DisbursementMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LoanRepaymentResponse(
        Long id,
        String reference,
        Long amount,
        String currency,
        DisbursementMethod paymentMethod,
        String paymentReference,
        LocalDate paymentDate,
        String receivedBy,
        String comment,
        List<LoanRepaymentAllocationResponse> allocations,
        Instant createdAt
) {
}
