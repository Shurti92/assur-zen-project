package ci.assurzen.dto;

import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.ModePaiement;
import ci.assurzen.entities.PeriodiciteEcheance;
import ci.assurzen.entities.StatutReversementPrime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractSubscriptionRequest(
        @Size(max = 50) String policyNumber,
        LocalDate dateEffet,
        @NotBlank @Size(max = 10) String duree,
        LocalDate dateEcheance,
        @NotNull Long productId,
        Long partenaireId,
        @NotNull Long compagnieId,
        @NotNull Long premium,
        BigDecimal commissionRate,
        Long commissionAmount,
        Long partnerShare,
        ContractStatus status,
        CommissionStatus commissionStatus,
        LocalDate signedAt,
        Long salesRepId,
        String notes,
        ModePaiement modePaiement,
        Integer nombreEcheances,
        PeriodiciteEcheance periodiciteEcheances,
        Long montantDejaVerse,
        Long contratPrecedentId,
        Long commercialCommissionAmount,
        /** Prime deja versee a la compagnie par l'intermediaire (partenaire ou commercial) - optionnel.
         * null = non_reverse avec intermediaire, reverse sans (ContractsService.statutReversementParDefaut). */
        StatutReversementPrime primeReversementStatus
) {
}
