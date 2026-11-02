package ci.assurzen.services;

import ci.assurzen.events.ContractSignedEvent;
import ci.assurzen.events.SycaPayPaymentConfirmedEvent;
import ci.assurzen.events.SycaPayPaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Declenche le traitement des commissions (partenaire, et commerciales apres reconciliation) APRES commit de la transaction d'origine
 * (fallbackExecution : aussi hors transaction) et sur un thread dedie : un echec ici - reseau SycaPay,
 * SMS, base - ne peut jamais annuler la signature du contrat ni la reconciliation qui l'a declenche.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PartenaireCommissionEventListener {

    private final PartenaireCommissionAutomationService automationService;
    private final CommercialCommissionService commercialCommissionService;
    private final CommissionPartialPaymentService partialPaymentService;
    private final CommercialBonusPaymentService commercialBonusPaymentService;
    private final CommercialBenefitPaymentService commercialBenefitPaymentService;

    @Async("commissionPaiementTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onContractSigned(ContractSignedEvent event) {
        try {
            automationService.traiterContratSigne(event.contractId());
        } catch (RuntimeException e) {
            log.error("[COMMISSION-AUTO] echec du traitement de la commission du contrat {} - a traiter manuellement : {}",
                    event.contractId(), e.getMessage(), e);
        }
    }

    @Async("commissionPaiementTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSycaPayPaymentConfirmed(SycaPayPaymentConfirmedEvent event) {
        // Chaque service ignore les transactions de l'autre type de beneficiaire.
        try {
            automationService.enregistrerVirementConfirme(event.sycapayTransactionId());
            commercialCommissionService.enregistrerVirementConfirme(event.sycapayTransactionId(), event.confirmePar());
            // Virement d'une demande de paiement (avance / demande de commercial), 2026-09-26.
            partialPaymentService.confirmerVirement(event.sycapayTransactionId(), event.confirmePar());
            // Virement du bonus mensuel d'un commercial, 2026-10-02.
            commercialBonusPaymentService.confirmerVirementBonus(event.sycapayTransactionId(), event.confirmePar());
            // Virement de la prime de transport / indemnite de communication d'un commercial, 2026-11-01.
            commercialBenefitPaymentService.confirmerVirement(event.sycapayTransactionId(), event.confirmePar());
        } catch (RuntimeException e) {
            log.error("[COMMISSION-AUTO] virement SycaPay {} confirme mais paiement non enregistre - a traiter manuellement : {}",
                    event.sycapayTransactionId(), e.getMessage(), e);
        }
    }

    /** Reconciliation FAILED d'un virement de demande de paiement : la demande passe en echec (2026-09-26). */
    @Async("commissionPaiementTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSycaPayPaymentFailed(SycaPayPaymentFailedEvent event) {
        try {
            partialPaymentService.marquerEchecVirement(event.sycapayTransactionId());
        } catch (RuntimeException e) {
            log.error("[COMMISSION-PARTIEL] virement SycaPay {} en echec mais demande non mise a jour - a traiter manuellement : {}",
                    event.sycapayTransactionId(), e.getMessage(), e);
        }
    }
}
