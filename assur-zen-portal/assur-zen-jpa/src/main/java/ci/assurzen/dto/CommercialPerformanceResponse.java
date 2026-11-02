package ci.assurzen.dto;

import ci.assurzen.entities.StatutCommercialPerformance;

import java.math.BigDecimal;
import java.time.Instant;

public record CommercialPerformanceResponse(
        Long id,
        Long commercialId,
        Long moisComptableId,
        String moisLibelle,
        Integer moisNumero,
        BigDecimal objectiveAmount,
        BigDecimal realizedAmount,
        BigDecimal achievementRate,
        BigDecimal bonusRateSnapshot,
        BigDecimal bonusAmount,
        boolean eligible,
        BigDecimal baseSalarySnapshot,
        BigDecimal transportBenefitSnapshot,
        BigDecimal phoneAllowanceSnapshot,
        BigDecimal totalRemuneration,
        StatutCommercialPerformance statut,
        Instant calculatedAt,
        Instant validatedAt,
        Long validatedById,
        Instant paidAt,
        Long paidById,
        Boolean notificationEnvoyee,
        // ---- Ajouts 2026-10-02 (detail du calcul + paiement reel du bonus) ----
        /** true tant que la performance n'est pas VALIDATED/PAID/CLOSED : valeurs recalculees en direct, bonus estime. */
        boolean estime,
        /** Seuil d'eligibilite applique (COMMERCIAL_BONUS_MIN_ACHIEVEMENT_RATE, en %) - detail du calcul. */
        BigDecimal seuilEligibilite,
        /** Bonus REELLEMENT payable maintenant : VALIDATED + eligible + montant > 0 + aucun virement en cours. */
        boolean payable,
        /** VIREMENT_SYCAPAY ou ENREGISTREMENT_MANUEL - null tant que le bonus n'est pas paye. */
        String modePaiement,
        String paymentReference,
        /** Dernier virement SycaPay tente pour ce bonus (null si aucun). */
        Long sycapayTransactionId,
        String sycapayStatus,
        String sycapayTransactionReference,
        String sycapayMessage,
        Instant sycapayRequestDate,
        /** Identite du commercial (historique des paiements de bonus tous commerciaux confondus). */
        String commercialNom,
        String employeeCode
) {
}
