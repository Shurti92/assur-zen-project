package ci.assurzen.dto;

/**
 * Resultat d'une tentative de paiement du bonus mensuel (2026-10-02). Comme pour les commissions, le
 * frontend DOIT lire `sycapayStatus` plutot que supposer un succes : SUCCESS (bonus PAID), PROCESSING /
 * UNKNOWN (virement en cours ou d'issue incertaine - ne pas relancer, reconcilier), FAILED (aucun montant
 * parti, relancable), REJECTED (aucune tentative : virement deja en cours, service indisponible).
 * modePaiement = ENREGISTREMENT_MANUEL quand SycaPay est desactive (paiement fait hors application).
 */
public record CommercialBonusPaymentResponse(
        CommercialPerformanceResponse performance,
        String modePaiement,
        String sycapayStatus,
        String sycapayTransactionReference,
        String sycapayMessage
) {
}
