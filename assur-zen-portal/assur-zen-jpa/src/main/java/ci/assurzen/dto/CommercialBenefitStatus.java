package ci.assurzen.dto;

import ci.assurzen.entities.StatutAvantageCommercial;
import ci.assurzen.entities.TypeAvantageCommercial;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Etat d'un avantage (prime de transport ou indemnite de communication) d'un commercial pour un mois
 * (2026-11-01). payable = un clic sur "payer" est accepte maintenant (A_PAYER, ECHEC relancable, ou virement
 * confirme non encore enregistre). EN_COURS (PENDING/PROCESSING/UNKNOWN) : ne pas relancer.
 */
public record CommercialBenefitStatus(
        TypeAvantageCommercial type,
        /** Montant de la fiche commercial (fige une fois paye). */
        BigDecimal amount,
        boolean payable,
        StatutAvantageCommercial statut,
        String sycapayStatus,
        Long sycapayTransactionId,
        String sycapayTransactionReference,
        String sycapayMessage,
        Instant paidAt,
        String modePaiement,
        String paymentReference,
        /** Identifiant du paiement (null tant qu'aucune tentative n'a eu lieu). */
        Long paymentId
) {
}
