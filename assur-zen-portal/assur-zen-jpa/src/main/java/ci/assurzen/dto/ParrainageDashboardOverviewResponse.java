package ci.assurzen.dto;

public record ParrainageDashboardOverviewResponse(
        long totalParrainages,
        long parrainagesEligibles,
        long parrainagesConvertis,
        double tauxConversion,
        long recompensesAttribuees,
        long montantTotalRecompenses,
        long parrainsActifs
) {
}
