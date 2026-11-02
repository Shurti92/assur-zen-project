package ci.assurzen.dto;

import ci.assurzen.entities.FundOutflowPriority;
import ci.assurzen.entities.RepaymentFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Champs loan* obligatoires uniquement quand outflowType resout sur le code "LOAN" (verifie cote
 * service) - les conditions du pret sont figees des la creation de la demande, pas inventees plus
 * tard au decaissement, pour que l'approbation porte sur les vraies conditions.
 */
public record CreateFundOutflowRequest(
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
