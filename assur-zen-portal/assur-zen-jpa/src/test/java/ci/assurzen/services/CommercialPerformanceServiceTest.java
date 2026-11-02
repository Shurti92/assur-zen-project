package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommercialDashboardResponse;
import ci.assurzen.dto.CommercialManagementDashboardResponse;
import ci.assurzen.dto.CommercialPerformanceResponse;
import ci.assurzen.dto.CommercialRankingEntry;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialMonthlyObjective;
import ci.assurzen.entities.CommercialObjectiveType;
import ci.assurzen.entities.CommercialPerformance;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.Profile;
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
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre le coeur du calcul (§48-51 : le bonus est toujours 5% de l'OBJECTIF, jamais du realise,
 * meme en depassement), le verrouillage d'une periode validee (§31), et les transitions de statut
 * du workflow bonus (§29). CommercialProductionService est mocke (collaborateur) - ce test se
 * concentre sur la logique propre a CommercialPerformanceService, pas sur le calcul de production.
 */
class CommercialPerformanceServiceTest {

    private record Fixture(
            CommercialPerformanceService service,
            CommercialPerformanceRepository performanceRepository,
            CommercialMonthlyObjectiveRepository monthlyObjectiveRepository,
            CommercialRepository commercialRepository,
            MoisComptableRepository moisComptableRepository,
            UserRepository userRepository,
            CommercialService commercialService,
            CommercialProductionService productionService,
            DynamicConfigurationService dynamicConfigurationService,
            SmsNotificationService smsNotificationService,
            TelegramNotificationService telegramNotificationService,
            AuthService authService,
            FunctionalAuditRecorder functionalAuditRecorder,
            SycaPayTransactionRepository sycaPayTransactionRepository
    ) {
    }

    private Fixture newFixture() {
        CommercialPerformanceRepository performanceRepository = mock(CommercialPerformanceRepository.class);
        CommercialMonthlyObjectiveRepository monthlyObjectiveRepository = mock(CommercialMonthlyObjectiveRepository.class);
        CommercialRepository commercialRepository = mock(CommercialRepository.class);
        MoisComptableRepository moisComptableRepository = mock(MoisComptableRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CommercialService commercialService = mock(CommercialService.class);
        CommercialProductionService productionService = mock(CommercialProductionService.class);
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        TelegramNotificationService telegramNotificationService = mock(TelegramNotificationService.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);
        SycaPayTransactionRepository sycaPayTransactionRepository = mock(SycaPayTransactionRepository.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        // Bareme par defaut = memes valeurs que le seed data.sql (COMMERCIAL_BONUS_RATE=5,
        // COMMERCIAL_BONUS_MIN_ACHIEVEMENT_RATE=100, COMMERCIAL_BONUS_ENABLED=true).
        when(dynamicConfigurationService.getBoolean(eq("COMMERCIAL_BONUS_ENABLED"), any())).thenReturn(true);
        when(dynamicConfigurationService.getDecimal(eq("COMMERCIAL_BONUS_RATE"), any())).thenReturn(BigDecimal.valueOf(5));
        when(dynamicConfigurationService.getDecimal(eq("COMMERCIAL_BONUS_MIN_ACHIEVEMENT_RATE"), any())).thenReturn(BigDecimal.valueOf(100));

        AtomicLong idSeq = new AtomicLong(1);
        when(performanceRepository.save(any())).thenAnswer(inv -> {
            CommercialPerformance p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(idSeq.getAndIncrement());
            }
            return p;
        });

        CommercialPerformanceService service = new CommercialPerformanceService(
                performanceRepository, monthlyObjectiveRepository, commercialRepository, moisComptableRepository,
                userRepository, commercialService, productionService, dynamicConfigurationService,
                smsNotificationService, telegramNotificationService, authService, functionalAuditRecorder,
                sycaPayTransactionRepository, mock(org.springframework.context.ApplicationEventPublisher.class));

        return new Fixture(service, performanceRepository, monthlyObjectiveRepository, commercialRepository,
                moisComptableRepository, userRepository, commercialService, productionService,
                dynamicConfigurationService, smsNotificationService, telegramNotificationService,
                authService, functionalAuditRecorder, sycaPayTransactionRepository);
    }

    private Commercial commercial(long id) {
        User user = new User();
        user.setId(100 + id);
        user.setUsername("commercial" + id);
        Profile profile = Profile.builder().id(200 + id).firstname("Jean").lastname("Koffi")
                .phone("0700000000").user(user).build();
        return Commercial.builder().id(id).employeeCode("COM-00000" + id).profile(profile)
                .baseSalary(BigDecimal.valueOf(300000)).transportBenefitAmount(BigDecimal.ZERO)
                .phoneAllowanceAmount(BigDecimal.ZERO).statut(StatutCommercial.ACTIVE).build();
    }

    private MoisComptable mois(long id) {
        return MoisComptable.builder().id(id).numero(1).libelle("Janvier 2026")
                .dateDebut(LocalDate.of(2026, 1, 1)).dateFin(LocalDate.of(2026, 1, 31)).cloture(false).build();
    }

    /** Cable objectif/realise/taux pour un couple (commercial, mois) donne - objectifNullable=null simule
     * l'absence d'objectif mensuel defini (orElse(ZERO) cote service). */
    private void avecProduction(Fixture fx, Commercial c, MoisComptable m, BigDecimal objectifNullable, BigDecimal realise, BigDecimal taux) {
        BigDecimal objectifEffectif = objectifNullable == null ? BigDecimal.ZERO : objectifNullable;
        when(fx.monthlyObjectiveRepository().findByCommercialIdAndMoisComptableIdAndObjectiveType(
                c.getId(), m.getId(), CommercialObjectiveType.PREMIUM_AMOUNT))
                .thenReturn(objectifNullable == null ? Optional.empty() : Optional.of(CommercialMonthlyObjective.builder()
                        .commercial(c).moisComptable(m).objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT)
                        .targetAmount(objectifNullable).build()));
        when(fx.productionService().computeRealise(c, m)).thenReturn(realise);
        when(fx.productionService().computeTauxRealisation(realise, objectifEffectif)).thenReturn(taux);
    }

