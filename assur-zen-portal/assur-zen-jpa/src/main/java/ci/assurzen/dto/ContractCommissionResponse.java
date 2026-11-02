package ci.assurzen.dto;

import ci.assurzen.entities.CommissionStatus;

import java.time.LocalDate;

public record ContractCommissionResponse(
        Long contractId,
        String policyNumber,
        LocalDate signedAt,
        Long partnerShare,
        CommissionStatus commissionStatus
) {
}
