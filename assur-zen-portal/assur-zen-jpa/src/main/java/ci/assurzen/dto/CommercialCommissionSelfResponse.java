package ci.assurzen.dto;

import java.util.List;

/** Vue self-service de la commission commerciale (§ "Commission commerciale interne", 2026-09-08) -
 * resume + detail contrat par contrat en un seul appel, borne au commercial connecte (pas de parametre
 * d'id cote client, meme mecanisme que CommercialDashboardResponse). */
public record CommercialCommissionSelfResponse(
        long pendingContractsCount,
        long paidContractsCount,
        long totalPending,
        long totalPaid,
        List<ContractCommercialCommissionResponse> contracts
) {
}
