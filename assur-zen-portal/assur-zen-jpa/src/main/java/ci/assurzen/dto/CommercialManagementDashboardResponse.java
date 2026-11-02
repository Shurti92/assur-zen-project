package ci.assurzen.dto;

import java.math.BigDecimal;

public record CommercialManagementDashboardResponse(
        Long moisComptableId,
        String moisLibelle,
        long totalCommercials,
        long activeCommercials,
        BigDecimal monthlyObjective,
        BigDecimal monthlyProduction,
        BigDecimal monthlyAchievementRate,
        long eligibleCommercials,
        long nonEligibleCommercials,
        BigDecimal totalBonus
) {
}
