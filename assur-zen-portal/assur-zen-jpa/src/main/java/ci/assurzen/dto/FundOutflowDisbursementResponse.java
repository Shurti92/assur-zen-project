package ci.assurzen.dto;

import ci.assurzen.entities.DisbursementMethod;
import ci.assurzen.entities.DisbursementStatus;

import java.time.Instant;
import java.time.LocalDate;

public record FundOutflowDisbursementResponse(
        Long id,
        Long fundOutflowId,
        String reference,
        Long amount,
        String currency,
        DisbursementMethod method,
        String paymentReference,
        DisbursementStatus status,
        String processedBy,
        LocalDate disbursementDate,
        Instant createdAt
) {
}
