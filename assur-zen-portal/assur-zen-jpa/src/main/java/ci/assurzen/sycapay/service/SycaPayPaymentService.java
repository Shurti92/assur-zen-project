package ci.assurzen.sycapay.service;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.config.SycaPayModeTest;
import ci.assurzen.sycapay.dto.SycaPayBalanceEntry;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.dto.SycaPayStatusResponse;
import ci.assurzen.sycapay.dto.SycaPayTransferRequest;
import ci.assurzen.sycapay.dto.SycaPayTransferResponse;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayAuthenticationException;
import ci.assurzen.sycapay.exception.SycaPayBalanceException;
import ci.assurzen.sycapay.exception.SycaPayDuplicatePaymentException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Orchestre un paiement de commission commerciale via SycaPay. Deliberement PAS annotee
 * @Transactional : cette methode encadre un appel HTTP sortant (borne par des timeouts explicites,
 * jamais indefini) et ne doit donc jamais tenir une transaction DB ouverte pendant son execution.
 * L'ecriture en base est deleguee a SycaPayTransactionWriter, en deux commits REQUIRES_NEW distincts
 * (avant et apres l'appel reseau) - cf. sa Javadoc pour le detail du raisonnement.
 *
 * Un seul contrat = un seul appel SycaPay par invocation de payCommission(). L'appelant
 * (CommercialCommissionService) est responsable de boucler sequentiellement sur plusieurs contrats
 * sans jamais envelopper cette boucle dans une transaction unique (le succes reel d'un virement Wave
 * ne peut pas etre annule par un rollback local).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SycaPayPaymentService {

    private static final String MODULE = "SYCAPAY";

    private final SycaPayProperties properties;
    private final SycaPayTransactionWriter writer;
    private final SycaPayAuthenticationService authenticationService;
    private final SycaPayClient sycaPayClient;
    private final FunctionalAuditRecorder auditRecorder;
    private final MeterRegistry meterRegistry;

    /** Mode test (hors production) : injecte par champ et optionnel - absent (tests unitaires), les
     * virements ne sont jamais rediriges. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SycaPayModeTest modeTest;

    public SycaPayPaymentOutcome payCommission(SycaPayPaymentCommand commandeDemandee) {
        if (!properties.isEnabled()) {
            throw new ci.assurzen.sycapay.exception.SycaPayException("Integration SycaPay desactivee (sycapay.enabled=false)");
        }
        // SycaPayProperties.validate() (@PostConstruct) ne verifie que la valeur STATIQUE d'enabled au
        // demarrage - "enabled" peut desormais aussi passer a true a chaud via le Dynamic Configuration
        // Engine (back-office) sans redemarrage, donc sans repasser par validate(). Garde live
        // equivalente ici, sur le seul chemin qui declenche reellement un appel externe.
        if (!hasCompleteCredentials()) {
            throw new ci.assurzen.sycapay.exception.SycaPayException(
                    "Integration SycaPay activee (SYCAPAY_ENABLED=true) mais merchant-id/api-key/secret-key incomplets");
        }

        // MODE TEST (hors production, SycaPayModeTest) : point d'entree UNIQUE de tous les virements
        // (manuel, IMMEDIAT automatique, nouvelle tentative) - tout est redirige ici, avant la creation de
        // la transaction : le solde verifie, le montant signe (HMAC) et le numero credite sont ceux du test.
        SycaPayPaymentCommand command = commandeDemandee;
        if (modeTest != null && modeTest.isActif()) {
            command = commandeDemandee.redirigeeVersTest(modeTest.getTelephone(), modeTest.getMontant());
            log.warn("[SYCAPAY] MODE TEST - virement du contrat {} redirige : {} XOF vers {} (reel) -> {} XOF vers {} (test)",
                    commandeDemandee.contractId(), commandeDemandee.amount(), commandeDemandee.beneficiaryPhone(),
                    command.amount(), command.beneficiaryPhone());
        }

        SycaPayTransaction transaction;
        try {
            transaction = (command.commercialPerformanceId() != null || command.commercialBenefitPaymentId() != null)
                    ? writer.createOrReactivatePending(
                            null, null, command.commercialPerformanceId(), command.commercialBenefitPaymentId(), command.beneficiaryType(), command.commercialId(),
                            command.partenaireId(),
                            command.amount(), properties.getCurrency(), command.beneficiaryName(), command.beneficiaryPhone(),
                            command.testMode())
                    : writer.createOrReactivatePending(
                            command.contractId(), command.partialPaymentRequestId(), command.beneficiaryType(), command.commercialId(),
                            command.partenaireId(),
                            command.amount(), properties.getCurrency(), command.beneficiaryName(), command.beneficiaryPhone(),
                            command.testMode());
        } catch (SycaPayDuplicatePaymentException e) {
            return SycaPayPaymentOutcome.rejected(e.getMessage());
        }

        auditRecorder.recordAction(MODULE, "SycaPayTransaction", transaction.getId().toString(), ActionAudit.PAYMENT_INITIATED,
                "Virement Wave initie pour " + (command.commercialBenefitPaymentId() != null
                        ? "un avantage commercial (paiement " + command.commercialBenefitPaymentId() + ")"
                        : command.commercialPerformanceId() != null
                        ? "le bonus mensuel (performance " + command.commercialPerformanceId() + ")"
                        : "le contrat " + command.contractId()) + " (reference " + transaction.getTransactionReference()
                        + ", montant " + command.amount() + " " + properties.getCurrency() + ")");
        count("initiated");

        // Exigence explicite (2026-09-22) : toujours verifier le solde Wave AVANT d'engager le
        // virement, jamais apres coup. Verification EN DIRECT (pas via le cache de
        // SycaPayBalanceService, reserve au polling frontend) - un paiement doit se baser sur le
        // solde reel a l'instant T, pas sur une valeur potentiellement vieille de plusieurs dizaines
        // de secondes. Si le solde est insuffisant (ou indisponible), la transaction est finalisee en
        // FAILED sans jamais appeler checktransferwave : aucune ambiguite possible, aucun montant n'a
        // pu partir puisqu'aucun virement n'a ete tente.
        BalanceCheck balanceCheck = verifySufficientBalance(command.amount());
        auditRecorder.recordAction(MODULE, "SycaPayBalance", transaction.getId().toString(), ActionAudit.BALANCE_CHECK,
                "Verification du solde " + properties.getProvider() + " avant paiement (contrat " + command.contractId() + ") : "
                        + (balanceCheck.sufficient() ? "suffisant" : "insuffisant/indisponible - " + balanceCheck.message()));
        if (!balanceCheck.sufficient()) {
            return finalizeAsFailed(transaction, null, balanceCheck.message());
        }

        String token;
        try {
            token = authenticationService.getValidToken();
        } catch (SycaPayAuthenticationException e) {
            return finalizeAsFailed(transaction, null, "Authentification SycaPay impossible: " + e.getMessage());
        }

        SycaPayTransferRequest transferRequest = buildTransferRequest(transaction, command);
        writer.markTransferSent(transaction.getId());
        SycaPayTransferResponse response = sycaPayClient.transferWave(token, transferRequest);

        if (response == null) {
            return finalizeAsUnknown(transaction);
        }

        // -19 (token incorrect) / -20 (token expire) : le jeton n'a pas ete accepte, le virement n'a
        // certainement pas pu partir - seul cas ou un second appel immediat est sans risque de double
        // paiement (cf. section 20 : retry autorise uniquement quand l'idempotence/le statut le
        // garantit). Une seule tentative de recuperation, jamais de boucle.
        if (isTokenError(response)) {
            authenticationService.invalidate();
            return retryOnceAfterTokenError(transaction, transferRequest);
        }

        return interpretResponse(transaction, response);
    }

    private SycaPayPaymentOutcome retryOnceAfterTokenError(SycaPayTransaction transaction, SycaPayTransferRequest transferRequest) {
        String retryToken;
        try {
            retryToken = authenticationService.getValidToken();
        } catch (SycaPayAuthenticationException e) {
            return finalizeAsFailed(transaction, null, "Reauthentification SycaPay impossible apres token invalide: " + e.getMessage());
        }
        SycaPayTransferResponse retryResponse = sycaPayClient.transferWave(retryToken, transferRequest);
        if (retryResponse == null) {
            return finalizeAsUnknown(transaction);
        }
        return interpretResponse(transaction, retryResponse);
    }

    private SycaPayPaymentOutcome interpretResponse(SycaPayTransaction transaction, SycaPayTransferResponse response) {
        if (response.isConfirmedSuccess()) {
            return finalizeAsSuccess(transaction, response);
        }
        if (response.isAsyncPending()) {
            return finalizeAsProcessing(transaction, response);
        }
        return finalizeAsFailed(transaction, response, response.description());
    }

    private boolean isTokenError(SycaPayTransferResponse response) {
        return response.code() != null && (response.code() == -19 || response.code() == -20);
    }

    private SycaPayPaymentOutcome finalizeAsSuccess(SycaPayTransaction transaction, SycaPayTransferResponse response) {
        SycaPayTransaction updated = writer.finalizeTransaction(transaction.getId(), SycaPayTransactionStatus.SUCCESS,
                response.referencetransfer(), null, response.code(), response.description(), null, null);
        auditRecorder.recordAction(MODULE, "SycaPayTransaction", updated.getId().toString(), ActionAudit.PAYMENT_SUCCESS,
                "Virement Wave confirme reussi (reference SycaPay " + response.referencetransfer() + ")");
        count("success");
        enrichSycapayTransactionId(updated);
        return SycaPayPaymentOutcome.of(SycaPayTransactionStatus.SUCCESS, updated.getId(), updated.getTransactionReference(), response.description());
    }

    /** Correctif 2026-09-23 : checktransferwave ne renvoie pas l'identifiant de transaction SycaPay, et
     * une transaction deja SUCCESS n'est plus reconciliable (StatusService) - sans cet appel,
     * sycapay_transaction_id restait NULL pour tout virement reussi du premier coup. Best-effort : le
     * paiement est DEJA confirme, aucun echec ici ne doit le remettre en cause (ni exception, ni
     * changement de statut) ; au pire la colonne reste NULL, comme avant. */
    private void enrichSycapayTransactionId(SycaPayTransaction transaction) {
        try {
            SycaPayStatusResponse status = sycaPayClient.getStatus(transaction.getTransactionReference());
            if (status != null) {
                writer.recordSycapayTransactionId(transaction.getId(), status.transactionid());
            }
        } catch (RuntimeException e) {
            log.warn("[SYCAPAY] identifiant de transaction SycaPay non recupere pour {} (paiement reussi, sans impact) : {}",
                    transaction.getTransactionReference(), e.getMessage());
        }
    }

    private SycaPayPaymentOutcome finalizeAsProcessing(SycaPayTransaction transaction, SycaPayTransferResponse response) {
        SycaPayTransaction updated = writer.finalizeTransaction(transaction.getId(), SycaPayTransactionStatus.PROCESSING,
                response.referencetransfer(), null, response.code(), response.description(), null, null);
        count("processing");
        return SycaPayPaymentOutcome.of(SycaPayTransactionStatus.PROCESSING, updated.getId(), updated.getTransactionReference(),
                "Traitement en cours cote SycaPay - a reconcilier via getStatusByOrderID");
    }

    private SycaPayPaymentOutcome finalizeAsFailed(SycaPayTransaction transaction, SycaPayTransferResponse response, String message) {
        Integer code = response == null ? null : response.code();
        SycaPayTransaction updated = writer.finalizeTransaction(transaction.getId(), SycaPayTransactionStatus.FAILED,
                response == null ? null : response.referencetransfer(), null, code, message,
                code == null ? null : String.valueOf(code), message);
        auditRecorder.recordActionFailure(MODULE, "SycaPayTransaction", updated.getId().toString(), ActionAudit.PAYMENT_FAILED, message);
        count("failed");
        return SycaPayPaymentOutcome.of(SycaPayTransactionStatus.FAILED, updated.getId(), updated.getTransactionReference(), message);
    }

    private SycaPayPaymentOutcome finalizeAsUnknown(SycaPayTransaction transaction) {
        SycaPayTransaction updated = writer.finalizeTransaction(transaction.getId(), SycaPayTransactionStatus.UNKNOWN,
                null, null, null, null, null,
                "Timeout ou erreur reseau - resultat du virement non confirme, a reconcilier avant toute nouvelle tentative");
        auditRecorder.recordAction(MODULE, "SycaPayTransaction", updated.getId().toString(), ActionAudit.PAYMENT_UNKNOWN,
                "Resultat du virement Wave inconnu (timeout/erreur reseau) - reference " + updated.getTransactionReference());
        count("unknown");
        log.warn("[SYCAPAY] transaction {} en statut UNKNOWN - reconciliation requise avant toute nouvelle tentative", updated.getTransactionReference());
        return SycaPayPaymentOutcome.of(SycaPayTransactionStatus.UNKNOWN, updated.getId(), updated.getTransactionReference(),
                "Resultat non confirme suite a une erreur reseau - ne pas relancer un paiement sans reconciliation prealable");
    }

    private SycaPayTransferRequest buildTransferRequest(SycaPayTransaction transaction, SycaPayPaymentCommand command) {
        return new SycaPayTransferRequest(
                transaction.getTransactionReference(),
                properties.getTypeTransfer(),
                libelleCommission(command),
                properties.getMerchantDisplayName(),
                properties.getMerchantDisplayName(),
                command.beneficiaryName(),
                String.valueOf(command.amount()),
                properties.getCurrency(),
                properties.getLanguage(),
                properties.getCallbackCancelUrl(),
                properties.getCallbackSuccessUrl(),
                normaliserAuFormatInternational(command.beneficiaryPhone()),
                properties.getProvider(),
                libelleCommission(command) + " AssurZen - " + (command.commercialBenefitPaymentId() != null
                        ? "avantage commercial " + command.commercialBenefitPaymentId()
                        : command.commercialPerformanceId() != null
                        ? "bonus mensuel " + command.commercialPerformanceId()
                        : command.partialPaymentRequestId() != null
                        ? "demande de paiement " + command.partialPaymentRequestId() : "contrat " + command.contractId())
        );
    }

    private static String libelleCommission(SycaPayPaymentCommand command) {
        if (command.commercialBenefitPaymentId() != null) {
            return "Avantage commercial";
        }
        if (command.commercialPerformanceId() != null) {
            return "Bonus commercial";
        }
        return command.beneficiaryType() == SycaPayBeneficiaryType.PARTENAIRE ? "Commission partenaire" : "Commission commerciale";
    }

    /** Meme normalisation que SmsSenderService (numero ivoirien post-2021 : le 0 initial est conserve
     * meme a l'international). Dupliquee volontairement (peu de code, deux modules independants) plutot
     * que d'introduire un couplage entre ci.assurzen.services et ci.assurzen.sycapay pour 6 lignes. */
    private String normaliserAuFormatInternational(String numero) {
        if (numero == null) {
            return null;
        }
        String trimmed = numero.trim();
        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        if (trimmed.startsWith("00")) {
            return "+" + trimmed.substring(2);
        }
        if (trimmed.startsWith("225")) {
            return "+" + trimmed;
        }
        if (trimmed.matches("0\\d{9}")) {
            return "+225" + trimmed;
        }
        return trimmed;
    }

    private void count(String status) {
        meterRegistry.counter("sycapay.payments.total", "status", status).increment();
    }

    private boolean hasCompleteCredentials() {
        return properties.getMerchantId() != null && !properties.getMerchantId().isBlank()
                && properties.getApiKey() != null && !properties.getApiKey().isBlank()
                && properties.getSecretKey() != null && !properties.getSecretKey().isBlank();
    }

    /**
     * Solde marchand SycaPay renvoye sous forme d'un tableau (un element par operateur : Wave, Orange,
     * MTN, Moov - format confirme sur un exemple reel). On ne retient que l'entree dont "operateur"
     * correspond au provider configure (Wave, perimetre de cette integration).
     */
    private BalanceCheck verifySufficientBalance(Long amountRequis) {
        SycaPayBalanceResponse balance;
        try {
            balance = sycaPayClient.checkBalance();
        } catch (SycaPayBalanceException e) {
            return BalanceCheck.insufficient("Solde SycaPay indisponible: " + e.getMessage());
        }

        List<SycaPayBalanceEntry> resultats = balance == null ? null : balance.resultats();
        if (resultats == null || resultats.isEmpty()) {
            return BalanceCheck.insufficient("Reponse de solde SycaPay vide ou illisible");
        }

        Optional<SycaPayBalanceEntry> entree = resultats.stream()
                .filter(e -> properties.getProvider().equalsIgnoreCase(e.operateur()))
                .findFirst();
        if (entree.isEmpty()) {
            return BalanceCheck.insufficient("Aucun solde " + properties.getProvider() + " present dans la reponse SycaPay");
        }

        BigDecimal disponible;
        try {
            disponible = new BigDecimal(entree.get().solde().trim());
        } catch (NumberFormatException | NullPointerException e) {
            return BalanceCheck.insufficient("Solde " + properties.getProvider() + " illisible: '" + entree.get().solde() + "'");
        }

        BigDecimal requis = BigDecimal.valueOf(amountRequis);
        if (disponible.compareTo(requis) < 0) {
            return BalanceCheck.insufficient("Solde " + properties.getProvider() + " insuffisant : " + disponible
                    + " " + properties.getCurrency() + " disponible, " + requis + " " + properties.getCurrency() + " requis");
        }
        return BalanceCheck.ok();
    }

    private record BalanceCheck(boolean sufficient, String message) {
        static BalanceCheck ok() {
            return new BalanceCheck(true, null);
        }

        static BalanceCheck insufficient(String message) {
            return new BalanceCheck(false, message);
        }
    }
}
