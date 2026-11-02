package ci.assurzen.dto;

import ci.assurzen.entities.FundOutflowPriority;
import ci.assurzen.entities.FundOutflowStatus;
import ci.assurzen.entities.RepaymentFrequency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FundOutflowResponse(
        Long id,
        String uuid,
        String reference,
        Long outflowTypeId,
        String outflowTypeCode,
        String outflowTypeNom,
        String beneficiaryType,
        Long beneficiaryId,
        String beneficiaryLabel,
        Long amount,
        String currency,
        String description,
        String reason,
        FundOutflowStatus status,
        FundOutflowPriority priority,
        LocalDate dueDate,
        String rejectionReason,
        Long loanId,
        BigDecimal loanInterestRate,
        LocalDate loanStartDate,
        LocalDate loanExpectedEndDate,
        RepaymentFrequency loanRepaymentFrequency,
        Integer loanNumberOfInstallments,
        Instant submittedAt, String submittedBy,
        Instant approvedAt, String approvedBy,
        Instant authorizedAt, String authorizedBy,
        Instant disbursedAt, String disbursedBy,
        Instant closedAt, String closedBy,
        Instant createdAt, String createdBy,
        Instant updatedAt, String updatedBy
) {
}
