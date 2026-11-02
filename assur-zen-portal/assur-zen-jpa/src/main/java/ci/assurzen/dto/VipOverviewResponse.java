package ci.assurzen.dto;

public record VipOverviewResponse(
        long totalVip,
        long totalComptes,
        Double scoreMoyenVip,
        long primeGenereeVip,
        long recompensesDistribuees,
        long pointsRecompensesDistribues,
        long entreesVipMoisCourant,
        long sortiesVipMoisCourant
) {
}
