package ci.assurzen.sycapay.dto;

import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;

/** Commande metier transmise a SycaPayPaymentService (par CommercialCommissionService pour un
 * commercial, par PartenaireCommissionAutomationService pour un partenaire) - aucune notion
 * SycaPay-specifique ici (orderid/typetransfer/... restent internes au package sycapay).
 * commercialId est renseigne pour COMMERCIAL, partenaireId pour PARTENAIRE (2026-09-23). */
public record SycaPayPaymentCommand(
        Long contractId,
        SycaPayBeneficiaryType beneficiaryType,
        Long commercialId,
        Long partenaireId,
        Long amount,
        String beneficiaryName,
        String beneficiaryPhone,
        /** Virement redirige par le mode test (hors production). */
        boolean testMode,
        /** Demande de paiement payee par ce virement (contractId null dans ce cas), 2026-09-26. */
        Long partialPaymentRequestId,
        /** Bonus mensuel (CommercialPerformance) paye par ce virement (contractId null dans ce cas), 2026-10-02. */
        Long commercialPerformanceId,
        /** Prime de transport / indemnite de communication (CommercialBenefitPayment) payee par ce virement, 2026-11-01. */
        Long commercialBenefitPaymentId
) {
    /** Signature anterieure (sans avantage commercial). */
    public SycaPayPaymentCommand(Long contractId, SycaPayBeneficiaryType beneficiaryType, Long commercialId, Long partenaireId,
                                 Long amount, String beneficiaryName, String beneficiaryPhone, boolean testMode,
                                 Long partialPaymentRequestId, Long commercialPerformanceId) {
        this(contractId, beneficiaryType, commercialId, partenaireId, amount, beneficiaryName, beneficiaryPhone, testMode,
                partialPaymentRequestId, commercialPerformanceId, null);
    }

    /** Virement d'une prime de transport / indemnite de communication (2026-11-01) - aucun contrat, demande ni bonus. */
    public static SycaPayPaymentCommand forAvantage(Long commercialBenefitPaymentId, Long commercialId, Long amount,
                                                    String beneficiaryName, String beneficiaryPhone) {
        return new SycaPayPaymentCommand(null, SycaPayBeneficiaryType.COMMERCIAL, commercialId, null, amount, beneficiaryName,
                beneficiaryPhone, false, null, null, commercialBenefitPaymentId);
    }

    /** Signature anterieure (virement d'un contrat ou d'une demande de paiement). */
    public SycaPayPaymentCommand(Long contractId, SycaPayBeneficiaryType beneficiaryType, Long commercialId, Long partenaireId,
                                 Long amount, String beneficiaryName, String beneficiaryPhone, boolean testMode,
                                 Long partialPaymentRequestId) {
        this(contractId, beneficiaryType, commercialId, partenaireId, amount, beneficiaryName, beneficiaryPhone, testMode,
                partialPaymentRequestId, null);
    }

    /** Signature anterieure (virement d'un contrat). */
    public SycaPayPaymentCommand(Long contractId, SycaPayBeneficiaryType beneficiaryType, Long commercialId, Long partenaireId,
                                 Long amount, String beneficiaryName, String beneficiaryPhone, boolean testMode) {
        this(contractId, beneficiaryType, commercialId, partenaireId, amount, beneficiaryName, beneficiaryPhone, testMode, null, null);
    }

    /** Virement d'une demande de paiement (avance partenaire ou demande de commercial) - aucun contrat. */
    public static SycaPayPaymentCommand forDemande(Long partialPaymentRequestId, SycaPayBeneficiaryType beneficiaryType,
                                                   Long commercialId, Long partenaireId, Long amount,
                                                   String beneficiaryName, String beneficiaryPhone) {
        return new SycaPayPaymentCommand(null, beneficiaryType, commercialId, partenaireId, amount, beneficiaryName,
                beneficiaryPhone, false, partialPaymentRequestId, null);
    }

    /** Virement du bonus mensuel d'un commercial (2026-10-02) - aucun contrat, aucune demande. */
    public static SycaPayPaymentCommand forBonus(Long commercialPerformanceId, Long commercialId, Long amount,
                                                 String beneficiaryName, String beneficiaryPhone) {
        return new SycaPayPaymentCommand(null, SycaPayBeneficiaryType.COMMERCIAL, commercialId, null, amount, beneficiaryName,
                beneficiaryPhone, false, null, commercialPerformanceId);
    }

    /** Commission commerciale (signature historique, anterieure a l'ouverture aux partenaires). */
    public SycaPayPaymentCommand(Long contractId, Long commercialId, Long amount, String beneficiaryName, String beneficiaryPhone) {
        this(contractId, SycaPayBeneficiaryType.COMMERCIAL, commercialId, null, amount, beneficiaryName, beneficiaryPhone, false);
    }

    /** Meme commande, redirigee vers le numero et le montant de test (SycaPayModeTest). */
    public SycaPayPaymentCommand redirigeeVersTest(String telephoneTest, Long montantTest) {
        return new SycaPayPaymentCommand(contractId, beneficiaryType, commercialId, partenaireId, montantTest, beneficiaryName,
                telephoneTest, true, partialPaymentRequestId, commercialPerformanceId, commercialBenefitPaymentId);
    }

    public static SycaPayPaymentCommand forPartenaire(Long contractId, Long partenaireId, Long amount,
                                                      String beneficiaryName, String beneficiaryPhone) {
        return new SycaPayPaymentCommand(contractId, SycaPayBeneficiaryType.PARTENAIRE, null, partenaireId, amount,
                beneficiaryName, beneficiaryPhone, false);
    }
}
