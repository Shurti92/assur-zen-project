package ci.assurzen.dto;

import ci.assurzen.entities.InstallmentStatus;

import java.time.Instant;
import java.time.LocalDate;

public record LoanInstallmentResponse(
        Long id,
        Integer installmentNumber,
        LocalDate dueDate,
        Long principalAmount,
        Long interestAmount,
        Long totalDue,
        Long paidAmount,
        Long remainingAmount,
        InstallmentStatus status,
        Long daysOverdue,
        Instant paidAt
) {
}
