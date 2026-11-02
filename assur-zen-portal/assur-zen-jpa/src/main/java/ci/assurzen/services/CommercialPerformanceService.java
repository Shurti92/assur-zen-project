package ci.assurzen.services;

import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.events.NotificationUtilisateurEvent;
import org.springframework.context.ApplicationEventPublisher;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommercialDashboardResponse;
import ci.assurzen.dto.CommercialManagementDashboardResponse;
import ci.assurzen.dto.CommercialPerformanceResponse;
import ci.assurzen.dto.CommercialRankingEntry;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialMonthlyObjective;
import ci.assurzen.entities.CommercialObjectiveType;
import ci.assurzen.entities.CommercialPerformance;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.StatutCommercial;
import ci.assurzen.entities.StatutCommercialPerformance;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.CommercialMonthlyObjectiveRepository;
import ci.assurzen.repositories.CommercialPerformanceRepository;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.MoisComptableRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Performance/bonus mensuel d'un commercial (contrat "Module Commercial", 2026-09-07). Formule
 * confirmee : bonus = objectif_mensuel x COMMERCIAL_BONUS_RATE% des lors que le taux d'atteinte
 * (realise/objectif) &gt;= COMMERCIAL_BONUS_MIN_ACHIEVEMENT_RATE, TOUJOURS calcule sur l'objectif et
 * jamais sur le realise (meme si depasse). Metrique de production fixee a PREMIUM_AMOUNT pour cette
 * phase (pas de cle de configuration COMMERCIAL_PERFORMANCE_METRIC : aucune autre metrique n'est
 * implementee, une abstraction non cablee n'a pas sa place ici).
 *
 * Workflow : OPEN/CALCULATED -&gt; ELIGIBLE|NOT_ELIGIBLE -&gt; VALIDATED -&gt; PAID -&gt; CLOSED. Toute lecture
 * (dashboard, GET performance, ranking, dashboard management) recalcule "en direct" tant que la ligne
 * n'a pas atteint VALIDATED - au-dela, les valeurs sont figees (snapshot) et ne bougent plus (§28/§31).
 *
 * IMPORTANT (correctif du 2026-09-07, bug reproductible signale par le frontend) : ce calcul "en
 * direct" ne persiste JAMAIS rien - resoudreEnLecture() construit systematiquement un objet
 * CommercialPerformance detache (jamais charge depuis le repository), pour que les lectures restent de
 * vraies lectures. Version precedente : les 4 endpoints de lecture (dashboard/getPerformance/ranking/
 * managementDashboard) appelaient tous une meme methode qui faisait un repository.save() sur l'entite
 * geree - deux GET concurrents sur le meme (commercial, moisComptable) se percutaient alors sur le
 * verrou optimiste (@Version) et l'un des deux echouait en 409, ce qui n'a strictement aucun sens pour
 * un endpoint de lecture. Seuls recalculer() (action admin explicite) et recalculerSysteme() (job
 * planifie) persistent reellement, via calculerEtPersister().
 */
@Service
@RequiredArgsConstructor
public class CommercialPerformanceService {

    private static final String TEMPLATE_BONUS_COMMERCIAL_PAYE = "BONUS_COMMERCIAL_PAYE";

    private final CommercialPerformanceRepository repository;
    private final CommercialMonthlyObjectiveRepository monthlyObjectiveRepository;
    private final CommercialRepository commercialRepository;
    private final MoisComptableRepository moisComptableRepository;
    private final UserRepository userRepository;
    private final CommercialService commercialService;
    private final CommercialProductionService productionService;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final SmsNotificationService smsNotificationService;
    private final TelegramNotificationService telegramNotificationService;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final SycaPayTransactionRepository sycaPayTransactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public CommercialDashboardResponse dashboard() {
        Commercial commercial = commercialService.getCommercialByUser(commercialService.currentUserId());
        MoisComptable mois = resolveMoisCourant();
        CommercialPerformance perf = resoudreEnLecture(commercial, mois);

        BigDecimal reste = perf.getObjectiveAmount().subtract(perf.getRealizedAmount());
        if (reste.compareTo(BigDecimal.ZERO) < 0) {
            reste = BigDecimal.ZERO;
        }
        boolean estime = perf.getStatut() != StatutCommercialPerformance.VALIDATED
                && perf.getStatut() != StatutCommercialPerformance.PAID
                && perf.getStatut() != StatutCommercialPerformance.CLOSED;

        return new CommercialDashboardResponse(
                mois.getId(), mois.getLibelle(),
                perf.getObjectiveAmount(), perf.getRealizedAmount(), perf.getAchievementRate(),
                reste, perf.getBonusAmount(), estime,
                productionService.countContratsEligibles(commercial, mois),
                perf.getStatut()
        );
    }

    @Transactional(readOnly = true)
    public CommercialPerformanceResponse getPerformance(Long commercialId, Long moisComptableId) {
        commercialService.verifierAccesCommercialCible(commercialId);
        Commercial commercial = commercialService.getCommercial(commercialId);
        MoisComptable mois = resolveMois(moisComptableId);
        return toResponse(resoudreEnLecture(commercial, mois));
    }

    /**
     * Historique de performance d'un commercial (correctif 2026-10-02). Version precedente : ne lisait que
     * les lignes PERSISTEES - or une ligne n'est creee que par un recalcul admin explicite ou par
     * CommercialPerformanceScheduler (le jour suivant la fin d'un mois deja cloture), donc l'historique
     * restait vide en pratique, et le bouton "Recalculer" du frontend n'apparaissant que sur une ligne
     * existante, rien ne permettait d'en sortir.
     *
     * Desormais : un mois figure dans l'historique s'il a une ligne persistee, OU un objectif mensuel deja
     * commence, OU s'il s'agit du mois courant. Les mois non verrouilles sont recalcules en direct (lecture
     * pure, jamais de save - meme chemin que dashboard()/getPerformance()), les mois VALIDATED/PAID/CLOSED
     * renvoient leur snapshot fige. Les mois futurs (objectif deja saisi) sont exclus.
     */
    @Transactional(readOnly = true)
    public List<CommercialPerformanceResponse> list(Long commercialId) {
        commercialService.verifierAccesCommercialCible(commercialId);
        Commercial commercial = commercialService.getCommercial(commercialId);
        LocalDate aujourdhui = LocalDate.now();

        Map<Long, MoisComptable> moisRetenus = new java.util.LinkedHashMap<>();
        repository.findByCommercialIdOrderByMoisComptable_NumeroDesc(commercialId)
                .forEach(perf -> moisRetenus.putIfAbsent(perf.getMoisComptable().getId(), perf.getMoisComptable()));
        monthlyObjectiveRepository.findByCommercialIdOrderByMoisComptable_NumeroDesc(commercialId).stream()
                .map(CommercialMonthlyObjective::getMoisComptable)
                .filter(mois -> mois.getDateDebut() != null && !mois.getDateDebut().isAfter(aujourdhui))
                .forEach(mois -> moisRetenus.putIfAbsent(mois.getId(), mois));
        moisComptableRepository
                .findFirstByDateDebutLessThanEqualAndDateFinGreaterThanEqualOrderByDateDebutDesc(aujourdhui, aujourdhui)
                .ifPresent(mois -> moisRetenus.putIfAbsent(mois.getId(), mois));

        List<CommercialPerformance> performances = moisRetenus.values().stream()
                .sorted(java.util.Comparator.comparing(MoisComptable::getDateDebut,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed())
                .map(mois -> resoudreEnLecture(commercial, mois))
                .toList();
        Map<Long, SycaPayTransaction> transactions = transactionsParPerformance(performances);
        return performances.stream()
                .map(perf -> toResponse(perf, perf.getId() == null ? null : transactions.get(perf.getId())))
                .toList();
    }

    /** Lecture "en direct" (jamais persistee) reutilisable par CommercialPayrollService et
     * CommercialBonusPaymentService - meme regle que dashboard()/getPerformance(). */
    @Transactional(readOnly = true)
    public CommercialPerformance resoudrePourLecture(Commercial commercial, MoisComptable mois) {
        return resoudreEnLecture(commercial, mois);
    }

    /** Virements SycaPay de bonus des performances persistees donnees, en une requete. */
    public Map<Long, SycaPayTransaction> transactionsParPerformance(List<CommercialPerformance> performances) {
        List<Long> ids = performances.stream().map(CommercialPerformance::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sycaPayTransactionRepository.findByCommercialPerformanceIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(SycaPayTransaction::getCommercialPerformanceId, t -> t, (a, b) -> a));
    }

    /** Point d'entree systeme (CommercialPerformanceScheduler) - PAS de garde ADMIN : aucun principal
     * authentifie n'existe dans le contexte d'un job planifie (authService.currentPrincipal() y leverait),
     * et cette methode n'est exposee par aucun controleur. */
    @Transactional
    public void recalculerSysteme(Commercial commercial, MoisComptable mois) {
        calculerEtPersister(commercial, mois);
    }

    /** Contrairement a dashboard()/getPerformance() (lecture passive, renvoient silencieusement le
     * snapshot fige si verrouille), le recalcul EXPLICITE demande par un admin doit echouer en 409 sur
     * une periode verrouillee plutot que rendre une reponse 200 qui n'a rien recalcule (§31). */
    @Transactional
    public CommercialPerformanceResponse recalculer(Long commercialId, Long moisComptableId) {
        commercialService.requireAdmin();
        Commercial commercial = commercialService.getCommercial(commercialId);
        MoisComptable mois = resolveMois(moisComptableId);

        repository.findByCommercialIdAndMoisComptableId(commercialId, mois.getId())
                .filter(perf -> estVerrouille(perf.getStatut()))
                .ifPresent(perf -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Periode verrouillee (statut " + perf.getStatut() + "), recalcul interdit");
                });

        return toResponse(calculerEtPersister(commercial, mois));
    }

    /**
     * Transitions autorisees : (CALCULATED|ELIGIBLE|NOT_ELIGIBLE) -&gt; VALIDATED, VALIDATED -&gt; CLOSED,
     * PAID -&gt; CLOSED. VALIDATED declenche une alerte Telegram admin si eligible.
     *
     * Correctifs 2026-10-02 :
     * - VALIDATED sur un mois jamais persiste (historique recalcule en direct, cf. list()) : la ligne est
     *   d'abord calculee et persistee, au lieu d'un 404 qui obligeait a "recalculer" une ligne invisible.
     * - VALIDATED -&gt; PAID n'est plus un simple changement de statut : il passe exclusivement par
     *   CommercialBonusPaymentService.payer() (virement SycaPay reel, ou enregistrement hors application
     *   si SycaPay est desactive), qui seul ecrit le grand livre et notifie le commercial.
     */
    @Transactional
    public CommercialPerformanceResponse changerStatut(Long commercialId, Long moisComptableId, StatutCommercialPerformance nouveauStatut, String motif) {
        commercialService.requireAdmin();
        if (nouveauStatut == StatutCommercialPerformance.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le paiement d'un bonus passe par l'action \"Payer le bonus\" (virement reel), pas par un changement de statut");
        }
        Commercial commercial = commercialService.getCommercial(commercialId);
        MoisComptable mois = resolveMois(moisComptableId);
        Optional<CommercialPerformance> existante = repository.findByCommercialIdAndMoisComptableId(commercialId, mois.getId());
        if (existante.isEmpty() && nouveauStatut != StatutCommercialPerformance.VALIDATED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Performance non encore calculee pour ce mois - lancer un recalcul d'abord");
        }
        CommercialPerformance perf = existante.orElseGet(() -> calculerEtPersister(commercial, mois));
        CommercialPerformanceResponse avant = toResponse(perf);

        validerTransition(perf.getStatut(), nouveauStatut, perf.isEligible());

        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User utilisateur = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        switch (nouveauStatut) {
            case VALIDATED -> {
                perf.setValidatedBy(utilisateur);
                perf.setValidatedAt(Instant.now());
                if (perf.isEligible()) {
                    telegramNotificationService.envoyer("<b>Bonus commercial valide</b>\n"
                            + TelegramNotificationService.echapperHtml(nomCommercial(commercial)) + " - " + mois.getLibelle()
                            + " : " + formatMontant(perf.getBonusAmount()) + " FCFA (taux " + perf.getAchievementRate() + "%)");
                }
            }
            case CLOSED -> { /* rien de plus a faire */ }
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Transition vers " + nouveauStatut + " non autorisee");
        }
        perf.setStatut(nouveauStatut);
        perf = repository.save(perf);
        if (nouveauStatut == StatutCommercialPerformance.VALIDATED && perf.isEligible()) {
            notifierCommercial(commercial, PartenaireNotificationType.BONUS_VALIDE, "Bonus validé",
                    "Votre bonus de " + mois.getLibelle() + " (" + formatMontant(perf.getBonusAmount()) + " FCFA) a été validé.",
                    perf);
        }

        CommercialPerformanceResponse apres = toResponse(perf);
        functionalAuditRecorder.recordUpdate("COMMERCIAL_PERFORMANCE", "CommercialPerformance", String.valueOf(perf.getId()), avant, apres, motif);
        return apres;
    }

    @Transactional(readOnly = true)
    public List<CommercialRankingEntry> ranking(Long moisComptableId) {
        MoisComptable mois = moisComptableId != null ? resolveMois(moisComptableId) : resolveMoisCourant();
        List<CommercialRankingEntry> tries = commercialRepository.findByStatut(StatutCommercial.ACTIVE).stream()
                .map(commercial -> {
                    CommercialPerformance perf = resoudreEnLecture(commercial, mois);
                    return new CommercialRankingEntry(commercial.getId(), commercial.getEmployeeCode(), nomCommercial(commercial), perf.getAchievementRate(), 0);
                })
                .sorted((a, b) -> b.achievementRate().compareTo(a.achievementRate()))
                .toList();

        List<CommercialRankingEntry> classement = new java.util.ArrayList<>(tries.size());
        for (int i = 0; i < tries.size(); i++) {
            CommercialRankingEntry entree = tries.get(i);
            classement.add(new CommercialRankingEntry(entree.commercialId(), entree.employeeCode(), entree.nomComplet(), entree.achievementRate(), i + 1));
        }
        return classement;
    }

    @Transactional(readOnly = true)
    public CommercialManagementDashboardResponse managementDashboard(Long moisComptableId) {
        commercialService.requireAdmin();
        MoisComptable mois = moisComptableId != null ? resolveMois(moisComptableId) : resolveMoisCourant();

        List<Commercial> actifs = commercialRepository.findByStatut(StatutCommercial.ACTIVE);
        List<CommercialPerformance> performances = actifs.stream()
                .map(commercial -> resoudreEnLecture(commercial, mois))
                .toList();

        BigDecimal objectifTotal = sum(performances.stream().map(CommercialPerformance::getObjectiveAmount));
        BigDecimal productionTotale = sum(performances.stream().map(CommercialPerformance::getRealizedAmount));
        BigDecimal tauxMoyen = objectifTotal.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : productionTotale.divide(objectifTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        long eligibles = performances.stream().filter(CommercialPerformance::isEligible).count();
        BigDecimal bonusTotal = sum(performances.stream().map(CommercialPerformance::getBonusAmount));

        return new CommercialManagementDashboardResponse(
                mois.getId(), mois.getLibelle(),
                commercialRepository.count(), actifs.size(),
                objectifTotal, productionTotale, tauxMoyen,
                eligibles, performances.size() - eligibles,
                bonusTotal
        );
    }

    /**
     * Export CSV performance+bonus+remuneration (§72 et §45 - sert aussi d'element de preparation
     * paie, pas de PayrollIntegrationService distinct : les memes colonnes deja disponibles sur
     * CommercialPerformance suffisent). Une seule ressource exportee (pas de
     * /commercial-bonuses/export separe) - performance et bonus sont deliberement la meme entite dans
     * ce module (cf. javadoc de classe), un second export en ferait une simple redite des memes lignes.
     * Reserve aux administrateurs (contient le salaire). Format CSV uniquement, meme convention que
     * tous les autres exports de ce projet (ContractsController.export, etc.) - aucun module existant
     * ne produit de veritable .xlsx binaire (pas de dependance Apache POI dans ce projet), et un CSV
     * s'ouvre nativement dans Excel.
     */
    public byte[] exportCsv(Long commercialId, Long moisComptableId) {
        commercialService.requireAdmin();
        List<CommercialPerformance> rows = repository.findAll().stream()
                .filter(p -> commercialId == null || p.getCommercial().getId().equals(commercialId))
                .filter(p -> moisComptableId == null || p.getMoisComptable().getId().equals(moisComptableId))
                .sorted(java.util.Comparator
                        .comparing((CommercialPerformance p) -> p.getMoisComptable().getNumero()).reversed()
                        .thenComparing(p -> p.getCommercial().getId()))
                .toList();

        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Matricule;Commercial;Mois;ObjectifMensuel;Realise;TauxAtteinte;SalaireBase;AvantageTransport;DotationTelephone;TauxBonus;MontantBonus;RemunerationTotale;Statut;ValidePar;DateValidation;PayePar;DatePaiement\n");
        for (CommercialPerformance p : rows) {
            Commercial c = p.getCommercial();
            BigDecimal total = p.getBaseSalarySnapshot().add(p.getTransportBenefitSnapshot())
                    .add(p.getPhoneAllowanceSnapshot()).add(p.getBonusAmount());
            csv.append(CsvUtils.field(c.getEmployeeCode()))
                    .append(';').append(CsvUtils.field(nomCommercial(c)))
                    .append(';').append(CsvUtils.field(p.getMoisComptable().getLibelle()))
                    .append(';').append(CsvUtils.field(p.getObjectiveAmount()))
                    .append(';').append(CsvUtils.field(p.getRealizedAmount()))
                    .append(';').append(CsvUtils.field(p.getAchievementRate()))
                    .append(';').append(CsvUtils.field(p.getBaseSalarySnapshot()))
                    .append(';').append(CsvUtils.field(p.getTransportBenefitSnapshot()))
                    .append(';').append(CsvUtils.field(p.getPhoneAllowanceSnapshot()))
                    .append(';').append(CsvUtils.field(p.getBonusRateSnapshot()))
                    .append(';').append(CsvUtils.field(p.getBonusAmount()))
                    .append(';').append(CsvUtils.field(total))
                    .append(';').append(CsvUtils.field(p.getStatut()))
                    .append(';').append(CsvUtils.field(p.getValidatedBy() == null ? null : p.getValidatedBy().getUsername()))
                    .append(';').append(CsvUtils.field(p.getValidatedAt()))
                    .append(';').append(CsvUtils.field(p.getPaidBy() == null ? null : p.getPaidBy().getUsername()))
                    .append(';').append(CsvUtils.field(p.getPaidAt()))
                    .append('\n');
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Coeur du calcul, reutilise par dashboard()/getPerformance()/recalculer()/ranking()/managementDashboard()
    // ------------------------------------------------------------------

    /**
     * Chemin LECTURE (dashboard/getPerformance/ranking/managementDashboard) : ne persiste jamais rien.
     * Si une ligne verrouillee existe deja, la renvoie telle quelle (snapshot fige, aucune mutation).
     * Sinon, calcule "en direct" sur un objet CommercialPerformance DETACHE - jamais charge depuis le
     * repository, donc jamais suivi par le contexte de persistance Hibernate : aucun risque qu'il soit
     * flushe/sauvegarde en fin de transaction, meme si ses setters sont appeles. C'est ce qui garantit
     * que deux lectures concurrentes sur le meme (commercial, moisComptable) ne se percutent jamais sur
     * le verrou optimiste de l'entite geree (cf. javadoc de classe, correctif du 2026-09-07).
     */
    private CommercialPerformance resoudreEnLecture(Commercial commercial, MoisComptable mois) {
        Optional<CommercialPerformance> existantOpt = repository.findByCommercialIdAndMoisComptableId(commercial.getId(), mois.getId());
        if (existantOpt.isPresent() && estVerrouille(existantOpt.get().getStatut())) {
            return existantOpt.get();
        }
        CommercialPerformance transitoire = CommercialPerformance.builder()
                .id(existantOpt.map(CommercialPerformance::getId).orElse(null))
                .commercial(commercial)
                .moisComptable(mois)
                .build();
        return calculerValeurs(commercial, mois, transitoire);
    }

    /**
     * Chemin ECRITURE (recalculer()/recalculerSysteme() uniquement) : cree ou met a jour REELLEMENT la
     * ligne en base. Appelee uniquement depuis des methodes publiques deja @Transactional de cette
     * classe (self-invocation : une @Transactional posee ici serait silencieusement ignoree par le
     * proxy Spring).
     */
    private CommercialPerformance calculerEtPersister(Commercial commercial, MoisComptable mois) {
        Optional<CommercialPerformance> existantOpt = repository.findByCommercialIdAndMoisComptableId(commercial.getId(), mois.getId());
        if (existantOpt.isPresent() && estVerrouille(existantOpt.get().getStatut())) {
            return existantOpt.get();
        }

        CommercialPerformance cible = existantOpt.orElseGet(() -> CommercialPerformance.builder()
                .commercial(commercial)
                .moisComptable(mois)
                .build());
        calculerValeurs(commercial, mois, cible);

        boolean creation = cible.getId() == null;
        CommercialPerformance saved = repository.save(cible);
        if (creation) {
            functionalAuditRecorder.recordAction("COMMERCIAL_PERFORMANCE", "CommercialPerformance", String.valueOf(saved.getId()),
                    ActionAudit.CREATE, "Performance calculee pour " + mois.getLibelle());
        }
        return saved;
    }

    /** Calcul pur : remplit `cible` (transitoire ou geree, selon l'appelant) et la renvoie, sans jamais
     * appeler le repository en ecriture - la persistance (ou son absence) est entierement decidee par
     * l'appelant (resoudreEnLecture() vs calculerEtPersister()). */
    private CommercialPerformance calculerValeurs(Commercial commercial, MoisComptable mois, CommercialPerformance cible) {
        BigDecimal objectif = monthlyObjectiveRepository
                .findByCommercialIdAndMoisComptableIdAndObjectiveType(commercial.getId(), mois.getId(), CommercialObjectiveType.PREMIUM_AMOUNT)
                .map(CommercialMonthlyObjective::getTargetAmount)
                .orElse(BigDecimal.ZERO);
        BigDecimal realise = productionService.computeRealise(commercial, mois);
        // Borne AVANT toute utilisation (verification d'eligibilite incluse) : un objectif fixe
        // anormalement bas produit sinon un taux de plusieurs millions de %, hors de portee de la
        // colonne achievement_rate (DECIMAL(9,4)) - Data truncation SQL a la persistance, bug reel
        // constate le 2026-09-17. Le seuil d'eligibilite (typiquement 100%) reste toujours atteint
        // apres bornage, aucun changement de comportement metier pour ce cas.
        BigDecimal taux = ci.assurzen.utils.PercentageUtils.borner(
                productionService.computeTauxRealisation(realise, objectif), 9, 4);

        boolean bonusActive = dynamicConfigurationService.getBoolean("COMMERCIAL_BONUS_ENABLED", true);
        BigDecimal seuilMinimum = dynamicConfigurationService.getDecimal("COMMERCIAL_BONUS_MIN_ACHIEVEMENT_RATE", BigDecimal.valueOf(100));
        BigDecimal tauxBonus = dynamicConfigurationService.getDecimal("COMMERCIAL_BONUS_RATE", BigDecimal.valueOf(5));

        boolean eligible = bonusActive && objectif.compareTo(BigDecimal.ZERO) > 0 && taux.compareTo(seuilMinimum) >= 0;
        BigDecimal bonus = eligible
                ? objectif.multiply(tauxBonus).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        cible.setObjectiveAmount(objectif);
        cible.setRealizedAmount(realise);
        cible.setAchievementRate(taux);
        cible.setBonusRateSnapshot(tauxBonus);
        cible.setBonusAmount(bonus);
        cible.setEligible(eligible);
        cible.setBaseSalarySnapshot(commercial.getBaseSalary());
        cible.setTransportBenefitSnapshot(commercial.getTransportBenefitAmount());
        cible.setPhoneAllowanceSnapshot(commercial.getPhoneAllowanceAmount());
        cible.setStatut(eligible ? StatutCommercialPerformance.ELIGIBLE : StatutCommercialPerformance.NOT_ELIGIBLE);
        cible.setCalculatedAt(Instant.now());
        return cible;
    }

    public boolean estVerrouille(StatutCommercialPerformance statut) {
        return statut == StatutCommercialPerformance.VALIDATED
                || statut == StatutCommercialPerformance.PAID
                || statut == StatutCommercialPerformance.CLOSED;
    }

    /** Visible du package : reutilisee par CommercialBonusPaymentService pour VALIDATED -&gt; PAID. */
    void validerTransition(StatutCommercialPerformance actuel, StatutCommercialPerformance cible, boolean eligible) {
        boolean valide = switch (actuel) {
            case CALCULATED, ELIGIBLE, NOT_ELIGIBLE -> cible == StatutCommercialPerformance.VALIDATED;
            case VALIDATED -> (cible == StatutCommercialPerformance.PAID && eligible) || cible == StatutCommercialPerformance.CLOSED;
            case PAID -> cible == StatutCommercialPerformance.CLOSED;
            case OPEN, CLOSED -> false;
        };
        if (!valide) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transition " + actuel + " -> " + cible + " non autorisee");
        }
    }

    /** Notification in-app du commercial (son compte utilisateur), creee apres commit - 2026-10-03. */
    public void notifierCommercial(Commercial commercial, PartenaireNotificationType type, String titre, String message,
                                   CommercialPerformance perf) {
        if (commercial.getProfile() == null || commercial.getProfile().getUser() == null) {
            return;
        }
        eventPublisher.publishEvent(NotificationUtilisateurEvent.pourUtilisateur(commercial.getProfile().getUser().getId(),
                type, titre, message,
                perf.getBonusAmount() == null ? null : perf.getBonusAmount().setScale(0, RoundingMode.HALF_UP).longValue(),
                "PERFORMANCE_COMMERCIALE", perf.getId()));
    }

    /** SMS "bonus paye" au commercial (echec non bloquant) - appele par CommercialBonusPaymentService. */
    public boolean notifierBonusPaye(Commercial commercial, MoisComptable mois, CommercialPerformance perf) {
        return smsNotificationService.sendFromTemplate(
                commercial.getProfile().getPhone(),
                TEMPLATE_BONUS_COMMERCIAL_PAYE,
                Map.of(
                        "montant", formatMontant(perf.getBonusAmount()),
                        "mois", mois.getLibelle()
                ),
                TypeMessageSms.bonus_commercial_paye
        );
    }

    private MoisComptable resolveMois(Long moisComptableId) {
        return moisComptableRepository.findById(moisComptableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mois comptable introuvable: " + moisComptableId));
    }

    private MoisComptable resolveMoisCourant() {
        LocalDate aujourdhui = LocalDate.now();
        return moisComptableRepository
                .findFirstByDateDebutLessThanEqualAndDateFinGreaterThanEqualOrderByDateDebutDesc(aujourdhui, aujourdhui)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Aucun mois comptable ne couvre la date du jour"));
    }

    public String nomCommercial(Commercial commercial) {
        return commercial.getProfile().getFirstname() + " " + commercial.getProfile().getLastname();
    }

    private BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Regroupement par milliers avec un espace simple, meme raison que PrimeObjectifService.formatMontant (SMS GSM-7). */
    public String formatMontant(BigDecimal montant) {
        String brut = montant.setScale(0, RoundingMode.HALF_UP).toPlainString();
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

    private CommercialPerformanceResponse toResponse(CommercialPerformance perf) {
        SycaPayTransaction transaction = perf.getId() == null ? null
                : sycaPayTransactionRepository.findByCommercialPerformanceId(perf.getId()).orElse(null);
        return toResponse(perf, transaction);
    }

    /** Seuil d'eligibilite au bonus actuellement configure (% d'atteinte de l'objectif). */
    public BigDecimal seuilEligibilite() {
        return dynamicConfigurationService.getDecimal("COMMERCIAL_BONUS_MIN_ACHIEVEMENT_RATE", BigDecimal.valueOf(100));
    }

    /** Bonus payable maintenant : objectif atteint (ELIGIBLE, valide automatiquement au paiement) ou deja
     * VALIDATED, montant &gt; 0, et aucun virement en cours ni d'issue incertaine (FAILED = relancable ;
     * SUCCESS non encore enregistre = rattrapage sans nouveau virement, cf. CommercialBonusPaymentService). */
    public boolean estPayable(CommercialPerformance perf, SycaPayTransaction transaction) {
        return (perf.getStatut() == StatutCommercialPerformance.VALIDATED || perf.getStatut() == StatutCommercialPerformance.ELIGIBLE)
                && perf.isEligible()
                && perf.getBonusAmount() != null && perf.getBonusAmount().compareTo(BigDecimal.ZERO) > 0
                && (transaction == null
                || transaction.getStatus() == SycaPayTransactionStatus.FAILED
                || transaction.getStatus() == SycaPayTransactionStatus.SUCCESS);
    }

    public CommercialPerformanceResponse toResponse(CommercialPerformance perf, SycaPayTransaction transaction) {
        MoisComptable mois = perf.getMoisComptable();
        BigDecimal total = perf.getBaseSalarySnapshot()
                .add(perf.getTransportBenefitSnapshot())
                .add(perf.getPhoneAllowanceSnapshot())
                .add(perf.getBonusAmount());
        BigDecimal seuil = seuilEligibilite();
        return new CommercialPerformanceResponse(
                perf.getId(), perf.getCommercial().getId(),
                mois.getId(), mois.getLibelle(), mois.getNumero(),
                perf.getObjectiveAmount(), perf.getRealizedAmount(), perf.getAchievementRate(),
                perf.getBonusRateSnapshot(), perf.getBonusAmount(), perf.isEligible(),
                perf.getBaseSalarySnapshot(), perf.getTransportBenefitSnapshot(), perf.getPhoneAllowanceSnapshot(), total,
                perf.getStatut(), perf.getCalculatedAt(),
                perf.getValidatedAt(), perf.getValidatedBy() == null ? null : perf.getValidatedBy().getId(),
                perf.getPaidAt(), perf.getPaidBy() == null ? null : perf.getPaidBy().getId(),
                perf.getNotificationEnvoyee(),
                !estVerrouille(perf.getStatut()),
                seuil,
                estPayable(perf, transaction),
                perf.getModePaiement(),
                perf.getPaymentReference(),
                transaction == null ? null : transaction.getId(),
                transaction == null ? null : transaction.getStatus().name(),
                transaction == null ? null : transaction.getTransactionReference(),
                transaction == null ? null : (transaction.getErrorMessage() != null ? transaction.getErrorMessage() : transaction.getSycapayResponseMessage()),
                transaction == null ? null : transaction.getRequestDate(),
                perf.getCommercial().getProfile() == null ? null : nomCommercial(perf.getCommercial()),
                perf.getCommercial().getEmployeeCode()
        );
    }
}
