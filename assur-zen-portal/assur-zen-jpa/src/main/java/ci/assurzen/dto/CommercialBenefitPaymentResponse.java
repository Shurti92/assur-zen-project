package ci.assurzen.dto;

/**
 * Resultat d'une tentative de paiement de la prime de transport / indemnite de communication
 * (2026-11-01). Meme contrat que CommercialBonusPaymentResponse : lire `sycapayStatus` (SUCCESS, PROCESSING,
 * UNKNOWN, FAILED, REJECTED ; null en enregistrement manuel) plutot que supposer un succes.
 */
public record CommercialBenefitPaymentResponse(
        CommercialBenefitStatus avantage,
        String modePaiement,
        String sycapayStatus,
        String sycapayTransactionReference,
        String sycapayMessage
) {
}
