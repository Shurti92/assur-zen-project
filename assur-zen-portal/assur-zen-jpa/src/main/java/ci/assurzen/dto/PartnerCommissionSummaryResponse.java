package ci.assurzen.dto;

import ci.assurzen.entities.PartenaireType;

public record PartnerCommissionSummaryResponse(
        Long partenaireId,
        String codePartenaire,
        String nom,
        String prenoms,
        PartenaireType type,
        long pendingContractsCount,
        long paidContractsCount,
        long totalPending,
        long totalPaid
) {
}
