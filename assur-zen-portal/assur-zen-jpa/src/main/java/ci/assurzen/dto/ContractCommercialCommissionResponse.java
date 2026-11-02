package ci.assurzen.dto;

import ci.assurzen.entities.CommissionStatus;

import java.time.LocalDate;

public record ContractCommercialCommissionResponse(
        Long contractId,
        String policyNumber,
        LocalDate signedAt,
        Long commercialCommissionAmount,
        CommissionStatus commercialCommissionStatus
) {
}
