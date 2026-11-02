package ci.assurzen.services;

import ci.assurzen.dto.CommissionPaymentRequest;
import ci.assurzen.dto.CommissionPaymentResponse;
import ci.assurzen.dto.ContractCommissionResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PartnerCommissionSummaryResponse;
import ci.assurzen.entities.CommissionPayment;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.entities.User;
import ci.assurzen.metrics.BusinessMetrics;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.repositories.CommissionPaymentRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayDuplicatePaymentException;
import ci.assurzen.sycapay.exception.SycaPayException;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import ci.assurzen.utils.SafeAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import static ci.assurzen.utils.SafeAccess.safe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionService {

    private static final String TEMPLATE_COMMISSION_PAYEE = "COMMISSION_PAYEE";
    private static final ZoneId ZONE_ID = ZoneId.of("Africa/Abidjan");
    static final String PARTENAIRE_SUPPRIME = "Partenaire supprime";
    /** CommissionPaymentResponse.modePaiement (2026-09-26). */
    static final String MODE_VIREMENT = "VIREMENT_SYCAPAY";
    static final String MODE_HORS_APPLICATION = "ENREGISTREMENT_MANUEL";
    /** Contrat entierement couvert par des avances deja versees (demandes de paiement partiel). */
    static final String MODE_AVANCE = "AVANCE_IMPUTEE";

    private final ContractRepository contractRepository;
    private final PartenaireRepository partenaireRepository;
    private final CommissionPaymentRepository commissionPaymentRepository;
    private final CommissionPartialPaymentRequestRepository commissionPartialPaymentRequestRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final BusinessMetrics businessMetrics;
    private final LedgerService ledgerService;
    private final SmsNotificationService smsNotificationService;
    private final TelegramNotificationService telegramNotificationService;
    private final SycaPayTransactionRepository sycaPayTransactionRepository;
    private final SycaPayProperties sycaPayProperties;
    private final SycaPayPaymentService sycaPayPaymentService;
    private final PlatformTransactionManager transactionManager;

    /** Statuts SycaPay qui interdisent un paiement MANUEL du meme contrat (2026-09-23) : virement en
     * cours, au resultat incertain, ou deja reussi - seul FAILED (ou aucune transaction) laisse la
     * commission payable a la main. */
    private static final Set<SycaPayTransactionStatus> STATUTS_SYCAPAY_BLOQUANTS = EnumSet.of(
            SycaPayTransactionStatus.PENDING, SycaPayTransactionStatus.PROCESSING,
            SycaPayTransactionStatus.UNKNOWN, SycaPayTransactionStatus.SUCCESS);

    /**
     * Somme de Contract.partnerShare des contrats signed/pending d'un partenaire (meme calcul que
     * summarize().totalPending, extrait ici pour reutilisation par CommissionPartialPaymentService -
     * contrat "Espace Partenaire" §2, 2026-08-21).
     */
    public long totalPendingFor(Long partenaireId) {
        return contractRepository.findByStatusAndPartenaireIdAndCommissionStatus(
                        ContractStatus.signed, partenaireId, CommissionStatus.pending)
                .stream()
                .mapToLong(c -> c.getPartnerShare() == null ? 0 : c.getPartnerShare())
                .sum();
    }

    /**
     * Vrai si ce partenaire a une demande de paiement partiel EN ATTENTE, ou une avance deja versee pas
     * encore imputee sur des contrats (2026-09-26). Utilise par le paiement AUTOMATIQUE (partenaire
     * IMMEDIAT), qui verserait sinon la totalite d'une commission deja partiellement avancee. Le paiement
     * MANUEL, lui, impute les avances (pay()) et ne bloque que sur une demande en attente.
     */
    public boolean aDesPaiementsPartielsEnCours(Long partenaireId) {
        return aDesDemandesPartiellesEnAttente(partenaireId) || avancesDe(partenaireId).disponible() > 0;
    }

    /** Demande de paiement partiel pas encore validee : son montant n'est pas encore fixe comme avance. */
    public boolean aDesDemandesPartiellesEnAttente(Long partenaireId) {
        // En attente, en cours de virement ou en echec (montant reserve) : pas encore une avance acquise.
        return !commissionPartialPaymentRequestRepository.findByPartenaireIdAndStatusIn(partenaireId, List.of(
                PartialPaymentRequestStatus.pending, PartialPaymentRequestStatus.processing, PartialPaymentRequestStatus.failed)).isEmpty();
    }

    private AvancesPartenaire avancesDe(Long partenaireId) {
        return new AvancesPartenaire(commissionPartialPaymentRequestRepository
                .findByPartenaireIdAndStatusOrderByRequestedAtAsc(partenaireId, PartialPaymentRequestStatus.paid));
    }

    public List<PartnerCommissionSummaryResponse> listPartnerSummaries() {
        List<Contract> eligible = contractRepository.findByStatusAndPartenaireIsNotNull(ContractStatus.signed);

        return partenaireRepository.findAll().stream()
                .map(partenaire -> summarize(partenaire, eligible))
                .filter(summary -> summary.pendingContractsCount() > 0 || summary.paidContractsCount() > 0)
                .toList();
    }

    public List<ContractCommissionResponse> listPartnerContracts(Long partenaireId, CommissionStatus statusFilter) {
        resolvePartenaire(partenaireId);

        List<Contract> contracts = statusFilter == null
                ? contractRepository.findByStatusAndPartenaireId(ContractStatus.signed, partenaireId)
                : contractRepository.findByStatusAndPartenaireIdAndCommissionStatus(ContractStatus.signed, partenaireId, statusFilter);

        return contracts.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toContractResponse)
                .toList();
    }

    /**
     * Paiement MANUEL (admin) des commissions partenaire - hybride (decision utilisateur du 2026-09-26) :
     * - SycaPay active : VIREMENT Wave reel vers le numero de commission du partenaire, contrat par
     *   contrat et independamment (plus de "tout ou rien" : un virement deja parti ne peut pas etre
     *   annule par l'echec d'un autre). Seul un SUCCESS enregistre le paiement ; FAILED laisse la
     *   commission payable ; PROCESSING/UNKNOWN la bloquent jusqu'a reconciliation (garde 409) ; REJECTED
     *   = aucune tentative (numero Wave absent, virement deja en cours, service indisponible...).
     * - SycaPay desactive : ENREGISTREMENT d'un paiement fait hors application (comportement historique,
     *   "tout ou rien" dans une transaction), signale par modePaiement = ENREGISTREMENT_MANUEL.
     * Volontairement PAS @Transactional au niveau methode : aucun appel reseau SycaPay sous une
     * transaction ouverte (meme regle que CommercialCommissionService.pay()).
     * Plus de SMS "demande recue" avant traitement (decision du 2026-09-26) : un seul SMS au partenaire,
     * apres un paiement effectivement enregistre.
     * IMPUTATION DES AVANCES (2026-09-26) : les demandes de paiement partiel deja payees sont des avances
     * sur le solde, rattachees a aucun contrat. Chaque contrat paye ici consomme d'abord ces avances (les
     * plus anciennes d'abord) ; seul le reste est verse (ou enregistre). Contrat entierement couvert :
     * aucun virement, le contrat est simplement marque paye (modePaiement AVANCE_IMPUTEE).
     */
    public List<CommissionPaymentResponse> pay(CommissionPaymentRequest request) {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User paidBy = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        List<Contract> contracts = request.contractIds().stream()
                .map(this::resolveEligibleContract)
                .toList();

        alerterAdminsAvantTraitement(contracts, request.reference());

        Map<Long, AvancesPartenaire> avances = new HashMap<>();
        contracts.forEach(contract -> avances.computeIfAbsent(contract.getPartenaire().getId(), this::avancesDe));

        return sycaPayProperties.isEnabled()
                ? payerParVirement(contracts, avances, paidBy, request.reference())
                : enregistrerPaiementsHorsApplication(contracts, avances, paidBy, request.reference());
    }

    private static AvancesPartenaire.Reservation reserverAvance(Map<Long, AvancesPartenaire> avances, Contract contract) {
        long part = contract.getPartnerShare() == null ? 0L : contract.getPartnerShare();
        return avances.get(contract.getPartenaire().getId()).reserver(part);
    }

    /** Enregistre la consommation des avances (montantImpute) d'un paiement enregistre. */
    private void imputer(AvancesPartenaire.Reservation reservation) {
        reservation.parDemande().forEach((idDemande, montant) -> commissionPartialPaymentRequestRepository.findById(idDemande)
                .ifPresent(demande -> {
                    demande.setMontantImpute((demande.getMontantImpute() == null ? 0L : demande.getMontantImpute()) + montant);
                    commissionPartialPaymentRequestRepository.save(demande);
                }));
    }

    /** SycaPay desactive : comportement historique, tous les contrats ou aucun. */
    private List<CommissionPaymentResponse> enregistrerPaiementsHorsApplication(List<Contract> contracts,
                                                                                Map<Long, AvancesPartenaire> avances,
                                                                                User paidBy, String reference) {
        return new TransactionTemplate(transactionManager).execute(statut -> {
            List<CommissionPayment> payments = contracts.stream()
                    .map(contract -> {
                        AvancesPartenaire.Reservation avance = reserverAvance(avances, contract);
                        imputer(avance);
                        return recordPayment(contract, paidBy, reference, null, avance);
                    })
                    .toList();
            Map<Long, Boolean> notificationParPartenaire = notifierEtTracer(payments, reference);
            return payments.stream()
                    .map(payment -> toPaymentResponse(payment, notificationParPartenaire.get(idPartenaire(payment))))
                    .toList();
        });
    }

    /** SycaPay active : un virement Wave par contrat, chacun traite independamment. */
    private List<CommissionPaymentResponse> payerParVirement(List<Contract> contracts, Map<Long, AvancesPartenaire> avances,
                                                             User paidBy, String reference) {
        List<TentativeVirement> tentatives = contracts.stream()
                .map(contract -> tenterVirement(contract, avances, paidBy, reference))
                .toList();

        List<CommissionPayment> reussis = tentatives.stream().map(TentativeVirement::payment).filter(Objects::nonNull).toList();
        Map<Long, Boolean> notificationParPartenaire = reussis.isEmpty() ? Map.of() : notifierEtTracer(reussis, reference);

        return tentatives.stream()
                .map(tentative -> toPaymentResponse(tentative, notificationParPartenaire))
                .toList();
    }

    private TentativeVirement tenterVirement(Contract contract, Map<Long, AvancesPartenaire> avances, User paidBy, String reference) {
        Partenaire partenaire = contract.getPartenaire();
        String nomPartenaire = (partenaire.getPrenoms() == null ? "" : partenaire.getPrenoms() + " ") + partenaire.getNom();
        AvancesPartenaire.Reservation avance = reserverAvance(avances, contract);
        long aVerser = (contract.getPartnerShare() == null ? 0L : contract.getPartnerShare()) - avance.total();
        if (aVerser <= 0) {
            // Entierement couvert par des avances deja versees : aucun virement, contrat marque paye.
            CommissionPayment payment = new TransactionTemplate(transactionManager).execute(statut -> {
                imputer(avance);
                return recordPayment(contract, paidBy, reference, null, avance);
            });
            return new TentativeVirement(contract, payment, null, null, null, "Commission couverte par les avances deja versees");
        }
        TentativeVirement tentative = virer(contract, partenaire, nomPartenaire, aVerser, avance, paidBy, reference);
        if (tentative.payment() == null) {
            avances.get(partenaire.getId()).liberer(avance); // rien n'est parti : l'avance reste disponible
        }
        return tentative;
    }

    private TentativeVirement virer(Contract contract, Partenaire partenaire, String nomPartenaire, long aVerser,
                                    AvancesPartenaire.Reservation avance, User paidBy, String reference) {
        if (!StringUtils.hasText(partenaire.getNumeroWaveCommission())) {
            return TentativeVirement.rejetee(contract, "Numero Wave de commission non renseigne pour le partenaire " + nomPartenaire
                    + " - a renseigner sur sa fiche avant le paiement");
        }

        SycaPayPaymentOutcome outcome;
        try {
            outcome = sycaPayPaymentService.payCommission(SycaPayPaymentCommand.forPartenaire(contract.getId(), partenaire.getId(),
                    aVerser, nomPartenaire, partenaire.getNumeroWaveCommission()));
        } catch (SycaPayException e) {
            // Identifiants incomplets, integration coupee entre-temps... : aucun virement n'est parti.
            log.warn("[COMMISSION] contrat {} : virement impossible ({})", contract.getPolicyNumber(), e.getMessage());
            return TentativeVirement.rejetee(contract, "Service de paiement SycaPay indisponible - aucun virement n'est parti");
        }

        if (!outcome.isSuccess()) {
            return new TentativeVirement(contract, null, outcome.rejected() ? "REJECTED" : outcome.status().name(),
                    outcome.transactionReference(), outcome.transactionId(), outcome.message());
        }
        CommissionPayment payment = new TransactionTemplate(transactionManager).execute(statut -> {
            imputer(avance);
            return recordPayment(contract, paidBy, reference, outcome.transactionId(), avance);
        });
        return new TentativeVirement(contract, payment, SycaPayTransactionStatus.SUCCESS.name(),
                outcome.transactionReference(), outcome.transactionId(), outcome.message());
    }

    /** Resultat d'un virement pour UN contrat : payment non null uniquement si SUCCESS. */
    private record TentativeVirement(Contract contract, CommissionPayment payment, String sycapayStatus,
                                     String sycapayTransactionReference, Long sycapayTransactionPk, String sycapayMessage) {
        static TentativeVirement rejetee(Contract contract, String message) {
            return new TentativeVirement(contract, null, "REJECTED", null, null, message);
        }
    }

    /** SMS de confirmation (un par partenaire) puis resultat conserve sur chaque paiement. */
    private Map<Long, Boolean> notifierEtTracer(List<CommissionPayment> payments, String reference) {
        Map<Long, Boolean> notificationParPartenaire = notifierPartenaires(payments, reference);
        payments.forEach(payment -> payment.setNotificationEnvoyee(notificationParPartenaire.get(idPartenaire(payment))));
        commissionPaymentRepository.saveAll(payments);
        return notificationParPartenaire;
    }

    private static Long idPartenaire(CommissionPayment payment) {
        return payment.getPartenaire() == null ? null : payment.getPartenaire().getId();
    }

    /**
     * AVANT tout enregistrement en base (retour utilisateur, 2026-08-30), pour chaque partenaire
     * distinct concerne par cette demande de paiement (regroupement par Contract.partenaire, comme
     * notifierPartenaires ci-dessous) :
     * - SMS au partenaire l'informant que sa demande de paiement de commission a ete soumise avec
     *   succes (le SMS confirmant le versement effectif, lui, reste envoye APRES par
     *   notifierPartenaires - le partenaire recoit donc 2 SMS distincts pour un seul appel a pay()) ;
     * - alerte Telegram admin/finance (meme canal que CommissionPartialPaymentService), precisant
     *   qu'il s'agit d'un paiement TOTAL par contrat (par opposition a la demande de paiement PARTIEL
     *   initiee par le partenaire lui-meme, qui a sa propre alerte Telegram distincte). "Initiee par"
     *   affiche le numero du PARTENAIRE (choix utilisateur, 2026-08-30), pas l'admin qui a appele
     *   POST /commissions/pay - deja repete via la ligne "Partenaire" au-dessus, mais c'est voulu.
     * Base sur Contract.partnerShare (le montant qui sera effectivement verse). Best-effort : un echec
     * d'un des deux canaux ne fait jamais echouer le traitement de la demande.
     */
    private void alerterAdminsAvantTraitement(List<Contract> contracts, String reference) {
        Map<Long, List<Contract>> parPartenaire = contracts.stream()
                .filter(contract -> contract.getPartenaire() != null)
                .collect(Collectors.groupingBy(contract -> contract.getPartenaire().getId()));

        String date = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(ZONE_ID).format(Instant.now());
        String referenceAffichee = StringUtils.hasText(reference) ? reference : "N/A";

        for (List<Contract> groupe : parPartenaire.values()) {
            Partenaire partenaire = groupe.get(0).getPartenaire();
            long montantTotal = groupe.stream().mapToLong(c -> c.getPartnerShare() == null ? 0 : c.getPartnerShare()).sum();
            String nomPartenaire = (partenaire.getPrenoms() == null ? "" : partenaire.getPrenoms() + " ") + partenaire.getNom();
            String telephone = StringUtils.hasText(partenaire.getTelephone()) ? partenaire.getTelephone() : "non renseigne";

            telegramNotificationService.envoyer(
                    "Demande de paiement TOTAL de commission recue : " + formatMontant(montantTotal) + " FCFA \n"
                            + "- Partenaire : <b>" + TelegramNotificationService.echapperHtml(nomPartenaire)
                            + " (" + TelegramNotificationService.echapperHtml(telephone) + ")</b> \n"
                            + "- " + groupe.size() + " contrat(s), reference : " + referenceAffichee + " \n"
                            + "- Initiee par : " + telephone + " \n"
                            + "- Date et heure : " + date);
        }
    }

    /**
     * Un seul SMS agrege par partenaire DISTINCT concerne par ce paiement (montant total + nombre de
     * contrats), jamais un SMS par contrat - meme si plusieurs contrats de plusieurs partenaires sont
     * payes en un seul appel (contrat "SMS de confirmation au partenaire lors du paiement d'une
     * commission", 2026-08-25). L'echec d'un envoi ne fait jamais echouer le paiement lui-meme (deja
     * acquis en base a cette etape) - sendFromTemplate() ne leve jamais, elle renvoie juste un boolean.
     */
    private Map<Long, Boolean> notifierPartenaires(List<CommissionPayment> payments, String reference) {
        Map<Long, List<CommissionPayment>> parPartenaire = payments.stream()
                .filter(payment -> payment.getPartenaire() != null)
                .collect(Collectors.groupingBy(payment -> payment.getPartenaire().getId()));

        Map<Long, Boolean> resultat = new HashMap<>();
        for (List<CommissionPayment> groupe : parPartenaire.values()) {
            Partenaire partenaire = groupe.get(0).getPartenaire();
            // Montant reellement verse (hors avances deja recues) : rien de neuf a annoncer s'il est nul.
            long montantTotal = groupe.stream().mapToLong(CommissionPayment::montantVerse).sum();
            if (montantTotal <= 0) {
                continue;
            }

            boolean notifie = smsNotificationService.sendFromTemplate(
                    partenaire.getTelephone(),
                    TEMPLATE_COMMISSION_PAYEE,
                    Map.of(
                            "montant", formatMontant(montantTotal),
                            "nombreContrats", String.valueOf(groupe.size()),
                            "reference", StringUtils.hasText(reference) ? reference : "N/A"
                    ),
                    TypeMessageSms.commission_payee
            );
            resultat.put(partenaire.getId(), notifie);
        }
        return resultat;
    }

    private String formatMontant(long montant) {
        return CommissionMontants.formater(montant);
    }

    public PageResponse<CommissionPaymentResponse> getPaymentHistory(Long partenaireId, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "paidAt"));

        Page<CommissionPayment> result = partenaireId == null
                ? commissionPaymentRepository.findAll(pageRequest)
                : commissionPaymentRepository.findByPartenaireId(partenaireId, pageRequest);

        // Une seule requete pour toutes les transactions SycaPay de la page (paiements automatiques).
        Map<Long, SycaPayTransaction> transactions = sycaPayTransactionRepository.findAllById(
                        result.getContent().stream().map(CommissionPayment::getSycapayTransactionId).filter(Objects::nonNull).toList())
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

    private CommissionPayment recordPayment(Contract contract, User paidBy, String reference, Long sycapayTransactionId,
                                            AvancesPartenaire.Reservation avance) {
        long avanceImputee = avance.total();
        contract.setCommissionStatus(CommissionStatus.paid);
        contractRepository.save(contract);

        CommissionPayment payment = CommissionPayment.builder()
                .contract(contract)
                .partenaire(contract.getPartenaire())
                .amount(contract.getPartnerShare())
                .paidBy(paidBy)
                .reference(reference)
                .sycapayTransactionId(sycapayTransactionId)
                .avanceImputee(avanceImputee > 0 ? avanceImputee : null)
                .build();
        payment = commissionPaymentRepository.save(payment);

        // Grand livre : commission du contrat MOINS la part deja ecrite a l'avance (demandes partielles payees
        // depuis le 2026-09-26) ; une avance anterieure, jamais comptabilisee, est ecrite ici avec le contrat.
        long aComptabiliser = payment.getAmount() - avance.totalComptabilise();
        if (aComptabiliser > 0) {
            ledgerService.enregistrerCommission(contract, aComptabiliser);
        }
        businessMetrics.incrementPaymentsSuccessful();

        return payment;
    }

    private Contract resolveEligibleContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + contractId));

        if (contract.getPartenaire() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contrat " + contract.getPolicyNumber() + " n'est rattache a aucun partenaire");
        }
        if (contract.getStatus() != ContractStatus.signed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contrat " + contract.getPolicyNumber() + " n'est pas signe (statut: " + contract.getStatus() + ")");
        }
        if (contract.getCommissionStatus() == CommissionStatus.paid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Commission deja payee pour le contrat " + contract.getPolicyNumber());
        }
        if (aDesDemandesPartiellesEnAttente(contract.getPartenaire().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce partenaire a une demande de paiement partiel en attente, en cours de virement ou en echec - traitez-la "
                            + "avant de payer ses contrats (les avances deja versees sont deduites automatiquement du paiement)");
        }
        // Protection anti double paiement (2026-09-23) : un paiement automatique SycaPay (partenaire
        // IMMEDIAT) en cours, incertain ou reussi interdit tout paiement manuel du meme contrat.
        // Message affiche tel quel par le frontend (code SYCAPAY_DUPLICATE_PAYMENT).
        if (sycaPayTransactionRepository.existsByContractIdAndStatusIn(contract.getId(), STATUTS_SYCAPAY_BLOQUANTS)) {
            throw new SycaPayDuplicatePaymentException("Un paiement SycaPay est deja en cours ou effectue pour le contrat "
                    + contract.getPolicyNumber() + ".");
        }

        return contract;
    }

    /**
     * Enregistre le paiement d'une commission partenaire versee AUTOMATIQUEMENT par SycaPay (virement
     * Wave confirme, 2026-09-23) : meme effet qu'un paiement manuel (contrat "paid", CommissionPayment,
     * ecriture comptable) mais sans admin (paidBy NULL, automatic = true). Relit le contrat en base :
     * s'il a ete paye entre-temps par un autre chemin, rien n'est enregistre et null est renvoye (le
     * virement, lui, est deja parti - l'appelant doit alerter).
     */
    /** Resultat du SMS de confirmation d'un paiement automatique (envoye apres coup, hors transaction). */
    @Transactional
    public void enregistrerResultatNotification(Long paymentId, boolean smsEnvoye) {
        commissionPaymentRepository.findById(paymentId).ifPresent(payment -> {
            payment.setNotificationEnvoyee(smsEnvoye);
            commissionPaymentRepository.save(payment);
        });
    }

    @Transactional
    public CommissionPayment enregistrerPaiementAutomatique(Long contractId, SycaPayTransaction transaction) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + contractId));
        if (contract.getCommissionStatus() == CommissionStatus.paid) {
            return null;
        }
        contract.setCommissionStatus(CommissionStatus.paid);
        contractRepository.save(contract);

        // Montant REEL de la commission (partnerShare), jamais celui du virement : en mode test (hors
        // production), le virement porte le montant de test, la commission reste enregistree a sa valeur.
        CommissionPayment payment = commissionPaymentRepository.save(CommissionPayment.builder()
                .contract(contract)
                .partenaire(contract.getPartenaire())
                .amount(contract.getPartnerShare())
                .reference(transaction.getTransactionReference())
                .automatic(true)
                .sycapayTransactionId(transaction.getId())
                .build());

        ledgerService.enregistrerCommission(contract, payment.getAmount());
        businessMetrics.incrementPaymentsSuccessful();
        return payment;
    }

    private Partenaire resolvePartenaire(Long partenaireId) {
        return partenaireRepository.findById(partenaireId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + partenaireId));
    }

    private PartnerCommissionSummaryResponse summarize(Partenaire partenaire, List<Contract> eligible) {
        long pendingCount = 0;
        long paidCount = 0;
        long totalPending = 0;
        long totalPaid = 0;

        for (Contract contract : eligible) {
            if (!contract.getPartenaire().getId().equals(partenaire.getId())) {
                continue;
            }
            long share = contract.getPartnerShare() == null ? 0 : contract.getPartnerShare();
            if (contract.getCommissionStatus() == CommissionStatus.paid) {
                paidCount++;
                totalPaid += share;
            } else {
                pendingCount++;
                totalPending += share;
            }
        }

        return new PartnerCommissionSummaryResponse(
                partenaire.getId(),
                partenaire.getCodePartenaire(),
                partenaire.getNom(),
                partenaire.getPrenoms(),
                partenaire.getType(),
                pendingCount,
                paidCount,
                totalPending,
                totalPaid);
    }

    private ContractCommissionResponse toContractResponse(Contract contract) {
        return new ContractCommissionResponse(
                contract.getId(),
                contract.getPolicyNumber(),
                contract.getSignedAt(),
                contract.getPartnerShare(),
                contract.getCommissionStatus());
    }

    private CommissionPaymentResponse toPaymentResponse(CommissionPayment payment, Boolean notificationEnvoyee) {
        return toPaymentResponse(payment, notificationEnvoyee, null);
    }

    private CommissionPaymentResponse toPaymentResponse(CommissionPayment payment, Boolean notificationEnvoyee,
                                                        SycaPayTransaction transaction) {
        Contract contract = payment.getContract();
        Partenaire partenaire = payment.getPartenaire();
        User paidBy = payment.getPaidBy();

        return new CommissionPaymentResponse(
                payment.getId(),
                contract == null ? null : SafeAccess.idOf(contract, contract::getId),
                contract == null ? null : safe(contract::getPolicyNumber),
                partenaire == null ? null : SafeAccess.idOf(partenaire, partenaire::getId),
                // Historique tolerant (2026-09-23) : un paiement dont le partenaire a ete supprime reste
                // affiche au lieu de faire echouer toute la page en 500.
                partenaire == null ? null : Optional.ofNullable(safe(() -> partenaire.getNom() + " " + partenaire.getPrenoms()))
                        .orElse(PARTENAIRE_SUPPRIME),
                payment.getAmount(),
                paidBy == null ? null : SafeAccess.idOf(paidBy, paidBy::getId),
                paidBy == null ? null : safe(paidBy::getUsername),
                payment.getReference(),
                payment.getPaidAt(),
                notificationEnvoyee != null ? notificationEnvoyee : payment.getNotificationEnvoyee(),
                payment.isAutomatic(),
                transaction == null ? null : transaction.getStatus().name(),
                transaction == null ? null : transaction.getTransactionReference(),
                transaction == null ? null : transaction.getId(),
                transaction == null ? null : transaction.getSycapayResponseMessage(),
                modePaiement(payment),
                payment.getAvanceImputee(),
                payment.montantVerse());
    }

    private static String modePaiement(CommissionPayment payment) {
        if (payment.isAutomatic() || payment.getSycapayTransactionId() != null) {
            return MODE_VIREMENT;
        }
        return payment.getAvanceImputee() != null && payment.montantVerse() <= 0 ? MODE_AVANCE : MODE_HORS_APPLICATION;
    }

    /** Reponse de pay() par virement, pour UN contrat : id/paidAt/paidBy/reference renseignes uniquement
     * si SUCCESS (paiement enregistre). */
    private CommissionPaymentResponse toPaymentResponse(TentativeVirement tentative, Map<Long, Boolean> notificationParPartenaire) {
        if (tentative.payment() != null) {
            CommissionPaymentResponse base = toPaymentResponse(tentative.payment(), notificationParPartenaire.get(idPartenaire(tentative.payment())));
            return new CommissionPaymentResponse(base.id(), base.contractId(), base.policyNumber(), base.partenaireId(),
                    base.partenaireNom(), base.amount(), base.paidByUserId(), base.paidByUsername(), base.reference(), base.paidAt(),
                    base.notificationEnvoyee(), false, tentative.sycapayStatus(), tentative.sycapayTransactionReference(),
                    tentative.sycapayTransactionPk(), tentative.sycapayMessage(), base.modePaiement(), base.avanceImputee(),
                    base.montantVerse());
        }
        Contract contract = tentative.contract();
        Partenaire partenaire = contract.getPartenaire();
        return new CommissionPaymentResponse(null, contract.getId(), contract.getPolicyNumber(), partenaire.getId(),
                partenaire.getNom() + " " + partenaire.getPrenoms(), contract.getPartnerShare(), null, null, null, null, null, false,
                tentative.sycapayStatus(), tentative.sycapayTransactionReference(), tentative.sycapayTransactionPk(),
                tentative.sycapayMessage(), MODE_VIREMENT, null, null);
    }
}
