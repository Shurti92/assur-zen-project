package ci.assurzen.dto;

import ci.assurzen.entities.StatutCommercialPerformance;

import java.math.BigDecimal;

/**
 * Dashboard personnel "Mon activite" (§34). Le bonus est toujours qualifie d'ESTIME
 * (bonusEstime=true) tant que la performance du mois n'est pas VALIDATED - §77 : un montant affiche
 * au commercial ne doit jamais etre presente comme acquis avant validation.
 */
public record CommercialDashboardResponse(
        Long moisComptableId,
        String moisLibelle,
        BigDecimal objectiveAmount,
        BigDecimal realizedAmount,
        BigDecimal achievementRate,
        BigDecimal resteARealiser,
        BigDecimal bonusPotentiel,
        boolean bonusEstime,
        long nombreContrats,
        StatutCommercialPerformance statut
) {
}
