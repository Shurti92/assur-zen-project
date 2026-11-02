package ci.assurzen.sycapay.service;

import ci.assurzen.enums.CodePrefix;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.services.CodeGeneratorService;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayDuplicatePaymentException;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Seul point d'ecriture de SycaPayTransaction. Isole dans son propre bean Spring (et non des methodes
 * privees de SycaPayPaymentService) pour la meme raison que FunctionalAuditRecorder delegue a
 * HistoriqueModificationWriter : @Transactional sur un appel "this.methode()" depuis la MEME instance
 * ne passe PAS par le proxy Spring et n'ouvre donc aucune transaction - separer en deux beans est la
 * seule maniere fiable d'obtenir des transactions REQUIRES_NEW independantes de part et d'autre de
 * l'appel HTTP sortant vers SycaPay (qui ne doit jamais s'executer avec une transaction DB ouverte).
 */
@Service
@RequiredArgsConstructor
public class SycaPayTransactionWriter {

    /** Auteur retenu hors requete HTTP authentifiee (job planifie, appel technique). */
    static final String SYSTEM_USER = "system";

    private final SycaPayTransactionRepository repository;
    private final CodeGeneratorService codeGeneratorService;

    /**
     * Cree la ligne PENDING avant tout appel externe (exigence section 9) ou reactive une ligne
     * existante en FAILED (nouvelle tentative). Rejette (SycaPayDuplicatePaymentException) si une
     * transaction active/non reconciliee existe deja pour ce contrat - garde d'idempotence. La
     * contrainte UNIQUE(contract_id) sert de filet de securite ultime contre une double insertion
     * concurrente (deux requetes simultanees passant toutes les deux le contrat "findByContractId
     * absent" avant que l'une des deux ne committe) : MySQL/InnoDB ne supporte pas d'index unique
     * partiel, donc ce filet catch une DataIntegrityViolationException plutot que d'eliminer
     * completement la fenetre de course en amont - c'est une decision assumee, pas un oubli.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SycaPayTransaction createOrReactivatePending(Long contractId, Long commercialId, Long amount,
                                                          String currency, String beneficiaryName, String beneficiaryPhone) {
        return createOrReactivatePending(contractId, SycaPayBeneficiaryType.COMMERCIAL, commercialId, null, amount,
                currency, beneficiaryName, beneficiaryPhone);
    }

    /** Variante generale (2026-09-23) : commission commerciale OU partenaire. Memes regles
     * d'idempotence - UNE transaction par contrat, quel que soit le type de beneficiaire (un contrat
     * n'a jamais les deux : CommercialCommissionService refuse deja un contrat avec partenaire). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SycaPayTransaction createOrReactivatePending(Long contractId, SycaPayBeneficiaryType beneficiaryType,
                                                          Long commercialId, Long partenaireId, Long amount,
                                                          String currency, String beneficiaryName, String beneficiaryPhone) {
        return createOrReactivatePending(contractId, beneficiaryType, commercialId, partenaireId, amount, currency,
                beneficiaryName, beneficiaryPhone, false);
    }

    /** testMode : virement redirige par le mode test (hors production) - trace sur la transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SycaPayTransaction createOrReactivatePending(Long contractId, SycaPayBeneficiaryType beneficiaryType,
                                                          Long commercialId, Long partenaireId, Long amount,
                                                          String currency, String beneficiaryName, String beneficiaryPhone,
                                                          boolean testMode) {
        return createOrReactivatePending(contractId, null, beneficiaryType, commercialId, partenaireId, amount, currency,
                beneficiaryName, beneficiaryPhone, testMode);
    }

    /** Variante complete (2026-09-26) : virement d'un contrat (contractId) OU d'une demande de paiement
     * (partialPaymentRequestId) - une transaction par objet paye, memes regles d'idempotence. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SycaPayTransaction createOrReactivatePending(Long contractId, Long partialPaymentRequestId,
                                                          SycaPayBeneficiaryType beneficiaryType,
                                                          Long commercialId, Long partenaireId, Long amount,
                                                          String currency, String beneficiaryName, String beneficiaryPhone,
                                                          boolean testMode) {
        return createOrReactivatePending(contractId, partialPaymentRequestId, null, beneficiaryType, commercialId, partenaireId,
                amount, currency, beneficiaryName, beneficiaryPhone, testMode);
    }

    /** Variante 2026-10-02 : ajoute le virement du bonus mensuel d'un commercial (commercialPerformanceId).
     * Exactement un des trois objets payes (contrat, demande, bonus) est renseigne. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SycaPayTransaction createOrReactivatePending(Long contractId, Long partialPaymentRequestId,
                                                          Long commercialPerformanceId,
                                                          SycaPayBeneficiaryType beneficiaryType,
                                                          Long commercialId, Long partenaireId, Long amount,
                                                          String currency, String beneficiaryName, String beneficiaryPhone,
                                                          boolean testMode) {
        return createOrReactivatePending(contractId, partialPaymentRequestId, commercialPerformanceId, null, beneficiaryType,
                commercialId, partenaireId, amount, currency, beneficiaryName, beneficiaryPhone, testMode);
    }

    /** Variante 2026-11-01 : ajoute le virement d'une prime de transport / indemnite de communication
     * (commercialBenefitPaymentId). Exactement un des quatre objets payes est renseigne. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SycaPayTransaction createOrReactivatePending(Long contractId, Long partialPaymentRequestId,
                                                          Long commercialPerformanceId, Long commercialBenefitPaymentId,
                                                          SycaPayBeneficiaryType beneficiaryType,
                                                          Long commercialId, Long partenaireId, Long amount,
                                                          String currency, String beneficiaryName, String beneficiaryPhone,
                                                          boolean testMode) {
        SycaPayTransaction transaction = (commercialBenefitPaymentId != null
                ? repository.findByCommercialBenefitPaymentId(commercialBenefitPaymentId)
                : commercialPerformanceId != null
                ? repository.findByCommercialPerformanceId(commercialPerformanceId)
                : partialPaymentRequestId != null
                ? repository.findByPartialPaymentRequestId(partialPaymentRequestId)
                : repository.findByContractId(contractId)).orElse(null);
        String objet = commercialBenefitPaymentId != null ? "l'avantage commercial (paiement " + commercialBenefitPaymentId + ")"
                : commercialPerformanceId != null ? "le bonus (performance " + commercialPerformanceId + ")"
                : partialPaymentRequestId != null ? "la demande de paiement " + partialPaymentRequestId : "le contrat " + contractId;

        if (transaction != null) {
            if (!transaction.getStatus().canTransitionTo(SycaPayTransactionStatus.PENDING)) {
                throw new SycaPayDuplicatePaymentException(
                        "Une transaction SycaPay " + transaction.getStatus()
                                + " existe deja pour " + objet
                                + " (reference " + transaction.getTransactionReference() + ")");
            }
            transaction.setTransactionReference(generateReference());
            transaction.setBeneficiaryType(beneficiaryType);
            transaction.setTestMode(testMode);
            transaction.setCommercialId(commercialId);
            transaction.setPartenaireId(partenaireId);
            // Correctif 2026-09-23 : les identifiants SycaPay de la tentative precedente doivent etre
            // effaces - finalizeTransaction() ne les ecrase que si la nouvelle reponse en fournit, une
            // nouvelle tentative en UNKNOWN/FAILED sans reponse aurait sinon garde ceux de l'ancien
            // virement (confusion possible lors d'une reconciliation).
            transaction.setSycapayReference(null);
            transaction.setSycapayTransactionId(null);
            transaction.setAmount(amount);
            transaction.setCurrency(currency);
            transaction.setBeneficiaryName(beneficiaryName);
            transaction.setBeneficiaryPhone(beneficiaryPhone);
            transaction.setStatus(SycaPayTransactionStatus.PENDING);
            transaction.setRequestDate(Instant.now());
            transaction.setProcessingDate(null);
            transaction.setCompletedDate(null);
            transaction.setAttemptCount(transaction.getAttemptCount() + 1);
            transaction.setSycapayResponseCode(null);
            transaction.setSycapayResponseMessage(null);
            transaction.setErrorCode(null);
            transaction.setErrorMessage(null);
            transaction.setUpdatedBy(currentUsername());
            return repository.save(transaction);
        }

        SycaPayTransaction fresh = new SycaPayTransaction();
        fresh.setTransactionReference(generateReference());
        fresh.setContractId(contractId);
        fresh.setPartialPaymentRequestId(partialPaymentRequestId);
        fresh.setCommercialPerformanceId(commercialPerformanceId);
        fresh.setCommercialBenefitPaymentId(commercialBenefitPaymentId);
        fresh.setBeneficiaryType(beneficiaryType);
        fresh.setTestMode(testMode);
        fresh.setCommercialId(commercialId);
        fresh.setPartenaireId(partenaireId);
        fresh.setAmount(amount);
        fresh.setCurrency(currency);
        fresh.setBeneficiaryName(beneficiaryName);
        fresh.setBeneficiaryPhone(beneficiaryPhone);
        fresh.setStatus(SycaPayTransactionStatus.PENDING);
        fresh.setRequestDate(Instant.now());
        fresh.setAttemptCount(1);
        String username = currentUsername();
        fresh.setCreatedBy(username);
        fresh.setUpdatedBy(username);

        try {
            return repository.save(fresh);
        } catch (DataIntegrityViolationException e) {
            throw new SycaPayDuplicatePaymentException(
                    "Une transaction SycaPay concurrente vient d'etre creee pour " + objet);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SycaPayTransaction finalizeTransaction(Long transactionId, SycaPayTransactionStatus newStatus,
                                                    String sycapayReference, String sycapayTransactionId,
                                                    Integer sycapayResponseCode, String sycapayResponseMessage,
                                                    String errorCode, String errorMessage) {
        SycaPayTransaction transaction = findOrThrow(transactionId);

        if (!transaction.getStatus().canTransitionTo(newStatus)) {
            throw new IllegalStateException("Transition SycaPay invalide : " + transaction.getStatus() + " -> " + newStatus
                    + " (transaction " + transaction.getTransactionReference() + ")");
        }

        transaction.setStatus(newStatus);
        if (sycapayReference != null) {
            transaction.setSycapayReference(sycapayReference);
        }
        if (sycapayTransactionId != null) {
            transaction.setSycapayTransactionId(sycapayTransactionId);
        }
        transaction.setSycapayResponseCode(sycapayResponseCode);
        transaction.setSycapayResponseMessage(sycapayResponseMessage);
        transaction.setErrorCode(errorCode);
        transaction.setErrorMessage(errorMessage);

        Instant now = Instant.now();
        // processingDate = date d'envoi du virement (cf. markTransferSent) : conservee si deja posee,
        // jamais decalee a la date de reponse.
        if (newStatus == SycaPayTransactionStatus.PROCESSING && transaction.getProcessingDate() == null) {
            transaction.setProcessingDate(now);
        }
        if (newStatus == SycaPayTransactionStatus.SUCCESS
                || newStatus == SycaPayTransactionStatus.FAILED
                || newStatus == SycaPayTransactionStatus.CANCELLED) {
            transaction.setCompletedDate(now);
        }
        transaction.setUpdatedBy(currentUsername());

        return repository.save(transaction);
    }

    /** Correctif 2026-09-23 : date d'envoi du virement, persistee AVANT l'appel HTTP checktransferwave
     * (une transaction finalisee directement en SUCCESS/FAILED/UNKNOWN n'avait sinon jamais de
     * processingDate). Non posee quand le virement n'est jamais parti (solde insuffisant,
     * authentification impossible). Idempotente : la date du premier envoi est conservee en cas de
     * nouvel essai apres token invalide. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SycaPayTransaction markTransferSent(Long transactionId) {
        SycaPayTransaction transaction = findOrThrow(transactionId);
        if (transaction.getProcessingDate() == null) {
            transaction.setProcessingDate(Instant.now());
            transaction.setUpdatedBy(currentUsername());
            transaction = repository.save(transaction);
        }
        return transaction;
    }

    /** Correctif 2026-09-23 : checktransferwave ne renvoie pas d'identifiant de transaction SycaPay (seul
     * getStatusByOrderID le fournit) - renseigne sycapayTransactionId apres coup, sans toucher au statut.
     * Ignore une valeur vide ; n'ecrase jamais une valeur deja connue. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSycapayTransactionId(Long transactionId, String sycapayTransactionId) {
        if (sycapayTransactionId == null || sycapayTransactionId.isBlank()) {
            return;
        }
        SycaPayTransaction transaction = findOrThrow(transactionId);
        if (transaction.getSycapayTransactionId() == null) {
            transaction.setSycapayTransactionId(sycapayTransactionId);
            transaction.setUpdatedBy(currentUsername());
            repository.save(transaction);
        }
    }

    private SycaPayTransaction findOrThrow(Long transactionId) {
        return repository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Transaction SycaPay introuvable: " + transactionId));
    }

    /** Correctif 2026-09-23 : created_by/updated_by restaient toujours NULL. Lu directement dans le
     * SecurityContext (pas AuthService.currentPrincipal(), qui leve une exception hors requete
     * authentifiee et recharge l'utilisateur en base) pour ne jamais bloquer une ecriture SycaPay. */
    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal
                && principal.username() != null) {
            return principal.username();
        }
        return SYSTEM_USER;
    }

    private String generateReference() {
        return codeGeneratorService.generate(CodePrefix.SYCAPAY_TRANSACTION);
    }
}
