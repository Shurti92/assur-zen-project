package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommercialBenefitPaymentResponse;
import ci.assurzen.dto.CommercialBenefitStatus;
import ci.assurzen.dto.CommercialBenefitSummary;
import ci.assurzen.dto.CommercialBonusPaymentRequest;
import ci.assurzen.dto.CommercialPaymentHistoryEntry;
import ci.assurzen.dto.CommercialPerformanceResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialBenefitPayment;
import ci.assurzen.entities.CommercialPerformance;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.StatutAvantageCommercial;
import ci.assurzen.entities.TypeAvantageCommercial;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.CommercialBenefitPaymentRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Paiement de la prime de transport et de l'indemnite de communication des commerciaux (2026-11-01),
 * SEPAREMENT du bonus (CommercialBonusPaymentService, inchange) et l'un de l'autre : un paiement par
 * commercial, par mois comptable et par type. Aucune dependance au statut du mois ni a l'eligibilite au
 * bonus. Le montant est celui de la fiche commercial (CommercialPerformance.*Snapshot, vide si l'avantage
 * est desactive) et il est fige dans le paiement a chaque tentative non encore payee.
 *
 * Codes d'erreur : 404 commercial / mois introuvable, 409 deja paye / virement en cours / numero Wave
 * absent, 422 avantage non applicable (montant nul ou avantage desactive). Les issues SycaPay (FAILED,
 * PROCESSING, UNKNOWN, REJECTED) ne sont PAS des erreurs HTTP : elles sont renvoyees dans sycapayStatus.
 *
 * Meme principe que CommercialBonusPaymentService : pas de @Transactional sur payer() (l'appel reseau
 * SycaPay ne doit jamais s'executer sous une transaction DB ouverte), ecritures locales courtes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommercialBenefitPaymentService {

    private static final String MODE_VIREMENT = CommissionService.MODE_VIREMENT;
    private static final String MODE_HORS_APPLICATION = CommissionService.MODE_HORS_APPLICATION;
    private static final String MODULE = "COMMERCIAL_PERFORMANCE";
    private static final EnumSet<SycaPayTransactionStatus> EN_COURS = EnumSet.of(
            SycaPayTransactionStatus.PENDING, SycaPayTransactionStatus.PROCESSING, SycaPayTransactionStatus.UNKNOWN);

    private final CommercialBenefitPaymentRepository repository;
    private final MoisComptableRepository moisComptableRepository;
    private final UserRepository userRepository;
    private final CommercialService commercialService;
    private final CommercialPerformanceService performanceService;
    private final CommercialBonusPaymentService bonusPaymentService;
    private final SycaPayPaymentService sycaPayPaymentService;
    private final SycaPayTransactionRepository sycaPayTransactionRepository;
    private final SycaPayProperties sycaPayProperties;
    private final LedgerService ledgerService;
    private final TelegramNotificationService telegramNotificationService;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final PlatformTransactionManager transactionManager;

    /** Donnees lues en transaction courte avant l'eventuel appel reseau. */
    private record Preparation(Long paymentId, BigDecimal montant, String nomCommercial, String telephone) {
    }

    public CommercialBenefitPaymentResponse payer(Long commercialId, Long moisComptableId, TypeAvantageCommercial type,
                                                  CommercialBonusPaymentRequest request) {
        commercialService.requireAdmin();
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        String reference = request == null ? null : request.reference();
        String motif = request == null ? null : request.motif();

        commercialService.getCommercial(commercialId);
        moisComptableRepository.findById(moisComptableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mois comptable introuvable: " + moisComptableId));

        Preparation prep = new TransactionTemplate(transactionManager).execute(statut -> preparer(commercialId, moisComptableId, type));
        Long paymentId = prep.paymentId();
        String libelle = type == TypeAvantageCommercial.TRANSPORT ? "prime de transport" : "indemnite de communication";

        SycaPayTransaction existante = sycaPayTransactionRepository.findByCommercialBenefitPaymentId(paymentId).orElse(null);
        if (existante != null && existante.getStatus() == SycaPayTransactionStatus.SUCCESS) {
            // Virement deja confirme mais avantage jamais enregistre : rattrapage SANS nouveau virement.
            enregistrerPaiement(paymentId, principal.userId(), MODE_VIREMENT, reference, existante.getId(), motif);
            return reponse(paymentId, type, MODE_VIREMENT, existante, existante.getStatus().name(),
                    "Virement deja confirme - " + libelle + " enregistree comme payee");
        }
        if (existante != null && EN_COURS.contains(existante.getStatus())) {
            return reponse(paymentId, type, MODE_VIREMENT, existante, existante.getStatus().name(),
                    "Un virement est deja en cours pour cet avantage (reference " + existante.getTransactionReference()
                            + ") - ne pas relancer, reconcilier d'abord");
        }

        if (!sycaPayProperties.isEnabled()) {
            enregistrerPaiement(paymentId, principal.userId(), MODE_HORS_APPLICATION, reference, null, motif);
            return reponse(paymentId, type, MODE_HORS_APPLICATION, null, null,
                    "Paiement enregistre (virement effectue hors application)");
        }

        if (!StringUtils.hasText(prep.telephone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aucun numero de telephone (Wave) renseigne pour ce commercial - virement impossible");
        }

        long montant = prep.montant().setScale(0, RoundingMode.HALF_UP).longValueExact();
        SycaPayPaymentCommand command = SycaPayPaymentCommand.forAvantage(paymentId, commercialId, montant,
                prep.nomCommercial(), prep.telephone());

        SycaPayPaymentOutcome outcome;
        try {
            outcome = sycaPayPaymentService.payCommission(command);
        } catch (SycaPayException e) {
            log.warn("[AVANTAGE-COMMERCIAL] paiement {} : virement impossible ({})", paymentId, e.getMessage());
            return reponse(paymentId, type, MODE_VIREMENT, existante, "REJECTED",
                    "Service de paiement SycaPay indisponible - aucun virement n'est parti");
        }

        if (outcome.transactionId() != null) {
            lierTransaction(paymentId, outcome.transactionId());
        }
        SycaPayTransaction transaction = outcome.transactionId() == null ? existante
                : sycaPayTransactionRepository.findById(outcome.transactionId()).orElse(null);

        if (outcome.isSuccess()) {
            enregistrerPaiement(paymentId, principal.userId(), MODE_VIREMENT, reference, outcome.transactionId(), motif);
            return reponse(paymentId, type, MODE_VIREMENT, transaction, outcome.status().name(), outcome.message());
        }

        // FAILED (relancable), PROCESSING/UNKNOWN (a reconcilier) ou REJECTED : l'avantage reste a payer.
        String statut = outcome.rejected() ? "REJECTED" : outcome.status().name();
        return reponse(paymentId, type, MODE_VIREMENT, transaction, statut, outcome.message());
    }

    /**
     * Virement d'avantage reste PROCESSING/UNKNOWN puis CONFIRME par une reconciliation
     * (SycaPayPaymentConfirmedEvent) : enregistre le paiement. Idempotent ; ignore toute transaction qui
     * n'est pas un virement d'avantage. Jamais d'appel SycaPay ici.
     */
    public void confirmerVirement(Long sycapayTransactionId, String confirmePar) {
        SycaPayTransaction transaction = sycaPayTransactionRepository.findById(sycapayTransactionId).orElse(null);
        if (transaction == null || transaction.getCommercialBenefitPaymentId() == null
                || transaction.getStatus() != SycaPayTransactionStatus.SUCCESS) {
            return;
        }
        CommercialBenefitPayment paiement = repository.findById(transaction.getCommercialBenefitPaymentId()).orElse(null);
        if (paiement == null) {
            log.error("[AVANTAGE-COMMERCIAL] virement {} confirme pour un paiement introuvable ({})",
                    transaction.getTransactionReference(), transaction.getCommercialBenefitPaymentId());
            return;
        }
        if (paiement.getPaidAt() != null) {
            if (!transaction.getId().equals(paiement.getSycapayTransactionId())) {
                telegramNotificationService.envoyer("ALERTE double paiement possible : virement SycaPay "
                        + transaction.getTransactionReference() + " (" + transaction.getAmount()
                        + " FCFA) confirme pour un avantage commercial deja paye (paiement " + paiement.getId()
                        + "). Verification manuelle requise.");
            }
            return;
        }
        Long payeurId = trouverUtilisateur(transaction.getCreatedBy())
                .or(() -> trouverUtilisateur(confirmePar))
                .map(User::getId)
                .orElse(null);
        enregistrerPaiement(paiement.getId(), payeurId, MODE_VIREMENT, null, transaction.getId(),
                "Virement SycaPay confirme par reconciliation (" + transaction.getTransactionReference() + ")");
    }

    // ------------------------------------------------------------------
    // Lecture : etat des avantages, historique, agregats
    // ------------------------------------------------------------------

    /** Montant de l'avantage selon la fiche commercial (0 si l'avantage est desactive). */
    public static BigDecimal montantFiche(CommercialPerformance perf, TypeAvantageCommercial type) {
        BigDecimal montant = type == TypeAvantageCommercial.TRANSPORT ? perf.getTransportBenefitSnapshot() : perf.getPhoneAllowanceSnapshot();
        return montant == null ? BigDecimal.ZERO : montant;
    }

    /** Paiements d'avantage d'un mois, indexes par commercial puis type. */
    public Map<Long, Map<TypeAvantageCommercial, CommercialBenefitPayment>> paiementsDuMois(Long moisComptableId) {
        return repository.findByMoisComptableId(moisComptableId).stream()
                .collect(Collectors.groupingBy(p -> p.getCommercial().getId(),
                        Collectors.toMap(CommercialBenefitPayment::getType, Function.identity(), (a, b) -> a)));
    }

    /** Virements SycaPay des paiements donnes, en une requete, indexes par paiement. */
    public Map<Long, SycaPayTransaction> transactionsParPaiement(java.util.Collection<CommercialBenefitPayment> paiements) {
        List<Long> ids = paiements.stream().map(CommercialBenefitPayment::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sycaPayTransactionRepository.findByCommercialBenefitPaymentIdIn(ids).stream()
                .collect(Collectors.toMap(SycaPayTransaction::getCommercialBenefitPaymentId, t -> t, (a, b) -> a));
    }

    /** Etat d'un avantage : paiement et virement peuvent etre null (aucune tentative). */
    public CommercialBenefitStatus statut(TypeAvantageCommercial type, BigDecimal montantFiche, CommercialBenefitPayment paiement,
                                          SycaPayTransaction transaction) {
        boolean paye = paiement != null && paiement.getPaidAt() != null;
        if (paye && MODE_HORS_APPLICATION.equals(paiement.getModePaiement())) {
            transaction = null; // paiement manuel : les echecs SycaPay anterieurs ne concernent plus l'avantage
        }
        BigDecimal montant = paye ? paiement.getAmount() : (montantFiche == null ? BigDecimal.ZERO : montantFiche);
        StatutAvantageCommercial statut;
        boolean payable;
        if (paye) {
            statut = StatutAvantageCommercial.PAYE;
            payable = false;
        } else if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            statut = StatutAvantageCommercial.NON_APPLICABLE;
            payable = false;
        } else if (transaction == null) {
            statut = StatutAvantageCommercial.A_PAYER;
            payable = true;
        } else if (transaction.getStatus() == SycaPayTransactionStatus.FAILED) {
            statut = StatutAvantageCommercial.ECHEC;
            payable = true;
        } else if (transaction.getStatus() == SycaPayTransactionStatus.SUCCESS) {
            statut = StatutAvantageCommercial.EN_COURS; // confirme mais non enregistre : rattrapage sans nouveau virement
            payable = true;
        } else {
            statut = StatutAvantageCommercial.EN_COURS;
            payable = false;
        }
        return new CommercialBenefitStatus(type, montant, payable, statut,
                transaction == null ? null : transaction.getStatus().name(),
                transaction == null ? null : transaction.getId(),
                transaction == null ? null : transaction.getTransactionReference(),
                transaction == null ? null : (transaction.getErrorMessage() != null ? transaction.getErrorMessage() : transaction.getSycapayResponseMessage()),
                paiement == null ? null : paiement.getPaidAt(),
                paiement == null ? null : paiement.getModePaiement(),
                paiement == null ? null : paiement.getPaymentReference(),
                paiement == null ? null : paiement.getId());
    }

    /** Agregats a payer / payes d'un mois, calcules a partir des lignes de paie (meme source que l'ecran). */
    public CommercialBenefitSummary resume(Long moisComptableId, List<ci.assurzen.dto.CommercialPayrollEntry> lignes) {
        BigDecimal transportAPayer = BigDecimal.ZERO, communicationAPayer = BigDecimal.ZERO;
        BigDecimal transportPaye = BigDecimal.ZERO, communicationPaye = BigDecimal.ZERO;
        long transportAPayerN = 0, communicationAPayerN = 0, transportPayeN = 0, communicationPayeN = 0;
        for (ci.assurzen.dto.CommercialPayrollEntry ligne : lignes) {
            CommercialBenefitStatus t = ligne.transport();
            CommercialBenefitStatus c = ligne.communication();
            if (t != null && t.payable()) {
                transportAPayer = transportAPayer.add(t.amount());
                transportAPayerN++;
            }
            if (t != null && t.statut() == StatutAvantageCommercial.PAYE) {
                transportPaye = transportPaye.add(t.amount());
                transportPayeN++;
            }
            if (c != null && c.payable()) {
                communicationAPayer = communicationAPayer.add(c.amount());
                communicationAPayerN++;
            }
            if (c != null && c.statut() == StatutAvantageCommercial.PAYE) {
                communicationPaye = communicationPaye.add(c.amount());
                communicationPayeN++;
            }
        }
        return new CommercialBenefitSummary(moisComptableId, transportAPayer, transportAPayerN, communicationAPayer,
                communicationAPayerN, transportPaye, transportPayeN, communicationPaye, communicationPayeN);
    }

    /**
     * Historique unifie des paiements commerciaux : type = BONUS | TRANSPORT | COMMUNICATION (null = tous),
     * filtrable par commercial, du plus recent au plus ancien.
     */
    public List<CommercialPaymentHistoryEntry> historique(Long commercialId, String type) {
        commercialService.requireAdmin();
        String filtre = StringUtils.hasText(type) ? type.trim().toUpperCase() : null;
        if (filtre != null && !List.of("BONUS", "TRANSPORT", "COMMUNICATION").contains(filtre)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type invalide : BONUS, TRANSPORT ou COMMUNICATION attendu");
        }
        List<CommercialPaymentHistoryEntry> resultat = new ArrayList<>();

        if (filtre == null || filtre.equals("BONUS")) {
            for (CommercialPerformanceResponse b : bonusPaymentService.historique(commercialId)) {
                resultat.add(new CommercialPaymentHistoryEntry("BONUS", b.id(), b.commercialId(), b.commercialNom(), b.employeeCode(),
                        b.moisComptableId(), b.moisLibelle(), b.bonusAmount(), statutHistorique(b.paidAt(), b.sycapayStatus()),
                        b.modePaiement(), b.paymentReference(), b.paidAt(), b.sycapayStatus(), b.sycapayTransactionId(),
                        b.sycapayTransactionReference(), b.sycapayMessage()));
            }
        }
        if (filtre == null || !filtre.equals("BONUS")) {
            TypeAvantageCommercial typeAvantage = filtre == null ? null : TypeAvantageCommercial.valueOf(filtre);
            List<CommercialPaymentHistoryEntry> avantages = new TransactionTemplate(transactionManager).execute(statut -> {
                List<CommercialBenefitPayment> paiements = repository.findHistory(commercialId, typeAvantage);
                Map<Long, SycaPayTransaction> transactions = transactionsParPaiement(paiements);
                return paiements.stream().map(p -> {
                    SycaPayTransaction t = transactions.get(p.getId());
                    return new CommercialPaymentHistoryEntry(p.getType().name(), p.getId(), p.getCommercial().getId(),
                            performanceService.nomCommercial(p.getCommercial()), p.getCommercial().getEmployeeCode(),
                            p.getMoisComptable().getId(), p.getMoisComptable().getLibelle(), p.getAmount(),
                            statutHistorique(p.getPaidAt(), t == null ? null : t.getStatus().name()),
                            p.getModePaiement(), p.getPaymentReference(), p.getPaidAt(),
                            t == null ? null : t.getStatus().name(), t == null ? null : t.getId(),
                            t == null ? null : t.getTransactionReference(),
                            t == null ? null : (t.getErrorMessage() != null ? t.getErrorMessage() : t.getSycapayResponseMessage()));
                }).toList();
            });
            resultat.addAll(avantages);
        }
        resultat.sort(Comparator.comparing(
                (CommercialPaymentHistoryEntry e) -> e.paidAt() == null ? Instant.EPOCH : e.paidAt()).reversed());
        return resultat;
    }

    private static String statutHistorique(Instant paidAt, String sycapayStatus) {
        if (paidAt != null) {
            return "PAYE";
        }
        if ("FAILED".equals(sycapayStatus)) {
            return "ECHEC";
        }
        return sycapayStatus == null ? "A_PAYER" : "EN_COURS";
    }

    // ------------------------------------------------------------------

    /** Valide le paiement et fige le montant ; cree l'enregistrement au premier essai. Tout en transaction courte. */
    private Preparation preparer(Long commercialId, Long moisComptableId, TypeAvantageCommercial type) {
        Commercial commercial = commercialService.getCommercial(commercialId);
        MoisComptable mois = moisComptableRepository.findById(moisComptableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mois comptable introuvable: " + moisComptableId));

        CommercialBenefitPayment paiement = repository
                .findByCommercialIdAndMoisComptableIdAndType(commercialId, moisComptableId, type).orElse(null);
        if (paiement != null && paiement.getPaidAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, libelleType(type) + " deja payee pour " + mois.getLibelle());
        }
        BigDecimal montant = montantFiche(performanceService.resoudrePourLecture(commercial, mois), type);
        if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    libelleType(type) + " non applicable : montant nul ou avantage desactive pour ce commercial");
        }

        if (paiement == null) {
            paiement = new CommercialBenefitPayment();
            paiement.setCommercial(commercial);
            paiement.setMoisComptable(mois);
            paiement.setType(type);
            paiement.setAmount(montant);
            try {
                paiement = repository.saveAndFlush(paiement);
            } catch (DataIntegrityViolationException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Un paiement est deja en cours de creation pour cet avantage");
            }
        } else if (paiement.getSycapayTransactionId() == null
                || sycaPayTransactionRepository.findById(paiement.getSycapayTransactionId())
                .map(t -> !EN_COURS.contains(t.getStatus()) && t.getStatus() != SycaPayTransactionStatus.SUCCESS).orElse(true)) {
            // Tentative precedente echouee ou jamais lancee : le montant est fige a nouveau (fiche du moment).
            paiement.setAmount(montant);
            repository.save(paiement);
        }
        String telephone = commercial.getProfile() == null ? null : commercial.getProfile().getPhone();
        return new Preparation(paiement.getId(), paiement.getAmount(), performanceService.nomCommercial(commercial), telephone);
    }

    private String libelleType(TypeAvantageCommercial type) {
        return type == TypeAvantageCommercial.TRANSPORT ? "Prime de transport" : "Indemnite de communication";
    }

    private void lierTransaction(Long paymentId, Long transactionId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(statut ->
                repository.findById(paymentId).ifPresent(p -> {
                    if (!transactionId.equals(p.getSycapayTransactionId())) {
                        p.setSycapayTransactionId(transactionId);
                        repository.save(p);
                    }
                }));
    }

    /** Passage a paye + grand livre + audit, atomiques ; notification Telegram ensuite (echec non bloquant). */
    private void enregistrerPaiement(Long paymentId, Long payeurId, String modePaiement, String reference,
                                     Long transactionId, String motif) {
        String[] message = new String[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(statut -> {
            CommercialBenefitPayment paiement = repository.findById(paymentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable: " + paymentId));
            if (paiement.getPaidAt() != null) {
                return; // deja enregistre (rattrapage concurrent)
            }
            CommercialBenefitStatus avant = statut(paiement.getType(), paiement.getAmount(), paiement, null);

            paiement.setPaidAt(Instant.now());
            paiement.setPaidBy(payeurId == null ? null : userRepository.findById(payeurId).orElse(null));
            paiement.setModePaiement(modePaiement);
            paiement.setPaymentReference(StringUtils.hasText(reference) ? reference.trim() : null);
            if (transactionId != null) {
                paiement.setSycapayTransactionId(transactionId);
            }
            CommercialBenefitPayment saved = repository.save(paiement);

            String nom = performanceService.nomCommercial(saved.getCommercial());
            String mois = saved.getMoisComptable().getLibelle();
            boolean transport = saved.getType() == TypeAvantageCommercial.TRANSPORT;
            ledgerService.enregistrerAvantageCommercial(transport, nom, mois,
                    saved.getAmount().setScale(0, RoundingMode.HALF_UP).longValueExact());
            functionalAuditRecorder.recordUpdate(MODULE, "CommercialBenefitPayment", String.valueOf(saved.getId()),
                    avant, statut(saved.getType(), saved.getAmount(), saved, null), motif);
            message[0] = "<b>" + (transport ? "Prime de transport" : "Indemnite de communication") + " payee</b>\n"
                    + TelegramNotificationService.echapperHtml(nom) + " - " + mois + " : "
                    + performanceService.formatMontant(saved.getAmount()) + " FCFA ("
                    + (MODE_VIREMENT.equals(modePaiement) ? "virement Wave" : "paiement hors application") + ")";
        });
        if (message[0] != null) {
            try {
                telegramNotificationService.envoyer(message[0]);
            } catch (RuntimeException e) {
                log.warn("[AVANTAGE-COMMERCIAL] notification Telegram non envoyee (paiement {}) : {}", paymentId, e.getMessage());
            }
        }
    }

    private CommercialBenefitPaymentResponse reponse(Long paymentId, TypeAvantageCommercial type, String modePaiement,
                                                     SycaPayTransaction transaction, String sycapayStatus, String message) {
        CommercialBenefitPayment paiement = repository.findById(paymentId).orElse(null);
        SycaPayTransaction t = transaction;
        if (paiement != null && paiement.getSycapayTransactionId() != null) {
            t = sycaPayTransactionRepository.findById(paiement.getSycapayTransactionId()).orElse(transaction);
        }
        BigDecimal montant = paiement == null ? BigDecimal.ZERO : paiement.getAmount();
        return new CommercialBenefitPaymentResponse(
                statut(type, montant, paiement, t),
                modePaiement,
                sycapayStatus,
                transaction == null ? null : transaction.getTransactionReference(),
                message);
    }

    private Optional<User> trouverUtilisateur(String username) {
        return StringUtils.hasText(username) ? userRepository.findByUsername(username) : Optional.empty();
    }
}
