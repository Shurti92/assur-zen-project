package ci.assurzen.dto;

import ci.assurzen.entities.StatutCommercialPerformance;

import java.math.BigDecimal;

/**
 * Element de paie d'un commercial pour un mois comptable donne (§45) : matricule, prime de base,
 * avantages, bonus, total variable - une simple projection en lecture seule de CommercialPerformance,
 * PAS un calcul de paie complet (pas de charges sociales, impots, net a payer...). Le champ `statut`
 * permet au systeme consommateur de confirmer que le bonus est definitif (VALIDATED/PAID/CLOSED) et
 * non un montant encore estime - cf. CommercialPayrollService.
 *
 * Depuis le 2026-10-02 le mois en cours est aussi expose (valeurs recalculees en direct) : `estime` = true
 * tant que la performance n'est pas definitive - un systeme de paie externe ne retient que estime = false.
 */
public record CommercialPayrollEntry(
        Long commercialId,
        String employeeCode,
        String commercialNom,
        Long moisComptableId,
        String moisLibelle,
        BigDecimal baseSalary,
        BigDecimal transportBenefitAmount,
        BigDecimal phoneAllowanceAmount,
        BigDecimal bonusAmount,
        BigDecimal totalVariable,
        StatutCommercialPerformance statut,
        // ---- Ajouts 2026-10-02 ----
        boolean estime,
        BigDecimal objectiveAmount,
        BigDecimal realizedAmount,
        BigDecimal achievementRate,
        boolean eligible,
        /** Bonus payable maintenant (cf. CommercialPerformanceResponse.payable). */
        boolean bonusPayable,
        /** Statut du dernier virement SycaPay du bonus (null si aucun). */
        String sycapayStatus,
        // ---- Ajouts 2026-10-02 (refonte de l'onglet Paie) ----
        /** Taux du bonus applique (% de l'objectif) et seuil d'eligibilite (% d'atteinte) - detail du calcul. */
        BigDecimal bonusRate,
        BigDecimal seuilEligibilite,
        String villeNom,
        /** Numero Wave du commercial, masque (ex. "07 •• •• 12") - jamais le numero complet. */
        String telephoneMasque,
        java.time.Instant paidAt,
        String modePaiement,
        Long sycapayTransactionId,
        String sycapayTransactionReference,
        // ---- Ajouts 2026-11-01 : paiement separe de la prime de transport et de l'indemnite de communication ----
        CommercialBenefitStatus transport,
        CommercialBenefitStatus communication
) {
}
