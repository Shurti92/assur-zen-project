package ci.assurzen.dto;

import ci.assurzen.entities.FundOutflowStatus;
import ci.assurzen.entities.RepaymentFrequency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanResponse(
        Long id,
        String uuid,
        Long fundOutflowId,
        String fundOutflowReference,
        FundOutflowStatus status,
        String loanReference,
        Long partnerId,
        String partnerCode,
        String partnerName,
        Long principalAmount,
        BigDecimal interestRate,
        Long totalAmount,
        String currency,
        LocalDate startDate,
        LocalDate expectedEndDate,
        RepaymentFrequency repaymentFrequency,
        Integer numberOfInstallments,
        Long remainingBalance,
        boolean overdue,
        Instant createdAt,
        Instant updatedAt
) {
}
