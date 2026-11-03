package ci.assurzen.service;

import ci.assurzen.dto.MobilePaymentDtos;
import ci.assurzen.entities.ClientAccountInsured;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.MobilePayment;
import ci.assurzen.exception.MobilePaymentException;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.MobilePaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paiement du renouvellement d'un contrat depuis l'application mobile (demande n°4, 2026-11-02).
 *
 * AUCUN fournisseur d'encaissement n'est branche : seule la SIMULATION existe (mobile.payments.simulated=true, dev
 * uniquement). Sans elle, aucun moyen de paiement n'est actif (enabled=false) et toute initiation est refusee
 * (METHOD_DISABLED). En simulation : aucun argent ne bouge ; une tentative est confirmee par le serveur apres quelques
 * secondes, sauf si le numero saisi se termine par 99 (echec simule). Le statut CONFIRMED n'est jamais pose par le client.
 * Apres confirmation d'un renouvellement, un NOUVEAU contrat lie a l'ancien (contratPrecedent) est cree.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MobilePaymentService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final Pattern PAYABLE_RENOUVELLEMENT = Pattern.compile("^RN-(\\d+)-(\\d{8})$");
    private static final Pattern TELEPHONE = Pattern.compile("^(01|05|07)\\d{8}$");
    private static final Duration EXPIRATION = Duration.ofMinutes(15);
    private static final Duration DELAI_SIMULATION = Duration.ofSeconds(8);
    private static final int MAX_TENTATIVES_PAR_HEURE = 5;
    private static final String METHODE_WAVE = "wave";

    private final MobilePaymentRepository paymentRepository;
    private final ClientAccountInsuredRepository clientAccountInsuredRepository;
    private final ContractRepository contractRepository;
    private final MobileNotificationService notificationService;

    @Value("${mobile.payments.simulated:false}")
    private boolean simulation;

    /** Pour les tests : active ou coupe la simulation. */
    void setSimulation(boolean simulation) {
        this.simulation = simulation;
    }

    public boolean paiementsActifs() {
        return simulation;
    }

    /** Reference payable d'un renouvellement : "RN-{idContrat}-{echeance yyyyMMdd}" (liee a l'echeance courante). */
    public static String referenceRenouvellement(Contract contrat) {
        return "RN-" + contrat.getId() + "-" + contrat.getDateEcheance().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public MobilePaymentDtos.MethodList moyens() {
        return new MobilePaymentDtos.MethodList(List.of(new MobilePaymentDtos.Method(METHODE_WAVE, "Wave",
                simulation ? "Paiement simule (aucun argent ne bouge)" : "Bientot disponible", true, simulation, simulation)));
    }

    @Transactional
    public MobilePaymentDtos.Payment initier(Long userId, MobilePaymentDtos.InitiateRequest requete, String cleHeader) {
        String cle = StringUtils.hasText(requete.idempotencyKey()) ? requete.idempotencyKey().trim()
                : StringUtils.hasText(cleHeader) ? cleHeader.trim() : null;
        if (cle != null) {
            MobilePayment existant = paymentRepository.findByUserIdAndIdempotencyKey(userId, cle).orElse(null);
            if (existant != null) {
                return vue(resoudre(existant));
            }
        }
        if (!simulation || !METHODE_WAVE.equalsIgnoreCase(requete.methodId())) {
            throw new MobilePaymentException(409, "METHOD_DISABLED", "Ce moyen de paiement n'est pas disponible pour le moment.", "methodId", null);
        }
        String telephone = requete.phone() == null ? null : requete.phone().replaceAll("\\s", "");
        if (telephone == null || !TELEPHONE.matcher(telephone).matches()) {
            throw new MobilePaymentException(400, "VALIDATION_FAILED",
                    "Le numero Wave doit contenir 10 chiffres et commencer par 01, 05 ou 07", "phone", null);
        }

        Contract contrat = contratPayable(userId, requete.payableReference());
        long montant = contrat.getPremium();
        if (requete.amount() != null && requete.amount() != montant) {
            throw new MobilePaymentException(409, "AMOUNT_MISMATCH",
                    "Le montant a change. Il est de " + montant + " FCFA, rouvrez le renouvellement.", "amount", null);
        }
        if (paymentRepository.existsByPayableReferenceAndStatus(requete.payableReference(), "CONFIRMED")) {
            throw new MobilePaymentException(409, "PAYMENT_ALREADY_CONFIRMED", "Ce renouvellement est deja paye.");
        }
        verifierQuota(userId);

        MobilePayment paiement = new MobilePayment();
        paiement.setUserId(userId);
        paiement.setContractId(contrat.getId());
        paiement.setPayableReference(requete.payableReference());
        paiement.setMethodId(METHODE_WAVE);
        paiement.setPhone(telephone);
        paiement.setAmount(montant);
        paiement.setSimulated(true);
        paiement.setIdempotencyKey(cle);
        paiement.setExpiresAt(Instant.now().plus(EXPIRATION));
        paiement.setReference("TMP-" + java.util.UUID.randomUUID().toString().substring(0, 24));
        try {
            paiement = paymentRepository.saveAndFlush(paiement);
        } catch (DataIntegrityViolationException e) {
            MobilePayment existant = cle == null ? null : paymentRepository.findByUserIdAndIdempotencyKey(userId, cle).orElse(null);
            if (existant != null) {
                return vue(existant);
            }
            throw e;
        }
        paiement.setReference(String.format("PAY-%d-%06d", Year.now().getValue(), paiement.getId()));
        paymentRepository.save(paiement);
        log.info("[MOBILE-PAIEMENT] tentative simulee {} : {} FCFA pour {}", paiement.getReference(), montant, paiement.getPayableReference());
        return vue(paiement);
    }

    @Transactional
    public MobilePaymentDtos.Payment statut(Long userId, String reference) {
        MobilePayment paiement = paymentRepository.findByReferenceAndUserId(reference, userId)
                .orElseThrow(() -> new MobilePaymentException(404, "PAYMENT_NOT_FOUND", "Paiement introuvable."));
        return vue(resoudre(paiement));
    }

    // ------------------------------------------------------------------

    /** Fait evoluer une tentative PENDING : expiration, puis resultat de la simulation (confirmation ou echec). */
    private MobilePayment resoudre(MobilePayment paiement) {
        if (!"PENDING".equals(paiement.getStatus())) {
            return paiement;
        }
        Instant maintenant = Instant.now();
        if (paiement.isSimulated() && maintenant.isAfter(paiement.getCreatedAt().plus(DELAI_SIMULATION))) {
            boolean echec = paiement.getPhone() != null && paiement.getPhone().endsWith("99");
            paiement.setStatus(echec ? "FAILED" : "CONFIRMED");
            paiement.setFailureReason(echec ? "Solde insuffisant (echec simule)" : null);
            paiement.setResolvedAt(maintenant);
            if (!echec) {
                paiement.setRenewalContractId(creerRenouvellement(paiement));
            }
            MobilePayment resolu = paymentRepository.save(paiement);
            notificationService.notifier(resolu.getUserId(), "PAYMENT", echec ? "Paiement échoué" : "Paiement confirmé",
                    echec ? "Votre paiement de " + resolu.getAmount() + " FCFA n'a pas abouti. Vous pouvez réessayer."
                            : "Votre paiement de " + resolu.getAmount() + " FCFA a été confirmé.",
                    resolu.getContractId(), null, "PAY-" + resolu.getId());
            return resolu;
        }
        if (maintenant.isAfter(paiement.getExpiresAt())) {
            paiement.setStatus("FAILED");
            paiement.setFailureReason("Delai de paiement depasse");
            paiement.setResolvedAt(maintenant);
            return paymentRepository.save(paiement);
        }
        return paiement;
    }

    private Contract contratPayable(Long userId, String payableReference) {
        Matcher m = PAYABLE_RENOUVELLEMENT.matcher(payableReference);
        if (!m.matches()) {
            throw new MobilePaymentException(404, "PAYABLE_NOT_FOUND", "Rien a payer pour cette reference.", "payableReference", null);
        }
        List<Long> assures = clientAccountInsuredRepository.findByUserId(userId).stream().map(ClientAccountInsured::getInsuredId).toList();
        Contract contrat = assures.isEmpty() ? null
                : contractRepository.findByIdAndInsuredIdIn(Long.valueOf(m.group(1)), assures).orElse(null);
        if (contrat == null || contrat.getDateEcheance() == null || contrat.getStatus() != ContractStatus.signed
                || !referenceRenouvellement(contrat).equals(payableReference)
                || contractRepository.findByContratPrecedentId(contrat.getId()).isPresent()
                || contrat.getDateEcheance().isBefore(LocalDate.now(ABIDJAN))) {
            throw new MobilePaymentException(404, "PAYABLE_NOT_FOUND", "Rien a payer pour cette reference.", "payableReference", null);
        }
        return contrat;
    }

    private void verifierQuota(Long userId) {
        Instant depuis = Instant.now().minus(Duration.ofHours(1));
        List<MobilePayment> recentes = paymentRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, depuis);
        if (recentes.size() >= MAX_TENTATIVES_PAR_HEURE) {
            long attente = Math.max(1, Duration.between(Instant.now(),
                    recentes.get(recentes.size() - MAX_TENTATIVES_PAR_HEURE).getCreatedAt().plus(Duration.ofHours(1))).getSeconds() + 1);
            throw new MobilePaymentException(429, "RATE_LIMITED", "Trop de tentatives de paiement. Reessayez dans "
                    + Math.max(1, (attente + 59) / 60) + " minute(s).", null, attente);
        }
    }

    /** Cree le contrat de renouvellement (SIMULE) lie a l'ancien : memes produit, assure, vehicule, prime ; une annee de plus. */
    private Long creerRenouvellement(MobilePayment paiement) {
        Contract ancien = contractRepository.findById(paiement.getContractId()).orElseThrow();
        if (contractRepository.findByContratPrecedentId(ancien.getId()).isPresent()) {
            return contractRepository.findByContratPrecedentId(ancien.getId()).get().getId();
        }
        LocalDate debut = ancien.getDateEcheance().plusDays(1);
        int mois = 12;
        String chiffres = ancien.getDuree() == null ? "" : ancien.getDuree().replaceAll("\\D", "");
        if (!chiffres.isEmpty()) {
            mois = Integer.parseInt(chiffres);
        }
        Contract nouveau = Contract.builder()
                .policyNumber(numeroUnique(ancien.getPolicyNumber()))
                .dateEffet(debut)
                .duree(ancien.getDuree())
                .dateEcheance(debut.plusMonths(mois).minusDays(1))
                .product(ancien.getProduct())
                .insured(ancien.getInsured())
                .vehicule(ancien.getVehicule())
                .partenaire(ancien.getPartenaire())
                .compagnie(ancien.getCompagnie())
                .salesRep(ancien.getSalesRep())
                .premium(paiement.getAmount())
                .commissionRate(java.math.BigDecimal.ZERO)
                .commissionAmount(0L)
                .partnerShare(0L)
                .status(ContractStatus.signed)
                .commissionStatus(ci.assurzen.entities.CommissionStatus.pending)
                .signedAt(LocalDate.now(ABIDJAN))
                .modePaiement(ancien.getModePaiement())
                .notes("Renouvellement SIMULE (paiement mobile " + paiement.getReference() + ") - aucun argent encaisse")
                .contratPrecedent(ancien)
                .build();
        return contractRepository.save(nouveau).getId();
    }

    private String numeroUnique(String base) {
        String candidat = base + "-R" + Year.now().getValue();
        int n = 1;
        while (contractRepository.findByPolicyNumber(candidat).isPresent()) {
            n++;
            candidat = base + "-R" + Year.now().getValue() + "-" + n;
        }
        return candidat.length() > 50 ? candidat.substring(candidat.length() - 50) : candidat;
    }

    private MobilePaymentDtos.Payment vue(MobilePayment p) {
        String consigne = "PENDING".equals(p.getStatus())
                ? "Paiement simule : la confirmation arrive automatiquement dans quelques secondes. Aucun argent ne bouge."
                : null;
        return new MobilePaymentDtos.Payment(p.getReference(), p.getStatus(), p.getFailureReason(), null, p.getPayableReference(),
                p.getAmount(), p.getMethodId(), p.isSimulated(), null, consigne, p.getCreatedAt(), p.getExpiresAt());
    }
}
