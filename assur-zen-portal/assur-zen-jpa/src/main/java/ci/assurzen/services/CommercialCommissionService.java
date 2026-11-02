package ci.assurzen.services;

import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.events.NotificationUtilisateurEvent;
import org.springframework.context.ApplicationEventPublisher;
import ci.assurzen.dto.CommercialCommissionPaymentRequest;
import ci.assurzen.dto.CommercialCommissionPaymentResponse;
import ci.assurzen.dto.CommercialCommissionSelfResponse;
import ci.assurzen.dto.CommercialCommissionSummaryResponse;
import ci.assurzen.dto.ContractCommercialCommissionResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialCommissionPayment;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.CommercialCommissionPaymentRepository;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayException;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.utils.SafeAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Paiement de la commission commerciale par contrat (contrat "Commission commerciale interne",
 * 2026-09-08) - flux SEPARE du cycle mensuel de performance/bonus (decision produit explicite),
 * mirroir volontairement allege de CommissionService (partenaires) : reprend le coeur (lister/payer/
 * historique) mais PAS la pre-notification "demande recue" ni le mecanisme d'avance/paiement partiel -
 * non demandes pour ce lot, a ajouter plus tard si le besoin se confirme.
 *
 * Reservee aux administrateurs (SecurityConfig : /api-assur-zen/commercial-commissions/** en
 * hasRole(ADMIN), meme convention que /commissions/** cote partenaire), meme garde en double au niveau
 * service.
 *
 * Integration SycaPay (2026-09-22) : pay() declenche desormais un VRAI virement Wave par contrat via
 * SycaPayPaymentService, au lieu de se contenter d'enregistrer une reference declarative. Consequence
 * assumee : pay() n'est plus "tout ou rien" au niveau de chaque contrat individuel une fois les
 * controles d'eligibilite locaux passes - un contrat peut echouer (FAILED/UNKNOWN/PROCESSING) sans
 * qu'aucun CommercialCommissionPayment ne soit cree, alors qu'un autre contrat de la MEME requete
 * reussit. C'est pourquoi pay() n'est plus @Transactional au niveau methode : un rollback local ne
 * peut pas annuler un virement Wave deja parti pour un autre contrat de la meme liste (cf.
 * SycaPayPaymentService pour le detail des transactions courtes REQUIRES_NEW autour de l'appel
 * reseau). Le frontend DOIT lire CommercialCommissionPaymentResponse.sycapayStatus pour chaque
 * contrat plutot que de supposer un succes uniforme.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommercialCommissionService {

    static final String COMMERCIAL_SUPPRIME = "Commercial supprime";
    /** CommercialCommissionPaymentResponse.modePaiement (2026-09-26), memes valeurs que cote partenaire. */
    static final String MODE_VIREMENT = CommissionService.MODE_VIREMENT;
    static final String MODE_HORS_APPLICATION = CommissionService.MODE_HORS_APPLICATION;

    private static final String TEMPLATE_COMMISSION_COMMERCIALE_PAYEE = "COMMISSION_COMMERCIALE_PAYEE";

    private final ContractRepository contractRepository;
    private final CommercialRepository commercialRepository;
    private final CommercialCommissionPaymentRepository commercialCommissionPaymentRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final LedgerService ledgerService;
    private final SmsNotificationService smsNotificationService;
    private final TelegramNotificationService telegramNotificationService;
    private final SycaPayPaymentService sycaPayPaymentService;
    private final SycaPayTransactionRepository sycaPayTransactionRepository;
    private final CommissionPartialPaymentRequestRepository paymentRequestRepository;
    private final SycaPayProperties sycaPayProperties;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationEventPublisher eventPublisher;

    public List<CommercialCommissionSummaryResponse> listCommercialSummaries() {
        requireAdmin();
        List<Contract> eligible = contractRepository.findByStatusAndPartenaireIsNull(ContractStatus.signed);

        return commercialRepository.findAll().stream()
                .map(commercial -> summarize(commercial, eligible))
                .filter(summary -> summary.pendingContractsCount() > 0 || summary.paidContractsCount() > 0)
                .toList();
    }

    /** Self-service (§ demande frontend/produit du 2026-09-08) : resume + detail contrat par contrat
     * du commercial CONNECTE uniquement - aucun parametre d'id cote client, resolu depuis le JWT (meme
     * mecanisme que CommercialPerformanceService.dashboard()). Pas de garde ADMIN : un commercial peut
     * consulter sa propre commission, jamais celle d'un autre (aucun id n'est accepte en entree). */
    public CommercialCommissionSelfResponse mySummary() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        Commercial commercial = commercialRepository.findByProfile_UserId(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune fiche commerciale liee a cet utilisateur"));

        List<Contract> contracts = contractRepository.findByStatusAndPartenaireIsNullAndSalesRepId(ContractStatus.signed, principal.userId());
        CommercialCommissionSummaryResponse summary = summarize(commercial, contracts);

        List<ContractCommercialCommissionResponse> contractResponses = contracts.stream()
                .filter(c -> c.getCommercialCommissionAmount() != null && c.getCommercialCommissionAmount() > 0)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toContractResponse)
                .toList();

        return new CommercialCommissionSelfResponse(
                summary.pendingContractsCount(), summary.paidContractsCount(),
                summary.totalPending(), summary.totalPaid(),
                contractResponses);
    }

    public List<ContractCommercialCommissionResponse> listCommercialContracts(Long commercialId, CommissionStatus statusFilter) {
        requireAdmin();
        Commercial commercial = resolveCommercial(commercialId);
        Long salesRepId = commercial.getProfile().getUser().getId();

        List<Contract> contracts = statusFilter == null
                ? contractRepository.findByStatusAndPartenaireIsNullAndSalesRepId(ContractStatus.signed, salesRepId)
                : contractRepository.findByStatusAndPartenaireIsNullAndSalesRepIdAndCommercialCommissionStatus(
                        ContractStatus.signed, salesRepId, statusFilter);

        return contracts.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toContractResponse)
                .toList();
    }

    /**
     * Volontairement PAS @Transactional au niveau methode (cf. Javadoc de la classe) : chaque contrat
     * declenche un appel reseau reel vers SycaPay, jamais execute sous une transaction DB ouverte.
     * Les controles d'eligibilite (resolveEligibleContract) restent verifies pour TOUS les contrats
     * AVANT le premier appel SycaPay - si un seul est inegible, aucun virement n'est tente (comportement
     * inchange), mais une fois cette etape passee, chaque contrat est traite independamment : l'echec
     * de l'un n'annule jamais le succes deja acquis (et deja payé cote Wave) d'un autre.
     */
    public List<CommercialCommissionPaymentResponse> pay(CommercialCommissionPaymentRequest request) {
        requireAdmin();
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User paidBy = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        List<Contract> contracts = request.contractIds().stream()
                .map(this::resolveEligibleContract)
                .toList();
        contracts.forEach(this::verifierAucuneDemandeDePaiement);

        // Mode hybride (decision utilisateur du 2026-09-26, meme regle que CommissionService.pay()) : SycaPay
        // desactive -> enregistrement d'un paiement fait hors application au lieu d'un refus (502).
        if (!sycaPayProperties.isEnabled()) {
            return enregistrerPaiementsHorsApplication(contracts, paidBy, request.reference());
        }

        List<PaymentAttemptResult> results = contracts.stream()
                .map(contract -> attemptPayment(contract, paidBy, request.reference()))
                .toList();

        List<CommercialCommissionPayment> successfulPayments = results.stream()
                .map(PaymentAttemptResult::payment)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, Boolean> notificationParCommercial = notifierCommerciaux(successfulPayments, request.reference());
        enregistrerResultatsNotification(successfulPayments, notificationParCommercial);

        return results.stream()
                .map(result -> toPaymentResponse(result,
                        result.commercial() == null ? null : notificationParCommercial.get(result.commercial().getId())))
                .toList();
    }

    /** Resout le commercial puis tente le virement Wave via SycaPay AVANT tout enregistrement local de
     * paiement (section 9 : le paiement local n'existe que si le virement est effectivement confirme). */
    private PaymentAttemptResult attemptPayment(Contract contract, User paidBy, String reference) {
        Commercial commercial = commercialRepository.findByProfile_UserId(contract.getSalesRep().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Aucune fiche commerciale liee au vendeur du contrat " + contract.getPolicyNumber()));

        SycaPayPaymentCommand command = new SycaPayPaymentCommand(
                contract.getId(), commercial.getId(), contract.getCommercialCommissionAmount(),
                nomCommercial(commercial), commercial.getProfile().getPhone());

        SycaPayPaymentOutcome outcome;
        try {
            outcome = sycaPayPaymentService.payCommission(command);
        } catch (SycaPayException e) {
            // Identifiants incomplets, integration coupee entre-temps... : aucun virement n'est parti, le
            // contrat est REJECTED au lieu de faire echouer toute la requete.
            log.warn("[COMMISSION-COMMERCIALE] contrat {} : virement impossible ({})", contract.getPolicyNumber(), e.getMessage());
            return new PaymentAttemptResult(contract, commercial, null, "REJECTED", null,
                    "Service de paiement SycaPay indisponible - aucun virement n'est parti", null);
        }

        if (outcome.isSuccess()) {
            CommercialCommissionPayment payment = recordPayment(contract, commercial, paidBy, reference, outcome.transactionId());
            return new PaymentAttemptResult(contract, commercial, payment,
                    outcome.status().name(), outcome.transactionReference(), outcome.message(), outcome.transactionId());
        }

        String statut = outcome.rejected() ? "REJECTED" : outcome.status().name();
        return new PaymentAttemptResult(contract, commercial, null, statut, outcome.transactionReference(), outcome.message(),
                outcome.transactionId());
    }

    /**
     * SycaPay desactive : paiement fait hors application (Wave depuis le telephone de la societe, virement
     * bancaire...) puis ENREGISTRE ici - tous les contrats ou aucun, comme avant l'integration SycaPay.
     * SMS de confirmation au commercial ; modePaiement = ENREGISTREMENT_MANUEL.
     */
    private List<CommercialCommissionPaymentResponse> enregistrerPaiementsHorsApplication(List<Contract> contracts, User paidBy,
                                                                                         String reference) {
        List<CommercialCommissionPayment> payments = new TransactionTemplate(transactionManager).execute(statut ->
                contracts.stream()
                        .map(contract -> recordPayment(contract, commercialDuVendeur(contract), paidBy, reference, null))
                        .toList());
        Map<Long, Boolean> notificationParCommercial = notifierCommerciaux(payments, reference);
        enregistrerResultatsNotification(payments, notificationParCommercial);
        return payments.stream()
                .map(payment -> toPaymentResponse(payment, notificationParCommercial.get(payment.getCommercial().getId())))
                .toList();
    }

    private Commercial commercialDuVendeur(Contract contract) {
        return commercialRepository.findByProfile_UserId(contract.getSalesRep().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Aucune fiche commerciale liee au vendeur du contrat " + contract.getPolicyNumber()));
    }

    /** Regroupe le resultat d'une tentative de paiement pour un contrat : payment() est non-null
     * UNIQUEMENT si le virement SycaPay a ete confirme reussi (cf. attemptPayment). */
    private record PaymentAttemptResult(
            Contract contract,
            Commercial commercial,
            CommercialCommissionPayment payment,
            String sycapayStatus,
            String sycapayTransactionReference,
            String sycapayMessage,
            Long sycapayTransactionPk
    ) {
    }

    public PageResponse<CommercialCommissionPaymentResponse> getPaymentHistory(Long commercialId, Integer page, Integer size) {
        requireAdmin();
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "paidAt"));

        Page<CommercialCommissionPayment> result = commercialId == null
                ? commercialCommissionPaymentRepository.findAll(pageRequest)
                : commercialCommissionPaymentRepository.findByCommercialId(commercialId, pageRequest);

        // Une seule requete pour les transactions SycaPay de la page (paiements lies a un virement).
        Map<Long, SycaPayTransaction> transactions = sycaPayTransactionRepository.findAllById(
                        result.getContent().stream().map(CommercialCommissionPayment::getSycapayTransactionId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(SycaPayTransaction::getId, t -> t));

        return new PageResponse<>(
                result.getContent().stream()
                        .map(payment -> toPaymentResponse(payment, null, transactions.get(payment.getSycapayTransactionId())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * Enregistre le paiement d'une commission commerciale dont le virement SycaPay, reste PROCESSING/
     * UNKNOWN, vient d'etre CONFIRME par une reconciliation (SycaPayPaymentConfirmedEvent, 2026-09-23) -
     * meme mecanisme que les commissions partenaire. Sans cela, le virement etait bien parti mais la
     * commission restait "a payer" (pas de double virement possible - SycaPay rejette - mais un
     * historique et une comptabilite faux). paidBy = l'admin qui avait lance le paiement (created_by
     * de la transaction), a defaut celui qui a reconcilie. Idempotent ; ignore les transactions
     * partenaire. Jamais d'appel SycaPay ici : uniquement de l'enregistrement.
     */
    @Transactional
    public void enregistrerVirementConfirme(Long sycapayTransactionId, String confirmePar) {
        SycaPayTransaction transaction = sycaPayTransactionRepository.findById(sycapayTransactionId).orElse(null);
        if (transaction == null
                || transaction.getPartialPaymentRequestId() != null // virement d'une demande : CommissionPartialPaymentService
                || transaction.getContractId() == null // virement d'un bonus mensuel : CommercialPerformanceService
                || transaction.getBeneficiaryType() != SycaPayBeneficiaryType.COMMERCIAL
                || transaction.getStatus() != SycaPayTransactionStatus.SUCCESS
                || commercialCommissionPaymentRepository.existsBySycapayTransactionId(sycapayTransactionId)) {
            return;
        }

        Contract contract = contractRepository.findById(transaction.getContractId())
                .orElseThrow(() -> new IllegalStateException("Contrat introuvable: " + transaction.getContractId()));
        if (contract.getCommercialCommissionStatus() == CommissionStatus.paid) {
            log.error("[COMMISSION-COMMERCIALE] virement {} confirme mais commission du contrat {} deja payee - DOUBLE PAIEMENT A VERIFIER",
                    transaction.getTransactionReference(), contract.getPolicyNumber());
            telegramNotificationService.envoyer("ALERTE double paiement possible : virement SycaPay "
                    + transaction.getTransactionReference() + " (" + transaction.getAmount() + " FCFA) confirme pour le contrat "
                    + contract.getPolicyNumber() + " dont la commission commerciale etait deja payee. Verification manuelle requise.");
            return;
        }
        Commercial commercial = commercialRepository.findById(transaction.getCommercialId())
                .orElseThrow(() -> new IllegalStateException("Commercial introuvable: " + transaction.getCommercialId()));
        User paidBy = trouverUtilisateur(transaction.getCreatedBy())
                .or(() -> trouverUtilisateur(confirmePar))
                .orElseThrow(() -> new IllegalStateException("Aucun admin a rattacher au paiement du virement "
                        + transaction.getTransactionReference()));

        CommercialCommissionPayment payment = recordPayment(contract, commercial, paidBy,
                transaction.getTransactionReference(), transaction.getId());
        enregistrerResultatsNotification(List.of(payment), notifierCommerciaux(List.of(payment), transaction.getTransactionReference()));
    }

    /** Resultat du SMS conserve sur chaque paiement (un SMS agrege par commercial), pour l'historique. */
    private void enregistrerResultatsNotification(List<CommercialCommissionPayment> payments, Map<Long, Boolean> resultatParCommercial) {
        if (payments.isEmpty()) {
            return;
        }
        payments.forEach(payment -> payment.setNotificationEnvoyee(resultatParCommercial.get(payment.getCommercial().getId())));
        commercialCommissionPaymentRepository.saveAll(payments);
    }

    private Optional<User> trouverUtilisateur(String username) {
        return StringUtils.hasText(username) ? userRepository.findByUsername(username) : Optional.empty();
    }

    /** Appelee UNIQUEMENT apres confirmation SUCCESS du virement SycaPay (cf. attemptPayment) - le
     * commercial est deja resolu par l'appelant, pas de second appel repository ici. */
    private CommercialCommissionPayment recordPayment(Contract contract, Commercial commercial, User paidBy, String reference,
                                                      Long sycapayTransactionId) {
        contract.setCommercialCommissionStatus(CommissionStatus.paid);
        contractRepository.save(contract);

        CommercialCommissionPayment payment = CommercialCommissionPayment.builder()
                .contract(contract)
                .commercial(commercial)
                .amount(contract.getCommercialCommissionAmount())
                .paidBy(paidBy)
                .reference(reference)
                .sycapayTransactionId(sycapayTransactionId)
                .build();
        payment = commercialCommissionPaymentRepository.save(payment);

        ledgerService.enregistrerCommissionCommerciale(contract, payment.getAmount());

        return payment;
    }

    /** Un seul SMS agrege par commercial DISTINCT concerne (montant total + nombre de contrats), jamais
     * un SMS par contrat - meme convention que CommissionService.notifierPartenaires. L'echec d'un
     * envoi ne fait jamais echouer le paiement (deja acquis en base a cette etape). */
    private Map<Long, Boolean> notifierCommerciaux(List<CommercialCommissionPayment> payments, String reference) {
        Map<Long, List<CommercialCommissionPayment>> parCommercial = payments.stream()
                .collect(Collectors.groupingBy(payment -> payment.getCommercial().getId()));

        Map<Long, Boolean> resultat = new HashMap<>();
        for (List<CommercialCommissionPayment> groupe : parCommercial.values()) {
            Commercial commercial = groupe.get(0).getCommercial();
            long montantTotal = groupe.stream().mapToLong(p -> p.getAmount() == null ? 0 : p.getAmount()).sum();

            boolean notifie = smsNotificationService.sendFromTemplate(
                    commercial.getProfile().getPhone(),
                    TEMPLATE_COMMISSION_COMMERCIALE_PAYEE,
                    Map.of(
                            "montant", formatMontant(montantTotal),
                            "nombreContrats", String.valueOf(groupe.size()),
                            "reference", StringUtils.hasText(reference) ? reference : "N/A"
                    ),
                    TypeMessageSms.commission_commerciale_payee
            );
            resultat.put(commercial.getId(), notifie);
            if (commercial.getProfile() != null && commercial.getProfile().getUser() != null) {
                boolean unSeul = groupe.size() == 1 && groupe.get(0).getContract() != null;
                eventPublisher.publishEvent(NotificationUtilisateurEvent.pourUtilisateur(commercial.getProfile().getUser().getId(),
                        PartenaireNotificationType.COMMISSION_COMMERCIALE_PAYEE, "Commission payée",
                        "Vos commissions (" + groupe.size() + " contrat(s)) ont été payées : " + formatMontant(montantTotal) + " FCFA.",
                        montantTotal, unSeul ? "CONTRAT" : null, unSeul ? groupe.get(0).getContract().getId() : null));
            }

            telegramNotificationService.envoyer(
                    "<b>Commission commerciale payee</b>\n"
                            + TelegramNotificationService.echapperHtml(nomCommercial(commercial))
                            + " : " + formatMontant(montantTotal) + " FCFA (" + groupe.size() + " contrat(s))");
        }
        return resultat;
    }

    private String formatMontant(long montant) {
        String brut = String.valueOf(montant);
        StringBuilder resultat = new StringBuilder();
        int compteur = 0;
        for (int i = brut.length() - 1; i >= 0; i--) {
            resultat.append(brut.charAt(i));
            compteur++;
            if (compteur % 3 == 0 && i != 0) {
                resultat.append(' ');
            }
        }
        return resultat.reverse().toString();
    }

    /**
     * Commission commerciale en attente du commercial (contrats signes, vente directe, commission > 0 et
     * non payee) - base du solde disponible des demandes de paiement (2026-09-25), meme calcul que
     * totalPending de /commercials/me/commissions.
     */
    public long totalPendingFor(Commercial commercial) {
        Long userId = commercial.getProfile().getUser().getId();
        return summarize(commercial, contractRepository.findByStatusAndPartenaireIsNullAndSalesRepId(ContractStatus.signed, userId))
                .totalPending();
    }

    /**
     * Anti double versement (meme regle que CommissionService.aDesPaiementsPartielsEnCours cote
     * partenaire, 2026-09-25) : des qu'un commercial a une demande de paiement en attente ou payee,
     * sa commission n'est plus payable contrat par contrat - la demande porte sur son solde agrege
     * sans marquer les contrats payes, un paiement par contrat verserait deux fois le meme montant.
     */
    private void verifierAucuneDemandeDePaiement(Contract contract) {
        commercialRepository.findByProfile_UserId(contract.getSalesRep().getId())
                .filter(commercial -> paymentRequestRepository.existsByCommercialIdAndStatusIn(commercial.getId(),
                        List.of(PartialPaymentRequestStatus.values())))
                .ifPresent(commercial -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Ce commercial a des demandes de paiement de commission - sa commission ne peut plus etre payee contrat par contrat"
                                    + " (contrat " + contract.getPolicyNumber() + ")");
                });
    }

    private Contract resolveEligibleContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + contractId));

        if (contract.getPartenaire() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contrat " + contract.getPolicyNumber() + " est rattache a un partenaire - la commission revient au partenaire, pas au commercial");
        }
        if (contract.getStatus() != ContractStatus.signed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contrat " + contract.getPolicyNumber() + " n'est pas signe (statut: " + contract.getStatus() + ")");
        }
        if (contract.getCommercialCommissionStatus() == CommissionStatus.paid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Commission commerciale deja payee pour le contrat " + contract.getPolicyNumber());
        }
        if (contract.getCommercialCommissionAmount() == null || contract.getCommercialCommissionAmount() <= 0L) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aucune commission commerciale due pour le contrat " + contract.getPolicyNumber()
                            + " (pas de commission commerciale configuree pour ce produit)");
        }

        return contract;
    }

    private Commercial resolveCommercial(Long commercialId) {
        return commercialRepository.findById(commercialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commercial introuvable: " + commercialId));
    }

    private void requireAdmin() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() == null || !principal.roles().toUpperCase(java.util.Locale.ROOT).contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }

    private CommercialCommissionSummaryResponse summarize(Commercial commercial, List<Contract> eligible) {
        Long userId = commercial.getProfile().getUser().getId();
        long pendingCount = 0;
        long paidCount = 0;
        long totalPending = 0;
        long totalPaid = 0;

        for (Contract contract : eligible) {
            if (contract.getSalesRep() == null || !contract.getSalesRep().getId().equals(userId)) {
                continue;
            }
            long amount = contract.getCommercialCommissionAmount() == null ? 0 : contract.getCommercialCommissionAmount();
            if (amount <= 0) {
                continue;
            }
            if (contract.getCommercialCommissionStatus() == CommissionStatus.paid) {
                paidCount++;
                totalPaid += amount;
            } else {
                pendingCount++;
                totalPending += amount;
            }
        }

        return new CommercialCommissionSummaryResponse(
                commercial.getId(),
                commercial.getEmployeeCode(),
                nomCommercial(commercial),
                pendingCount,
                paidCount,
                totalPending,
                totalPaid);
    }

    private String nomCommercial(Commercial commercial) {
        return commercial.getProfile().getFirstname() + " " + commercial.getProfile().getLastname();
    }

    private ContractCommercialCommissionResponse toContractResponse(Contract contract) {
        return new ContractCommercialCommissionResponse(
                contract.getId(),
                contract.getPolicyNumber(),
                contract.getSignedAt(),
                contract.getCommercialCommissionAmount(),
                contract.getCommercialCommissionStatus());
    }

    private CommercialCommissionPaymentResponse toPaymentResponse(CommercialCommissionPayment payment, Boolean notificationEnvoyee) {
        return toPaymentResponse(payment, notificationEnvoyee, null);
    }

    /** Lecture historique (getPaymentHistory) : champs SycaPay renseignes depuis la transaction liee
     * (depuis le 2026-09-23) - null pour un paiement anterieur, sans transaction rattachee. */
    private CommercialCommissionPaymentResponse toPaymentResponse(CommercialCommissionPayment payment, Boolean notificationEnvoyee,
                                                                  SycaPayTransaction transaction) {
        Contract contract = payment.getContract();
        Commercial commercial = payment.getCommercial();
        User paidBy = payment.getPaidBy();

        // Historique tolerant (2026-09-23) : une ligne liee a un contrat/commercial/utilisateur supprime
        // reste affichee au lieu de faire echouer toute la page (cf. SafeAccess.idOf).
        return new CommercialCommissionPaymentResponse(
                payment.getId(),
                contract == null ? null : SafeAccess.idOf(contract, contract::getId),
                contract == null ? null : SafeAccess.safe(contract::getPolicyNumber),
                commercial == null ? null : SafeAccess.idOf(commercial, commercial::getId),
                commercial == null ? null : Optional.ofNullable(SafeAccess.safe(() -> nomCommercial(commercial))).orElse(COMMERCIAL_SUPPRIME),
                payment.getAmount(),
                paidBy == null ? null : SafeAccess.idOf(paidBy, paidBy::getId),
                paidBy == null ? null : SafeAccess.safe(paidBy::getUsername),
                payment.getReference(),
                payment.getPaidAt(),
                notificationEnvoyee != null ? notificationEnvoyee : payment.getNotificationEnvoyee(),
                transaction == null ? null : transaction.getStatus().name(),
                transaction == null ? null : transaction.getTransactionReference(),
                transaction == null ? null : transaction.getSycapayResponseMessage(),
                transaction == null ? null : transaction.getId(),
                payment.getSycapayTransactionId() != null ? MODE_VIREMENT : MODE_HORS_APPLICATION);
    }

    /** Reponse de pay() pour UN contrat, que le virement SycaPay ait reussi ou non (cf. Javadoc de
     * PaymentAttemptResult) - id/paidAt/reference ne sont renseignes que si payment() est non-null. */
    private CommercialCommissionPaymentResponse toPaymentResponse(PaymentAttemptResult result, Boolean notificationEnvoyee) {
        if (result.payment() != null) {
            CommercialCommissionPaymentResponse base = toPaymentResponse(result.payment(), notificationEnvoyee);
            return new CommercialCommissionPaymentResponse(
                    base.id(), base.contractId(), base.policyNumber(), base.commercialId(), base.commercialNom(),
                    base.amount(), base.paidByUserId(), base.paidByUsername(), base.reference(), base.paidAt(),
                    base.notificationEnvoyee(), result.sycapayStatus(), result.sycapayTransactionReference(), result.sycapayMessage(),
                    result.sycapayTransactionPk(), MODE_VIREMENT);
        }

        Contract contract = result.contract();
        Commercial commercial = result.commercial();
        return new CommercialCommissionPaymentResponse(
                null,
                contract.getId(),
                contract.getPolicyNumber(),
                commercial == null ? null : commercial.getId(),
                commercial == null ? null : nomCommercial(commercial),
                contract.getCommercialCommissionAmount(),
                null, null, null, null, null,
                result.sycapayStatus(), result.sycapayTransactionReference(), result.sycapayMessage(),
                result.sycapayTransactionPk(), MODE_VIREMENT);
    }
}
