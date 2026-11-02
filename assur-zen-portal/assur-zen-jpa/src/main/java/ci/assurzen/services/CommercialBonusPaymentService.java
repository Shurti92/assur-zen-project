package ci.assurzen.services;

import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommercialBonusPaymentRequest;
import ci.assurzen.dto.CommercialBonusPaymentResponse;
import ci.assurzen.dto.CommercialPerformanceResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialPerformance;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.StatutCommercialPerformance;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.CommercialPerformanceRepository;
import ci.assurzen.repositories.MoisComptableRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayException;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Paiement REEL du bonus mensuel d'un commercial (2026-10-02). Service separe de
 * CommercialPerformanceService (§74 : Performance / Bonus / Payroll separes) : le calcul du bonus reste
 * entierement celui de CommercialPerformanceService (objectif x COMMERCIAL_BONUS_RATE% si taux &gt;=
 * COMMERCIAL_BONUS_MIN_ACHIEVEMENT_RATE), ce service ne fait que le payer.
 *
 * Workflow : performance calculee -&gt; ELIGIBLE -&gt; VALIDATED (montant fige) -&gt; payer() -&gt; virement Wave
 * SycaPay -&gt; PAID (confirmation immediate) ou en attente de reconciliation (PROCESSING/UNKNOWN, puis
 * SycaPayPaymentConfirmedEvent -&gt; confirmerVirementBonus()). Le bonus ne passe a PAID, le grand livre
 * n'est ecrit et le SMS n'est envoye QUE si le virement est confirme reussi.
 *
 * Mode hybride (meme regle que les commissions, decision du 2026-09-26) : SycaPay desactive -&gt; le
 * paiement est fait hors application puis ENREGISTRE ici (modePaiement = ENREGISTREMENT_MANUEL).
 *
 * Volontairement PAS @Transactional au niveau de payer() : l'appel reseau SycaPay ne doit jamais
 * s'executer sous une transaction DB ouverte (cf. SycaPayPaymentService) ; les ecritures locales sont
 * faites dans des TransactionTemplate courtes avant/apres.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommercialBonusPaymentService {

    static final String MODE_VIREMENT = CommissionService.MODE_VIREMENT;
    static final String MODE_HORS_APPLICATION = CommissionService.MODE_HORS_APPLICATION;
    private static final String MODULE = "COMMERCIAL_PERFORMANCE";
    private static final EnumSet<SycaPayTransactionStatus> EN_COURS = EnumSet.of(
            SycaPayTransactionStatus.PENDING, SycaPayTransactionStatus.PROCESSING, SycaPayTransactionStatus.UNKNOWN);

    private final CommercialPerformanceRepository repository;
    private final MoisComptableRepository moisComptableRepository;
    private final UserRepository userRepository;
    private final CommercialService commercialService;
    private final CommercialPerformanceService performanceService;
    private final SycaPayPaymentService sycaPayPaymentService;
    private final SycaPayTransactionRepository sycaPayTransactionRepository;
    private final SycaPayProperties sycaPayProperties;
    private final LedgerService ledgerService;
    private final TelegramNotificationService telegramNotificationService;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final PlatformTransactionManager transactionManager;

    /**
     * Paie le bonus valide d'un commercial pour un mois. Erreurs metier (409) : performance non validee,
     * bonus non eligible, montant nul, deja paye, numero de telephone absent. Les issues SycaPay (FAILED,
     * PROCESSING, UNKNOWN, REJECTED) ne sont PAS des erreurs HTTP : elles sont renvoyees dans
     * sycapayStatus pour que l'admin sache exactement ce qui s'est passe (meme contrat que
     * CommercialCommissionService.pay()).
     */
    public CommercialBonusPaymentResponse payer(Long commercialId, Long moisComptableId, CommercialBonusPaymentRequest request) {
        commercialService.requireAdmin();
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        String reference = request == null ? null : request.reference();
        String motif = request == null ? null : request.motif();

        validerSiObjectifAtteint(commercialId, moisComptableId, motif);
        CommercialPerformance perf = chargerPerformance(commercialId, moisComptableId);
        verifierPayable(perf);
        Commercial commercial = perf.getCommercial();
        MoisComptable mois = perf.getMoisComptable();

        SycaPayTransaction existante = sycaPayTransactionRepository.findByCommercialPerformanceId(perf.getId()).orElse(null);
        if (existante != null && existante.getStatus() == SycaPayTransactionStatus.SUCCESS) {
            // Virement deja confirme mais bonus jamais passe a PAID (echec local apres le virement) :
            // rattrapage SANS nouveau virement.
            CommercialPerformance payee = enregistrerPaiement(perf.getId(), principal.userId(), MODE_VIREMENT,
                    reference, existante.getId(), motif);
            return reponse(payee, MODE_VIREMENT, existante, existante.getStatus().name(), "Virement deja confirme - bonus enregistre comme paye");
        }
        if (existante != null && EN_COURS.contains(existante.getStatus())) {
            return reponse(perf, MODE_VIREMENT, existante, existante.getStatus().name(),
                    "Un virement est deja en cours pour ce bonus (reference " + existante.getTransactionReference()
                            + ") - ne pas relancer, reconcilier d'abord");
        }

        if (!sycaPayProperties.isEnabled()) {
            CommercialPerformance payee = enregistrerPaiement(perf.getId(), principal.userId(), MODE_HORS_APPLICATION,
                    reference, null, motif);
            return reponse(payee, MODE_HORS_APPLICATION, null, null, "Paiement enregistre (virement effectue hors application)");
        }

        String telephone = commercial.getProfile() == null ? null : commercial.getProfile().getPhone();
        if (!StringUtils.hasText(telephone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aucun numero de telephone (Wave) renseigne pour ce commercial - virement impossible");
        }

        long montant = perf.getBonusAmount().setScale(0, RoundingMode.HALF_UP).longValueExact();
        SycaPayPaymentCommand command = SycaPayPaymentCommand.forBonus(perf.getId(), commercial.getId(), montant,
                performanceService.nomCommercial(commercial), telephone);

        SycaPayPaymentOutcome outcome;
        try {
            outcome = sycaPayPaymentService.payCommission(command);
        } catch (SycaPayException e) {
            log.warn("[BONUS-COMMERCIAL] performance {} : virement impossible ({})", perf.getId(), e.getMessage());
            return reponse(perf, MODE_VIREMENT, existante, "REJECTED",
                    "Service de paiement SycaPay indisponible - aucun virement n'est parti");
        }

        if (outcome.transactionId() != null) {
            lierTransaction(perf.getId(), outcome.transactionId());
        }
        SycaPayTransaction transaction = outcome.transactionId() == null ? existante
                : sycaPayTransactionRepository.findById(outcome.transactionId()).orElse(null);

        if (outcome.isSuccess()) {
            CommercialPerformance payee = enregistrerPaiement(perf.getId(), principal.userId(), MODE_VIREMENT,
                    reference, outcome.transactionId(), motif);
            return reponse(payee, MODE_VIREMENT, transaction, outcome.status().name(), outcome.message());
        }

        // FAILED (relancable), PROCESSING/UNKNOWN (a reconcilier) ou REJECTED : le bonus reste VALIDATED.
        String statut = outcome.rejected() ? "REJECTED" : outcome.status().name();
        if (outcome.transactionId() != null) {
            perf.setSycapayTransactionId(outcome.transactionId());
        }
        return reponse(perf, MODE_VIREMENT, transaction, statut, outcome.message());
    }

    /**
     * Virement de bonus reste PROCESSING/UNKNOWN puis CONFIRME par une reconciliation
     * (SycaPayPaymentConfirmedEvent) : enregistre le paiement du bonus. Idempotent ; ignore toute
     * transaction qui n'est pas un virement de bonus. Jamais d'appel SycaPay ici.
     */
    public void confirmerVirementBonus(Long sycapayTransactionId, String confirmePar) {
        SycaPayTransaction transaction = sycaPayTransactionRepository.findById(sycapayTransactionId).orElse(null);
        if (transaction == null || transaction.getCommercialPerformanceId() == null
                || transaction.getStatus() != SycaPayTransactionStatus.SUCCESS) {
            return;
        }
        CommercialPerformance perf = repository.findById(transaction.getCommercialPerformanceId()).orElse(null);
        if (perf == null) {
            log.error("[BONUS-COMMERCIAL] virement {} confirme pour une performance introuvable ({})",
                    transaction.getTransactionReference(), transaction.getCommercialPerformanceId());
            return;
        }
        if (perf.getPaidAt() != null) {
            if (!transaction.getId().equals(perf.getSycapayTransactionId())) {
                telegramNotificationService.envoyer("ALERTE double paiement possible : virement SycaPay "
                        + transaction.getTransactionReference() + " (" + transaction.getAmount()
                        + " FCFA) confirme pour un bonus commercial deja paye (performance " + perf.getId()
                        + "). Verification manuelle requise.");
            }
            return;
        }
        Long payeurId = trouverUtilisateur(transaction.getCreatedBy())
                .or(() -> trouverUtilisateur(confirmePar))
                .map(User::getId)
                .orElse(null);
        enregistrerPaiement(perf.getId(), payeurId, MODE_VIREMENT, null, transaction.getId(),
                "Virement SycaPay confirme par reconciliation (" + transaction.getTransactionReference() + ")");
    }

    /** Historique des paiements de bonus (payes, en cours, echoues), tous commerciaux ou un seul. */
    public List<CommercialPerformanceResponse> historique(Long commercialId) {
        commercialService.requireAdmin();
        List<CommercialPerformance> performances = new TransactionTemplate(transactionManager).execute(statut -> {
            List<CommercialPerformance> lignes = repository.findBonusPaymentHistory(commercialId);
            // Initialise les associations LAZY utilisees par toResponse() hors transaction.
            lignes.forEach(p -> {
                p.getMoisComptable().getLibelle();
                p.getCommercial().getProfile().getFirstname();
                if (p.getPaidBy() != null) {
                    p.getPaidBy().getId();
                }
                if (p.getValidatedBy() != null) {
                    p.getValidatedBy().getId();
                }
            });
            return lignes;
        });
        Map<Long, SycaPayTransaction> transactions = performanceService.transactionsParPerformance(performances);
        return performances.stream()
                .map(p -> performanceService.toResponse(p, transactions.get(p.getId())))
                .toList();
    }

    // ------------------------------------------------------------------

    /**
     * Paiement direct d'un bonus atteint (demande du 2026-10-02) : si la periode n'est pas encore
     * verrouillee, la performance est d'abord RECALCULEE (chiffres du jour, persistes) puis, si l'objectif
     * est atteint, VALIDEE (montant fige) - exactement les deux actions admin existantes, enchainees. Une
     * performance non atteinte est refusee (409) sans rien valider. Periode deja verrouillee : rien a faire.
     */
    private void validerSiObjectifAtteint(Long commercialId, Long moisComptableId, String motif) {
        boolean verrouillee = repository.findByCommercialIdAndMoisComptableId(commercialId, moisComptableId)
                .map(perf -> perf.getStatut() == StatutCommercialPerformance.VALIDATED
                        || perf.getStatut() == StatutCommercialPerformance.PAID
                        || perf.getStatut() == StatutCommercialPerformance.CLOSED)
                .orElse(false);
        if (verrouillee) {
            return;
        }
        CommercialPerformanceResponse recalculee = performanceService.recalculer(commercialId, moisComptableId);
        if (!recalculee.eligible() || recalculee.bonusAmount() == null || recalculee.bonusAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bonus non eligible : taux d'atteinte "
                    + recalculee.achievementRate().setScale(2, RoundingMode.HALF_UP) + " % inferieur au seuil requis ("
                    + recalculee.seuilEligibilite() + " %)");
        }
        performanceService.changerStatut(commercialId, moisComptableId, StatutCommercialPerformance.VALIDATED,
                StringUtils.hasText(motif) ? motif : "Validation automatique au paiement du bonus (objectif atteint)");
    }

    private CommercialPerformance chargerPerformance(Long commercialId, Long moisComptableId) {
        commercialService.getCommercial(commercialId);
        MoisComptable mois = moisComptableRepository.findById(moisComptableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mois comptable introuvable: " + moisComptableId));
        return new TransactionTemplate(transactionManager).execute(statut -> {
            CommercialPerformance perf = repository.findByCommercialIdAndMoisComptableId(commercialId, mois.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Bonus non payable : la performance de " + mois.getLibelle() + " doit d'abord etre validee"));
            // Associations LAZY utilisees hors transaction (telephone, nom, libelle du mois).
            perf.getCommercial().getProfile().getPhone();
            perf.getMoisComptable().getLibelle();
            return perf;
        });
    }

    private void verifierPayable(CommercialPerformance perf) {
        if (perf.getPaidAt() != null || perf.getStatut() == StatutCommercialPerformance.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce bonus a deja ete paye");
        }
        if (!perf.isEligible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bonus non eligible : taux d'atteinte "
                    + perf.getAchievementRate().setScale(2, RoundingMode.HALF_UP) + " % inferieur au seuil requis");
        }
        if (perf.getBonusAmount() == null || perf.getBonusAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Montant du bonus nul - rien a payer");
        }
        if (perf.getStatut() == StatutCommercialPerformance.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Periode cloturee - le bonus ne peut plus etre paye");
        }
        if (perf.getStatut() != StatutCommercialPerformance.VALIDATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bonus non payable : la performance doit d'abord etre validee (montant fige)");
        }
    }

    private void lierTransaction(Long performanceId, Long transactionId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(statut ->
                repository.findById(performanceId).ifPresent(perf -> {
                    if (!transactionId.equals(perf.getSycapayTransactionId())) {
                        perf.setSycapayTransactionId(transactionId);
                        repository.save(perf);
                    }
                }));
    }

    /**
     * Passage a PAID + grand livre + audit, atomiques, puis SMS (hors transaction, echec non bloquant :
     * le paiement est deja acquis). Rejoue la regle de transition du workflow (VALIDATED -&gt; PAID).
     */
    private CommercialPerformance enregistrerPaiement(Long performanceId, Long payeurId, String modePaiement, String reference,
                                                      Long transactionId, String motif) {
        CommercialPerformance payee = new TransactionTemplate(transactionManager).execute(statut -> {
            CommercialPerformance perf = repository.findById(performanceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Performance introuvable: " + performanceId));
            if (perf.getPaidAt() != null) {
                return perf; // deja enregistre (rattrapage concurrent)
            }
            performanceService.validerTransition(perf.getStatut(), StatutCommercialPerformance.PAID, perf.isEligible());
            CommercialPerformanceResponse avant = performanceService.toResponse(perf, null);

            perf.setStatut(StatutCommercialPerformance.PAID);
            perf.setPaidAt(Instant.now());
            perf.setPaidBy(payeurId == null ? null : userRepository.findById(payeurId).orElse(null));
            perf.setModePaiement(modePaiement);
            perf.setPaymentReference(StringUtils.hasText(reference) ? reference.trim() : null);
            if (transactionId != null) {
                perf.setSycapayTransactionId(transactionId);
            }
            CommercialPerformance saved = repository.save(perf);

            Commercial commercial = saved.getCommercial();
            ledgerService.enregistrerBonusCommercial(performanceService.nomCommercial(commercial),
                    saved.getMoisComptable().getLibelle(),
                    saved.getBonusAmount().setScale(0, RoundingMode.HALF_UP).longValueExact());
            functionalAuditRecorder.recordUpdate(MODULE, "CommercialPerformance", String.valueOf(saved.getId()),
                    avant, performanceService.toResponse(saved, null), motif);
            // Associations LAZY utilisees apres la transaction (SMS, reponse).
            commercial.getProfile().getPhone();
            saved.getMoisComptable().getLibelle();
            if (saved.getValidatedBy() != null) {
                saved.getValidatedBy().getId();
            }
            if (saved.getPaidBy() != null) {
                saved.getPaidBy().getId();
            }
            return saved;
        });

        if (payee.getNotificationEnvoyee() == null) {
            boolean notifie = notifierSansEchec(payee);
            new TransactionTemplate(transactionManager).executeWithoutResult(statut ->
                    repository.findById(payee.getId()).ifPresent(perf -> {
                        perf.setNotificationEnvoyee(notifie);
                        repository.save(perf);
                    }));
            payee.setNotificationEnvoyee(notifie);
            performanceService.notifierCommercial(payee.getCommercial(), PartenaireNotificationType.BONUS_PAYE, "Bonus payé",
                    "Votre bonus de " + payee.getMoisComptable().getLibelle() + " ("
                            + performanceService.formatMontant(payee.getBonusAmount()) + " FCFA) a été payé.", payee);
            telegramNotificationService.envoyer("<b>Bonus commercial paye</b>\n"
                    + TelegramNotificationService.echapperHtml(performanceService.nomCommercial(payee.getCommercial()))
                    + " - " + payee.getMoisComptable().getLibelle() + " : "
                    + performanceService.formatMontant(payee.getBonusAmount()) + " FCFA ("
                    + (MODE_VIREMENT.equals(modePaiement) ? "virement Wave" : "paiement hors application") + ")");
        }
        return payee;
    }

    private boolean notifierSansEchec(CommercialPerformance perf) {
        try {
            return performanceService.notifierBonusPaye(perf.getCommercial(), perf.getMoisComptable(), perf);
        } catch (RuntimeException e) {
            log.warn("[BONUS-COMMERCIAL] SMS de bonus paye non envoye (performance {}) : {}", perf.getId(), e.getMessage());
            return false;
        }
    }

    private CommercialBonusPaymentResponse reponse(CommercialPerformance perf, String modePaiement, SycaPayTransaction transaction,
                                                   String sycapayStatus, String message) {
        return new CommercialBonusPaymentResponse(
                performanceService.toResponse(perf, transaction),
                modePaiement,
                sycapayStatus,
                transaction == null ? null : transaction.getTransactionReference(),
                message);
    }

    private Optional<User> trouverUtilisateur(String username) {
        return StringUtils.hasText(username) ? userRepository.findByUsername(username) : Optional.empty();
    }
}
