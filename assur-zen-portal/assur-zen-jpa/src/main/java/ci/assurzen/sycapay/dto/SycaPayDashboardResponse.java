package ci.assurzen.sycapay.dto;

/** Vue synthetique du monitoring back-office (section 15 de la specification). */
public record SycaPayDashboardResponse(
        long paiementsDuJour,
        long montantPayeAujourdhui,
        long paiementsSuccess,
        long paiementsFailed,
        long paiementsPending,
        long paiementsUnknown,
        /** Mode test actif sur ce serveur (hors production) : tout virement part vers testPhone pour testAmount. */
        boolean testMode,
        String testPhone,
        Long testAmount
) {
}
