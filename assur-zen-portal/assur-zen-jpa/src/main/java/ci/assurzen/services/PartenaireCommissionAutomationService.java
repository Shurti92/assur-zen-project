package ci.assurzen.services;

import ci.assurzen.entities.CommissionPayment;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.FrequencePaiementCommission;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireNotification;
import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.repositories.CommissionPaymentRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayException;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Paiement des commissions PARTENAIRE selon Partenaire.frequencePaiementCommission (regle metier du
 * 2026-09-23) :
 * - IMMEDIAT : a la signature du contrat, virement Wave automatique via SycaPay sur
 *   numeroWaveCommission. En cas d'echec (FAILED/REJECTED) la commission reste payable ; UNKNOWN/
 *   PROCESSING se reconcilient via /sycapay/transactions (enregistrerVirementConfirme a la confirmation).
 * - A_LA_DEMANDE : SMS + notification in-app a chaque commission gagnee (contrat signe).
 * - HEBDOMADAIRE / QUINZAINE / MENSUELLE : aucun paiement automatique, rappel SMS + in-app a chaque
 *   echeance (lundi / 1er et 16 / 1er du mois, 9h Abidjan) s'il reste des commissions a payer.
 *
 * Aucune methode de cette classe n'est @Transactional : le virement SycaPay ne doit jamais s'executer
 * avec une transaction DB ouverte (cf. SycaPayPaymentService). Les lectures passent par des
 * transactions courtes (TransactionTemplate), les ecritures par les services dedies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartenaireCommissionAutomationService {

    static final String TEMPLATE_COMMISSION_PAYEE = "COMMISSION_PAYEE";
    static final String TEMPLATE_COMMISSION_GAGNEE = "COMMISSION_GAGNEE";
    static final String TEMPLATE_COMMISSION_DISPONIBLE = "COMMISSION_DISPONIBLE";

    private final ContractRepository contractRepository;
    private final PartenaireRepository partenaireRepository;
    private final CommissionPaymentRepository commissionPaymentRepository;
    private final SycaPayTransactionRepository sycaPayTransactionRepository;
    private final CommissionService commissionService;
    private final SycaPayPaymentService sycaPayPaymentService;
    private final SycaPayProperties sycaPayProperties;
    private final PartenaireNotificationService notificationService;
    private final TelegramNotificationService telegramNotificationService;
    private final TransactionTemplate transactionTemplate;

    /** Donnees du contrat/partenaire lues dans une transaction courte, utilisables hors session JPA. */
    record ContratSigne(Long contractId, String policyNumber, Long partnerShare, CommissionStatus commissionStatus,
                        Long partenaireId, String partenaireNom, boolean partenaireActif,
                        FrequencePaiementCommission frequence, String numeroWave) {
    }

    /** Point d'entree de ContractSignedEvent (apres commit, asynchrone). */
    public void traiterContratSigne(Long contractId) {
        ContratSigne contrat = transactionTemplate.execute(status -> chargerContrat(contractId));
        if (contrat == null || contrat.partenaireId() == null) {
            return;
        }
        if (contrat.frequence() == FrequencePaiementCommission.IMMEDIAT) {
            payerImmediatement(contrat);
        } else if (contrat.frequence() == FrequencePaiementCommission.A_LA_DEMANDE) {
            notifierCommissionGagnee(contrat);
        }
        // HEBDOMADAIRE / QUINZAINE / MENSUELLE : rien a la signature, rappel periodique (envoyerRappels).
    }

    private ContratSigne chargerContrat(Long contractId) {
        Contract contract = contractRepository.findById(contractId).orElse(null);
        if (contract == null || contract.getPartenaire() == null) {
            return null;
        }
        Partenaire partenaire = contract.getPartenaire();
        return new ContratSigne(contract.getId(), contract.getPolicyNumber(), contract.getPartnerShare(),
                contract.getCommissionStatus(), partenaire.getId(), nomPartenaire(partenaire), partenaire.isActive(),
                partenaire.getFrequencePaiementCommission(), partenaire.getNumeroWaveCommission());
    }

    void payerImmediatement(ContratSigne contrat) {
        String motifAbandon = motifDeNePasPayer(contrat);
        if (motifAbandon != null) {
            log.info("[COMMISSION-AUTO] contrat {} : pas de paiement automatique ({}) - la commission reste payable manuellement",
                    contrat.policyNumber(), motifAbandon);
            return;
        }

        SycaPayPaymentOutcome outcome;
        try {
            outcome = sycaPayPaymentService.payCommission(SycaPayPaymentCommand.forPartenaire(
                    contrat.contractId(), contrat.partenaireId(), contrat.partnerShare(), contrat.partenaireNom(), contrat.numeroWave()));
        } catch (SycaPayException e) {
            // Integration desactivee entre-temps, identifiants incomplets... : aucun virement n'est parti.
            log.warn("[COMMISSION-AUTO] contrat {} : paiement automatique impossible ({}) - la commission reste payable manuellement",
                    contrat.policyNumber(), e.getMessage());
            return;
        }

        if (outcome.isSuccess()) {
            enregistrerVirementConfirme(outcome.transactionId());
        } else {
            log.info("[COMMISSION-AUTO] contrat {} : virement {} ({}){}", contrat.policyNumber(),
                    outcome.rejected() ? "REJECTED" : outcome.status(), outcome.message(),
                    outcome.status() == SycaPayTransactionStatus.UNKNOWN || outcome.status() == SycaPayTransactionStatus.PROCESSING
                            ? " - a reconcilier via /sycapay/transactions, ne pas relancer" : " - la commission reste payable");
        }
    }

    /** null si le paiement automatique peut partir, sinon la raison (journalisee). */
    private String motifDeNePasPayer(ContratSigne contrat) {
        if (contrat.commissionStatus() != CommissionStatus.pending) {
            return "commission deja " + contrat.commissionStatus();
        }
        if (contrat.partnerShare() == null || contrat.partnerShare() <= 0) {
            return "montant de commission nul";
        }
        if (!contrat.partenaireActif()) {
            return "partenaire inactif";
        }
        if (!sycaPayProperties.isEnabled()) {
            return "SycaPay desactive (SYCAPAY_ENABLED=false)";
        }
        if (!StringUtils.hasText(contrat.numeroWave())) {
            return "numero Wave de commission non renseigne";
        }
        // Protection anti double paiement : meme regle que le paiement manuel (CommissionService) -
        // une avance partielle en cours peut deja couvrir tout ou partie de ce montant.
        if (commissionService.aDesPaiementsPartielsEnCours(contrat.partenaireId())) {
            return "demande de paiement partiel en cours";
        }
        return null;
    }

    /**
     * Enregistre le paiement d'une commission partenaire dont le virement SycaPay est CONFIRME - appele
     * juste apres un succes immediat, ou par SycaPayPaymentConfirmedEvent apres une reconciliation.
     * Idempotent : un virement deja enregistre est ignore. Ignore les transactions commerciales.
     */
    public void enregistrerVirementConfirme(Long sycapayTransactionId) {
        SycaPayTransaction transaction = sycaPayTransactionRepository.findById(sycapayTransactionId).orElse(null);
        if (transaction == null
                || transaction.getPartialPaymentRequestId() != null // virement d'une demande : CommissionPartialPaymentService
                || transaction.getContractId() == null
                || transaction.getBeneficiaryType() != SycaPayBeneficiaryType.PARTENAIRE
                || transaction.getStatus() != SycaPayTransactionStatus.SUCCESS
                || commissionPaymentRepository.existsBySycapayTransactionId(sycapayTransactionId)) {
            return;
        }

        CommissionPayment payment = commissionService.enregistrerPaiementAutomatique(transaction.getContractId(), transaction);
        if (payment == null) {
            // Le virement est parti ALORS QUE la commission etait deja marquee payee par un autre
            // chemin : double versement probable, a traiter par un humain.
            log.error("[COMMISSION-AUTO] virement {} confirme mais commission du contrat {} deja payee - DOUBLE PAIEMENT A VERIFIER",
                    transaction.getTransactionReference(), transaction.getContractId());
            telegramNotificationService.envoyer("ALERTE double paiement possible : virement SycaPay "
                    + transaction.getTransactionReference() + " (" + transaction.getAmount() + " FCFA) confirme pour le contrat "
                    + transaction.getContractId() + " dont la commission etait deja payee. Verification manuelle requise.");
            return;
        }

        // Montant de la commission enregistree (reel), pas celui du virement (montant de test en mode test).
        String montant = CommissionMontants.formater(payment.getAmount());
        PartenaireNotification notification = notificationService.notifier(transaction.getPartenaireId(), PartenaireNotificationType.COMMISSION_VERSEE,
                "Commission versée",
                "Votre commission de " + montant + " FCFA a été versée sur votre compte Wave (réf. "
                        + transaction.getTransactionReference() + ").",
                payment.getAmount(), transaction.getContractId(),
                TEMPLATE_COMMISSION_PAYEE,
                Map.of("montant", montant, "nombreContrats", "1", "reference", transaction.getTransactionReference()),
                TypeMessageSms.commission_payee);
        if (notification != null && payment.getId() != null) {
            commissionService.enregistrerResultatNotification(payment.getId(), notification.isSmsEnvoye());
        }
    }

    void notifierCommissionGagnee(ContratSigne contrat) {
        if (contrat.commissionStatus() != CommissionStatus.pending || contrat.partnerShare() == null || contrat.partnerShare() <= 0) {
            return;
        }
        String montant = CommissionMontants.formater(contrat.partnerShare());
        String total = CommissionMontants.formater(commissionService.totalPendingFor(contrat.partenaireId()));
        notificationService.notifier(contrat.partenaireId(), PartenaireNotificationType.COMMISSION_GAGNEE,
                "Nouvelle commission disponible",
                "Une nouvelle commission de " + montant + " FCFA (contrat " + contrat.policyNumber() + ") est disponible. Total disponible : "
                        + total + " FCFA. Vous pouvez en demander le paiement depuis votre espace partenaire.",
                contrat.partnerShare(), contrat.contractId(),
                TEMPLATE_COMMISSION_GAGNEE,
                Map.of("montant", montant, "police", contrat.policyNumber(), "total", total),
                TypeMessageSms.commission_gagnee);
    }

    /** Frequences dont l'echeance tombe ce jour-la : lundi (hebdomadaire), 1er et 16 (quinzaine), 1er (mensuelle). */
    static Set<FrequencePaiementCommission> frequencesDues(LocalDate jour) {
        Set<FrequencePaiementCommission> dues = EnumSet.noneOf(FrequencePaiementCommission.class);
        if (jour.getDayOfWeek() == DayOfWeek.MONDAY) {
            dues.add(FrequencePaiementCommission.HEBDOMADAIRE);
        }
        if (jour.getDayOfMonth() == 1 || jour.getDayOfMonth() == 16) {
            dues.add(FrequencePaiementCommission.QUINZAINE);
        }
        if (jour.getDayOfMonth() == 1) {
            dues.add(FrequencePaiementCommission.MENSUELLE);
        }
        return dues;
    }

    /** Rappels periodiques du jour (PartenaireCommissionRappelScheduler). Renvoie le nombre de rappels envoyes. */
    public int envoyerRappels(LocalDate jour) {
        Set<FrequencePaiementCommission> dues = frequencesDues(jour);
        if (dues.isEmpty()) {
            return 0;
        }
        int envoyes = 0;
        for (Partenaire partenaire : partenaireRepository.findByActiveTrueAndFrequencePaiementCommissionIn(dues)) {
            try {
                long total = commissionService.totalPendingFor(partenaire.getId());
                if (total <= 0) {
                    continue;
                }
                String montant = CommissionMontants.formater(total);
                notificationService.notifier(partenaire.getId(), PartenaireNotificationType.COMMISSION_DISPONIBLE,
                        "Commissions disponibles",
                        "Vous avez " + montant + " FCFA de commissions disponibles. Vous pouvez en demander le paiement depuis votre espace partenaire.",
                        total, null,
                        TEMPLATE_COMMISSION_DISPONIBLE, Map.of("montant", montant), TypeMessageSms.commission_disponible);
                envoyes++;
            } catch (RuntimeException e) {
                // Un partenaire en erreur ne doit pas priver les suivants de leur rappel.
                log.error("[COMMISSION-RAPPEL] echec du rappel pour le partenaire {} : {}", partenaire.getId(), e.getMessage(), e);
            }
        }
        log.info("[COMMISSION-RAPPEL] {} rappel(s) de commissions disponibles envoye(s) ({} ; frequences {})", envoyes, jour, dues);
        return envoyes;
    }

    private static String nomPartenaire(Partenaire partenaire) {
        return ((partenaire.getPrenoms() == null ? "" : partenaire.getPrenoms() + " ") + partenaire.getNom()).trim();
    }
}
