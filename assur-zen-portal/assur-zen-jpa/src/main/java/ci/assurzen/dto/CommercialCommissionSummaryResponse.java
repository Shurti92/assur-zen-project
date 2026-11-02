package ci.assurzen.dto;

public record CommercialCommissionSummaryResponse(
        Long commercialId,
        String employeeCode,
        String nomComplet,
        long pendingContractsCount,
        long paidContractsCount,
        long totalPending,
        long totalPaid
) {
}
