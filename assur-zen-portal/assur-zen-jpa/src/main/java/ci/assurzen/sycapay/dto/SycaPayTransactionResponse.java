package ci.assurzen.sycapay.dto;

import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;

import java.time.Instant;

/** Vue monitoring d'une SycaPayTransaction, exposee par SycaPayController. Ne reprend aucun champ de
 * configuration/secret (apiKey/secretKey/token n'existent meme pas sur l'entite). */
public record SycaPayTransactionResponse(
        Long id,
        String transactionReference,
        Long contractId,
        /** COMMERCIAL ou PARTENAIRE (2026-09-23) - un seul des deux ids suivants est renseigne. */
        SycaPayBeneficiaryType beneficiaryType,
        Long commercialId,
        Long partenaireId,
        Long amount,
        String currency,
        String beneficiaryName,
        String beneficiaryPhone,
        SycaPayTransactionStatus status,
        String sycapayReference,
        String sycapayTransactionId,
        Integer sycapayResponseCode,
        String sycapayResponseMessage,
        Instant requestDate,
        Instant processingDate,
        Instant completedDate,
        Integer attemptCount,
        String errorCode,
        String errorMessage,
        /** Virement redirige par le mode test (hors production) : amount/beneficiaryPhone = valeurs de test. */
        boolean testMode,
        /** Demande de paiement (avance partenaire / demande de commercial) payee par ce virement - contractId
         * est alors null (2026-09-26). */
        Long partialPaymentRequestId,
        /** Bonus mensuel (CommercialPerformance) paye par ce virement - contractId est alors null (2026-10-02). */
        Long commercialPerformanceId
) {
    /** Signature anterieure (virement d'un contrat ou d'une demande de paiement). */
    public SycaPayTransactionResponse(Long id, String transactionReference, Long contractId, SycaPayBeneficiaryType beneficiaryType,
                                      Long commercialId, Long partenaireId, Long amount, String currency, String beneficiaryName,
                                      String beneficiaryPhone, SycaPayTransactionStatus status, String sycapayReference,
                                      String sycapayTransactionId, Integer sycapayResponseCode, String sycapayResponseMessage,
                                      Instant requestDate, Instant processingDate, Instant completedDate, Integer attemptCount,
                                      String errorCode, String errorMessage, boolean testMode, Long partialPaymentRequestId) {
        this(id, transactionReference, contractId, beneficiaryType, commercialId, partenaireId, amount, currency, beneficiaryName,
                beneficiaryPhone, status, sycapayReference, sycapayTransactionId, sycapayResponseCode, sycapayResponseMessage,
                requestDate, processingDate, completedDate, attemptCount, errorCode, errorMessage, testMode, partialPaymentRequestId, null);
    }

    /** Signature anterieure (virement d'un contrat). */
    public SycaPayTransactionResponse(Long id, String transactionReference, Long contractId, SycaPayBeneficiaryType beneficiaryType,
                                      Long commercialId, Long partenaireId, Long amount, String currency, String beneficiaryName,
                                      String beneficiaryPhone, SycaPayTransactionStatus status, String sycapayReference,
                                      String sycapayTransactionId, Integer sycapayResponseCode, String sycapayResponseMessage,
                                      Instant requestDate, Instant processingDate, Instant completedDate, Integer attemptCount,
                                      String errorCode, String errorMessage, boolean testMode) {
        this(id, transactionReference, contractId, beneficiaryType, commercialId, partenaireId, amount, currency, beneficiaryName,
                beneficiaryPhone, status, sycapayReference, sycapayTransactionId, sycapayResponseCode, sycapayResponseMessage,
                requestDate, processingDate, completedDate, attemptCount, errorCode, errorMessage, testMode, null);
    }
}
