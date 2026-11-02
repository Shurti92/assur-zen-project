package ci.assurzen.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Ligne de l'historique unifie des paiements commerciaux (2026-11-01) : type = BONUS, TRANSPORT ou COMMUNICATION. */
public record CommercialPaymentHistoryEntry(
        String type,
        Long paymentId,
        Long commercialId,
        String commercialNom,
        String employeeCode,
        Long moisComptableId,
        String moisLibelle,
        BigDecimal amount,
        /** A_PAYER, EN_COURS, PAYE ou ECHEC. */
        String statut,
        String modePaiement,
        String paymentReference,
        Instant paidAt,
        String sycapayStatus,
        Long sycapayTransactionId,
        String sycapayTransactionReference,
        String sycapayMessage
) {
}