    private void avecCommercialEtMoisResolubles(Fixture fx, Commercial c, MoisComptable m) {
        when(fx.commercialService().getCommercial(c.getId())).thenReturn(c);
        when(fx.moisComptableRepository().findById(m.getId())).thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(c.getId(), m.getId())).thenReturn(Optional.empty());
    }

    // ---------------------------------------------------------------- Formule du bonus (§48-51)

    @Test
    void recalculer_tauxExactement100Pourcent_estEligibleEtBonusEgal5PourcentDeLObjectif() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        avecCommercialEtMoisResolubles(fx, c, m);
        avecProduction(fx, c, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(100));

        CommercialPerformanceResponse response = fx.service().recalculer(1L, 10L);

        assertTrue(response.eligible());
        assertEquals(StatutCommercialPerformance.ELIGIBLE, response.statut());
        assertEquals(0, BigDecimal.valueOf(250000).compareTo(response.bonusAmount()));
    }

    @Test
    void recalculer_tauxSousLeSeuil_99_99Pourcent_nEstPasEligibleEtBonusZero() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        avecCommercialEtMoisResolubles(fx, c, m);
        avecProduction(fx, c, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(4_999_999), new BigDecimal("99.9998"));

        CommercialPerformanceResponse response = fx.service().recalculer(1L, 10L);

        assertFalse(response.eligible());
        assertEquals(StatutCommercialPerformance.NOT_ELIGIBLE, response.statut());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.bonusAmount()));
    }

    @Test
    void recalculer_objectifDepasse_leBonusResteBaseeSurLObjectifPasSurLeRealise_Regle51() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        avecCommercialEtMoisResolubles(fx, c, m);
        // Objectif 5M, realise 8M (160%) - le bonus doit rester 5% x 5M = 250 000, jamais 5% x 8M = 400 000.
        avecProduction(fx, c, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(8_000_000), BigDecimal.valueOf(160));

        CommercialPerformanceResponse response = fx.service().recalculer(1L, 10L);

        assertTrue(response.eligible());
        assertEquals(0, BigDecimal.valueOf(250000).compareTo(response.bonusAmount()));
    }

    @Test
    void recalculer_aucunObjectifMensuelDefini_nEstJamaisEligibleMemeSiProductionPositive() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        avecCommercialEtMoisResolubles(fx, c, m);
        avecProduction(fx, c, m, null, BigDecimal.valueOf(2_000_000), BigDecimal.ZERO);

        CommercialPerformanceResponse response = fx.service().recalculer(1L, 10L);

        assertFalse(response.eligible());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.bonusAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.objectiveAmount()));
    }

    @Test
    void recalculer_bonusDesactiveGlobalement_nEstJamaisEligibleMemeAuDelaDuSeuil() {
        Fixture fx = newFixture();
        when(fx.dynamicConfigurationService().getBoolean(eq("COMMERCIAL_BONUS_ENABLED"), any())).thenReturn(false);
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        avecCommercialEtMoisResolubles(fx, c, m);
        avecProduction(fx, c, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(7_000_000), BigDecimal.valueOf(140));

        CommercialPerformanceResponse response = fx.service().recalculer(1L, 10L);

        assertFalse(response.eligible());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.bonusAmount()));
    }

    @Test
    void recalculer_grosMontants_calculeAvecPrecisionEnBigDecimal_Regle67() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        avecCommercialEtMoisResolubles(fx, c, m);
        avecProduction(fx, c, m, BigDecimal.valueOf(100_000_000), BigDecimal.valueOf(100_000_000), BigDecimal.valueOf(100));

        CommercialPerformanceResponse response = fx.service().recalculer(1L, 10L);

        assertEquals(0, BigDecimal.valueOf(5_000_000).compareTo(response.bonusAmount()));
    }

    /** Bug reel du 2026-09-17 : un objectif mensuel fixe anormalement bas (ex. 1 FCFA) face a un
     * realise normal produit un taux de plusieurs millions de % - hors de portee de la colonne
     * achievement_rate (DECIMAL(9,4), max 99999.9999), Data truncation SQL a la persistance
     * (remontee en 409 generique par GlobalExceptionHandler avant meme d'atteindre ce test). Verifie
     * que le taux est desormais borne a la valeur max representable AVANT d'etre utilise (eligibilite
     * incluse) ou renvoye - jamais une exception, jamais une valeur hors de portee de la colonne. */
    @Test
    void recalculer_tauxAstronomique_estBorneALaValeurMaxRepresentableParLaColonne() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        avecCommercialEtMoisResolubles(fx, c, m);
        // objectif=1, realise=100000 -> taux brut = 10 000 000% (10 millions), hors de portee de
        // DECIMAL(9,4) (max 99999.9999).
        avecProduction(fx, c, m, BigDecimal.ONE, BigDecimal.valueOf(100_000), BigDecimal.valueOf(10_000_000));

        CommercialPerformanceResponse response = fx.service().recalculer(1L, 10L);

        assertEquals(0, BigDecimal.valueOf(99999.9999).compareTo(response.achievementRate()));
        assertTrue(response.eligible());
        assertEquals(StatutCommercialPerformance.ELIGIBLE, response.statut());
    }

    // ---------------------------------------------------------------- Verrouillage d'une periode validee (§31)

    @Test
    void recalculer_periodeDejaValidee_estRejeteAvec409() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        CommercialPerformance verrouillee = performance(c, m, StatutCommercialPerformance.VALIDATED, true);
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(verrouillee));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().recalculer(1L, 10L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void dashboard_periodeDejaValidee_renvoieLeSnapshotFigeSansErreurEtSansRecalcul() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        LocalDate aujourdhui = LocalDate.now();
        when(fx.commercialService().currentUserId()).thenReturn(1L);
        when(fx.commercialService().getCommercialByUser(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findFirstByDateDebutLessThanEqualAndDateFinGreaterThanEqualOrderByDateDebutDesc(aujourdhui, aujourdhui))
                .thenReturn(Optional.of(m));
        CommercialPerformance validee = performance(c, m, StatutCommercialPerformance.VALIDATED, true);
        validee.setBonusAmount(BigDecimal.valueOf(250000));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(validee));

        CommercialDashboardResponse dashboard = fx.service().dashboard();

        assertFalse(dashboard.bonusEstime());
        assertEquals(0, BigDecimal.valueOf(250000).compareTo(dashboard.bonusPotentiel()));
        verify(fx.productionService(), never()).computeRealise(any(), any());
    }

    @Test
    void dashboard_periodeEnCours_bonusToujoursQualifieDEstime() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        LocalDate aujourdhui = LocalDate.now();
        when(fx.commercialService().currentUserId()).thenReturn(1L);
        when(fx.commercialService().getCommercialByUser(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findFirstByDateDebutLessThanEqualAndDateFinGreaterThanEqualOrderByDateDebutDesc(aujourdhui, aujourdhui))
                .thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.empty());
        avecProduction(fx, c, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(100));
        when(fx.productionService().countContratsEligibles(c, m)).thenReturn(3L);

        CommercialDashboardResponse dashboard = fx.service().dashboard();

        assertTrue(dashboard.bonusEstime());
        assertEquals(3L, dashboard.nombreContrats());
        assertEquals(0, BigDecimal.ZERO.compareTo(dashboard.resteARealiser()));
    }

    // ---------------------------------------------------------------- Concurrence : les lectures n'ecrivent jamais
    // (bug reproductible signale par le frontend le 2026-09-07 : GET /commercial-performance/dashboard
    // + GET /commercial-performance/ranking en parallele sur le meme mois se percutaient sur le verrou
    // optimiste de CommercialPerformance, car les 4 endpoints de lecture appelaient tous une methode qui
    // faisait repository.save(). Ces tests prouvent la cause racine corrigee : aucune des 4 methodes de
    // lecture n'appelle plus jamais save(), donc deux lectures concurrentes ne peuvent plus se percuter,
    // quel que soit le nombre de threads reels.)

    @Test
    void dashboard_neSauvegardeJamaisRienEnBase() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        LocalDate aujourdhui = LocalDate.now();
        when(fx.commercialService().currentUserId()).thenReturn(1L);
        when(fx.commercialService().getCommercialByUser(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findFirstByDateDebutLessThanEqualAndDateFinGreaterThanEqualOrderByDateDebutDesc(aujourdhui, aujourdhui))
                .thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.empty());
        avecProduction(fx, c, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(100));
        when(fx.productionService().countContratsEligibles(c, m)).thenReturn(1L);

        fx.service().dashboard();

        verify(fx.performanceRepository(), never()).save(any());
    }

    @Test
    void getPerformance_neSauvegardeJamaisRienEnBase() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        avecCommercialEtMoisResolubles(fx, c, m);
        avecProduction(fx, c, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(100));

        fx.service().getPerformance(1L, 10L);

        verify(fx.performanceRepository(), never()).save(any());
    }

    @Test
    void ranking_neSauvegardeJamaisRienEnBase() {
        Fixture fx = newFixture();
        Commercial c1 = commercial(1);
        Commercial c2 = commercial(2);
        MoisComptable m = mois(10);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.commercialRepository().findByStatut(StatutCommercial.ACTIVE)).thenReturn(List.of(c1, c2));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.empty());
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(2L, 10L)).thenReturn(Optional.empty());
        avecProduction(fx, c1, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(5_500_000), BigDecimal.valueOf(110));
        avecProduction(fx, c2, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(60));

        List<CommercialRankingEntry> classement = fx.service().ranking(10L);

        assertEquals(2, classement.size());
        assertEquals(1L, classement.get(0).commercialId()); // 110% avant 60%
        assertEquals(1, classement.get(0).rang());
        assertEquals(2, classement.get(1).rang());
        verify(fx.performanceRepository(), never()).save(any());
    }

    @Test
    void managementDashboard_neSauvegardeJamaisRienEnBaseEtAgregeLesActifs() {
        Fixture fx = newFixture();
        Commercial c1 = commercial(1);
        Commercial c2 = commercial(2);
        MoisComptable m = mois(10);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.commercialRepository().findByStatut(StatutCommercial.ACTIVE)).thenReturn(List.of(c1, c2));
        when(fx.commercialRepository().count()).thenReturn(2L);
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.empty());
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(2L, 10L)).thenReturn(Optional.empty());
        avecProduction(fx, c1, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(100));
        avecProduction(fx, c2, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(4_000_000), BigDecimal.valueOf(80));

        CommercialManagementDashboardResponse dashboard = fx.service().managementDashboard(10L);

        assertEquals(1, dashboard.eligibleCommercials());
        assertEquals(1, dashboard.nonEligibleCommercials());
        assertEquals(0, BigDecimal.valueOf(10_000_000).compareTo(dashboard.monthlyObjective()));
        assertEquals(0, BigDecimal.valueOf(9_000_000).compareTo(dashboard.monthlyProduction()));
        verify(fx.performanceRepository(), never()).save(any());
    }

    @Test
    void recalculer_persisteReellementEnBase_contrairementAuxLectures() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        avecCommercialEtMoisResolubles(fx, c, m);
        avecProduction(fx, c, m, BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(5_000_000), BigDecimal.valueOf(100));

        fx.service().recalculer(1L, 10L);

        verify(fx.performanceRepository(), org.mockito.Mockito.times(1)).save(any());
    }

    // ---------------------------------------------------------------- Workflow (§29)

    @Test
    void changerStatut_eligibleVersValidated_notifieTelegramEtEnregistreLeValidateur() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        CommercialPerformance perf = performance(c, m, StatutCommercialPerformance.ELIGIBLE, true);
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(perf));

        CommercialPerformanceResponse response = fx.service().changerStatut(1L, 10L, StatutCommercialPerformance.VALIDATED, null);

        assertEquals(StatutCommercialPerformance.VALIDATED, response.statut());
        assertEquals(1L, response.validatedById());
        verify(fx.telegramNotificationService()).envoyer(anyString());
    }

    @Test
    void changerStatut_nonEligibleVersValidated_neNotifiePasTelegram() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        CommercialPerformance perf = performance(c, m, StatutCommercialPerformance.NOT_ELIGIBLE, false);
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(perf));

        fx.service().changerStatut(1L, 10L, StatutCommercialPerformance.VALIDATED, null);

        verify(fx.telegramNotificationService(), never()).envoyer(anyString());
    }

    /** 2026-10-02 : PAID n'est plus un simple changement de statut - le paiement passe exclusivement par
     * CommercialBonusPaymentService (virement reel), cf. CommercialBonusPaymentServiceTest. */
    @Test
    void changerStatut_validatedEligibleVersPaid_estRejeteAvec409_paiementParLActionDediee() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        CommercialPerformance perf = performance(c, m, StatutCommercialPerformance.VALIDATED, true);
        perf.setBonusAmount(BigDecimal.valueOf(250000));
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(perf));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().changerStatut(1L, 10L, StatutCommercialPerformance.PAID, null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(StatutCommercialPerformance.VALIDATED, perf.getStatut());
        verify(fx.smsNotificationService(), never()).sendFromTemplate(any(), any(), any(), any());
        verify(fx.performanceRepository(), never()).save(any());
    }

    @Test
    void changerStatut_validatedNonEligibleVersPaid_estRejeteAvec409() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        CommercialPerformance perf = performance(c, m, StatutCommercialPerformance.VALIDATED, false);
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(perf));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().changerStatut(1L, 10L, StatutCommercialPerformance.PAID, null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.smsNotificationService(), never()).sendFromTemplate(any(), any(), any(), any());
    }

    @Test
    void changerStatut_validatedNonEligibleVersClosed_estAccepteSansPaiement() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        CommercialPerformance perf = performance(c, m, StatutCommercialPerformance.VALIDATED, false);
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(perf));

        CommercialPerformanceResponse response = fx.service().changerStatut(1L, 10L, StatutCommercialPerformance.CLOSED, "cloture sans bonus");

        assertEquals(StatutCommercialPerformance.CLOSED, response.statut());
    }

    @Test
    void changerStatut_paidVersClosed_estAccepte() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        CommercialPerformance perf = performance(c, m, StatutCommercialPerformance.PAID, true);
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(perf));

        CommercialPerformanceResponse response = fx.service().changerStatut(1L, 10L, StatutCommercialPerformance.CLOSED, null);

        assertEquals(StatutCommercialPerformance.CLOSED, response.statut());
    }

    @Test
    void changerStatut_eligibleDirectementVersPaid_sauteValidated_estRejeteAvec409() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        CommercialPerformance perf = performance(c, m, StatutCommercialPerformance.ELIGIBLE, true);
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(perf));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().changerStatut(1L, 10L, StatutCommercialPerformance.PAID, null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void changerStatut_performanceIntrouvable_versClosed_estRejeteAvec404() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.moisComptableRepository().findById(10L)).thenReturn(Optional.of(m));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().changerStatut(1L, 10L, StatutCommercialPerformance.CLOSED, null));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    /** 2026-10-02 : un mois visible dans l'historique (recalcule en direct) mais jamais persiste peut etre
     * valide directement - la ligne est calculee et persistee d'abord, plus de 404. */
    @Test
    void changerStatut_performanceJamaisPersistee_versValidated_estCalculeePuisValidee() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        avecCommercialEtMoisResolubles(fx, c, m);
        avecProduction(fx, c, m, BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_200_000), BigDecimal.valueOf(120));

        CommercialPerformanceResponse response = fx.service().changerStatut(1L, 10L, StatutCommercialPerformance.VALIDATED, null);

        assertEquals(StatutCommercialPerformance.VALIDATED, response.statut());
        assertTrue(response.eligible());
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(response.bonusAmount()));
        assertFalse(response.estime());
        assertTrue(response.payable());
        verify(fx.performanceRepository(), org.mockito.Mockito.atLeast(2)).save(any());
    }

    // ---------------------------------------------------------------- Historique (correctif 2026-10-02)

    @Test
    void list_moisAvecObjectifSansLignePersistee_figureDansLHistoriqueRecalculeEnDirectSansSauvegarde() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.performanceRepository().findByCommercialIdOrderByMoisComptable_NumeroDesc(1L)).thenReturn(List.of());
        when(fx.monthlyObjectiveRepository().findByCommercialIdOrderByMoisComptable_NumeroDesc(1L)).thenReturn(List.of(
                CommercialMonthlyObjective.builder().commercial(c).moisComptable(m)
                        .objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.valueOf(1_000_000)).build()));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.empty());
        avecProduction(fx, c, m, BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(800_000), BigDecimal.valueOf(80));

        List<CommercialPerformanceResponse> historique = fx.service().list(1L);

        assertEquals(1, historique.size());
        assertEquals(10L, historique.get(0).moisComptableId());
        assertEquals(StatutCommercialPerformance.NOT_ELIGIBLE, historique.get(0).statut());
        assertTrue(historique.get(0).estime());
        assertFalse(historique.get(0).payable());
        verify(fx.performanceRepository(), never()).save(any());
    }

    @Test
    void list_moisFuturAvecObjectifDejaSaisi_estExcluDeLHistorique() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable futur = MoisComptable.builder().id(99L).numero(12).libelle("Decembre 2099")
                .dateDebut(LocalDate.of(2099, 12, 1)).dateFin(LocalDate.of(2099, 12, 31)).build();
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.monthlyObjectiveRepository().findByCommercialIdOrderByMoisComptable_NumeroDesc(1L)).thenReturn(List.of(
                CommercialMonthlyObjective.builder().commercial(c).moisComptable(futur)
                        .objectiveType(CommercialObjectiveType.PREMIUM_AMOUNT).targetAmount(BigDecimal.TEN).build()));

        assertTrue(fx.service().list(1L).isEmpty());
    }

    @Test
    void list_moisVerrouille_renvoieLeSnapshotFigeAvecLeStatutDuVirement() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        CommercialPerformance perf = performance(c, m, StatutCommercialPerformance.VALIDATED, true);
        perf.setId(7L);
        perf.setBonusAmount(BigDecimal.valueOf(50000));
        ci.assurzen.sycapay.entity.SycaPayTransaction tx = new ci.assurzen.sycapay.entity.SycaPayTransaction();
        tx.setId(70L);
        tx.setCommercialPerformanceId(7L);
        tx.setTransactionReference("SYC-1");
        tx.setStatus(ci.assurzen.sycapay.entity.SycaPayTransactionStatus.PROCESSING);
        when(fx.commercialService().getCommercial(1L)).thenReturn(c);
        when(fx.performanceRepository().findByCommercialIdOrderByMoisComptable_NumeroDesc(1L)).thenReturn(List.of(perf));
        when(fx.performanceRepository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(perf));
        when(fx.sycaPayTransactionRepository().findByCommercialPerformanceIdIn(List.of(7L))).thenReturn(List.of(tx));

        CommercialPerformanceResponse r = fx.service().list(1L).get(0);

        assertFalse(r.estime());
        assertEquals("PROCESSING", r.sycapayStatus());
        assertEquals("SYC-1", r.sycapayTransactionReference());
        // Virement en cours : surtout pas relancable.
        assertFalse(r.payable());
    }

    // ---------------------------------------------------------------- Securite (§68)

    @Test
    void recalculer_sansDroitsAdministrateur_estRejeteAvec403() {
        Fixture fx = newFixture();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs"))
                .when(fx.commercialService()).requireAdmin();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().recalculer(1L, 10L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fx.performanceRepository(), never()).findByCommercialIdAndMoisComptableId(any(), any());
    }

    @Test
    void changerStatut_sansDroitsAdministrateur_estRejeteAvec403() {
        Fixture fx = newFixture();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs"))
                .when(fx.commercialService()).requireAdmin();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().changerStatut(1L, 10L, StatutCommercialPerformance.VALIDATED, null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- Export CSV (§72)

    @Test
    void exportCsv_contientLenteteEtUneLigneParPerformanceAvecLaRemunerationTotale() {
        Fixture fx = newFixture();
        Commercial c = commercial(1);
        MoisComptable m = mois(10);
        CommercialPerformance perf = performance(c, m, StatutCommercialPerformance.PAID, true);
        perf.setBaseSalarySnapshot(BigDecimal.valueOf(300000));
        perf.setTransportBenefitSnapshot(BigDecimal.valueOf(50000));
        perf.setPhoneAllowanceSnapshot(BigDecimal.valueOf(20000));
        perf.setBonusAmount(BigDecimal.valueOf(250000));
        when(fx.performanceRepository().findAll()).thenReturn(List.of(perf));

        byte[] csv = fx.service().exportCsv(null, null);
        String contenu = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(contenu.contains("Matricule;Commercial;Mois;ObjectifMensuel;Realise;TauxAtteinte"));
        assertTrue(contenu.contains(c.getEmployeeCode()));
        // 300 000 + 50 000 + 20 000 + 250 000 = 620 000 (§48, exemple complet du cahier des charges).
        assertTrue(contenu.contains("620000"));
    }

    @Test
    void exportCsv_filtreParCommercialEtParMois() {
        Fixture fx = newFixture();
        Commercial c1 = commercial(1);
        Commercial c2 = commercial(2);
        MoisComptable m10 = mois(10);
        MoisComptable m11 = MoisComptable.builder().id(11L).numero(2).libelle("Fevrier 2026")
                .dateDebut(LocalDate.of(2026, 2, 1)).dateFin(LocalDate.of(2026, 2, 28)).build();
        CommercialPerformance p1 = performance(c1, m10, StatutCommercialPerformance.PAID, true);
        CommercialPerformance p2 = performance(c2, m10, StatutCommercialPerformance.PAID, true);
        CommercialPerformance p3 = performance(c1, m11, StatutCommercialPerformance.ELIGIBLE, true);
        when(fx.performanceRepository().findAll()).thenReturn(List.of(p1, p2, p3));

        byte[] csv = fx.service().exportCsv(1L, 10L);
        String contenu = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        long lignes = contenu.lines().count();

        assertEquals(2, lignes); // en-tete + 1 seule ligne (c1, mois 10)
        assertTrue(contenu.contains(c1.getEmployeeCode()));
    }

    @Test
    void exportCsv_sansDroitsAdministrateur_estRejeteAvec403() {
        Fixture fx = newFixture();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs"))
                .when(fx.commercialService()).requireAdmin();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().exportCsv(null, null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fx.performanceRepository(), never()).findAll();
    }

    // ---------------------------------------------------------------- Helpers

    private CommercialPerformance performance(Commercial c, MoisComptable m, StatutCommercialPerformance statut, boolean eligible) {
        return CommercialPerformance.builder()
                .id(50L).commercial(c).moisComptable(m)
                .objectiveAmount(BigDecimal.valueOf(5_000_000)).realizedAmount(BigDecimal.valueOf(5_000_000))
                .achievementRate(BigDecimal.valueOf(100)).bonusRateSnapshot(BigDecimal.valueOf(5))
                .bonusAmount(eligible ? BigDecimal.valueOf(250000) : BigDecimal.ZERO).eligible(eligible)
                .baseSalarySnapshot(BigDecimal.valueOf(300000)).transportBenefitSnapshot(BigDecimal.ZERO)
                .phoneAllowanceSnapshot(BigDecimal.ZERO).statut(statut).build();
    }
}
