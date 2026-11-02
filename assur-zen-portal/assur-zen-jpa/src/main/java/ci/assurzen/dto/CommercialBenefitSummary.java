package ci.assurzen.dto;

import java.math.BigDecimal;

/** Agregats des avantages (transport / communication) a payer et payes pour un mois comptable (2026-11-01). */
public record CommercialBenefitSummary(
        Long moisComptableId,
        BigDecimal transportAPayerTotal,
        long transportAPayerCount,
        BigDecimal communicationAPayerTotal,
        long communicationAPayerCount,
        BigDecimal transportPayeTotal,
        long transportPayeCount,
        BigDecimal communicationPayeTotal,
        long communicationPayeCount
) {
}
