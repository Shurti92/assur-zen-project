package ci.assurzen.dto;

import ci.assurzen.entities.FundOutflowPriority;
import ci.assurzen.entities.RepaymentFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Memes champs loan* que CreateFundOutflowRequest - permet de completer les conditions du pret
 * (laissees optionnelles a la creation) tant que la sortie reste au statut DRAFT. */
public record UpdateFundOutflowRequest(
        @NotNull Long outflowTypeId,
        @Size(max = 30) String beneficiaryType,
        Long beneficiaryId,
        @Size(max = 150) String beneficiaryLabel,
        @NotNull @Positive Long amount,
        @Size(max = 10) String currency,
        @Size(max = 4000) String description,
        @NotBlank String reason,
        FundOutflowPriority priority,
        LocalDate dueDate,
        BigDecimal loanInterestRate,
        LocalDate loanStartDate,
        LocalDate loanExpectedEndDate,
        RepaymentFrequency loanRepaymentFrequency,
        Integer loanNumberOfInstallments
) {
}
