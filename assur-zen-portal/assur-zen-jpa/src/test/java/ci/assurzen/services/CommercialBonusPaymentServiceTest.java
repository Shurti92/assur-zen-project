package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CommercialBonusPaymentRequest;
import ci.assurzen.dto.CommercialBonusPaymentResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialPerformance;
import ci.assurzen.entities.MoisComptable;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.StatutCommercial;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Paiement reel du bonus mensuel (2026-10-02) : regles d'eligibilite (non valide, non eligible,
 * montant nul, deja paye), mode hybride (SycaPay desactive), et chaque issue du virement SycaPay
 * (SUCCESS, FAILED, PROCESSING, service indisponible, virement deja en cours), plus la reconciliation.
 * Le bonus ne passe a PAID - et le grand livre n'est ecrit - QUE si le virement est confirme.
 */
class CommercialBonusPaymentServiceTest {

    private record Fixture(
            CommercialBonusPaymentService service,
            CommercialPerformanceRepository repository,
            CommercialPerformanceService performanceService,
            SycaPayPaymentService sycaPayPaymentService,
            SycaPayTransactionRepository sycaPayTransactionRepository,
            SycaPayProperties sycaPayProperties,
            LedgerService ledgerService,
            CommercialService commercialService
    ) {
    }

    private Fixture newFixture(CommercialPerformance perf) {
        CommercialPerformanceRepository repository = mock(CommercialPerformanceRepository.class);
        MoisComptableRepository moisComptableRepository = mock(MoisComptableRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CommercialService commercialService = mock(CommercialService.class);
        CommercialPerformanceService performanceService = mock(CommercialPerformanceService.class);
        SycaPayPaymentService sycaPayPaymentService = mock(SycaPayPaymentService.class);
        SycaPayTransactionRepository sycaPayTransactionRepository = mock(SycaPayTransactionRepository.class);
        SycaPayProperties sycaPayProperties = mock(SycaPayProperties.class);
        LedgerService ledgerService = mock(LedgerService.class);
        TelegramNotificationService telegramNotificationService = mock(TelegramNotificationService.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(moisComptableRepository.findById(10L)).thenReturn(Optional.of(perf.getMoisComptable()));
        when(repository.findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.of(perf));
        when(repository.findById(perf.getId())).thenReturn(Optional.of(perf));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(performanceService.nomCommercial(any())).thenReturn("Jean Koffi");
        when(performanceService.formatMontant(any())).thenReturn("50 000");
        when(performanceService.notifierBonusPaye(any(), any(), any())).thenReturn(true);
        when(sycaPayProperties.isEnabled()).thenReturn(true);

        CommercialBonusPaymentService service = new CommercialBonusPaymentService(repository, moisComptableRepository,
                userRepository, commercialService, performanceService, sycaPayPaymentService, sycaPayTransactionRepository,
                sycaPayProperties, ledgerService, telegramNotificationService, authService, functionalAuditRecorder,
                transactionManager);
        return new Fixture(service, repository, performanceService, sycaPayPaymentService, sycaPayTransactionRepository,
                sycaPayProperties, ledgerService, commercialService);
    }

    private CommercialPerformance performance(StatutCommercialPerformance statut, boolean eligible, BigDecimal bonus) {
        User user = new User();
        user.setId(101L);
        Profile profile = Profile.builder().id(201L).firstname("Jean").lastname("Koffi").phone("0700000000").user(user).build();
        Commercial commercial = Commercial.builder().id(1L).employeeCode("COM-000001").profile(profile)
                .baseSalary(BigDecimal.valueOf(300000)).statut(StatutCommercial.ACTIVE).build();
        MoisComptable mois = MoisComptable.builder().id(10L).numero(1).libelle("Janvier 2026")
                .dateDebut(LocalDate.of(2026, 1, 1)).dateFin(LocalDate.of(2026, 1, 31)).build();
        return CommercialPerformance.builder()
                .id(50L).commercial(commercial).moisComptable(mois)
                .objectiveAmount(BigDecimal.valueOf(1_000_000)).realizedAmount(BigDecimal.valueOf(1_100_000))
                .achievementRate(eligible ? BigDecimal.valueOf(110) : BigDecimal.valueOf(80)).bonusRateSnapshot(BigDecimal.valueOf(5))
                .bonusAmount(bonus).eligible(eligible)
                .baseSalarySnapshot(BigDecimal.valueOf(300000)).transportBenefitSnapshot(BigDecimal.ZERO)
                .phoneAllowanceSnapshot(BigDecimal.ZERO).statut(statut).build();
    }

    private CommercialPerformance bonusValide() {
        return performance(StatutCommercialPerformance.VALIDATED, true, BigDecimal.valueOf(50000));
    }

    private SycaPayTransaction transaction(SycaPayTransactionStatus status) {
        SycaPayTransaction tx = new SycaPayTransaction();
        tx.setId(900L);
        tx.setCommercialPerformanceId(50L);
        tx.setTransactionReference("SYC-900");
        tx.setStatus(status);
        tx.setAmount(50000L);
        tx.setCreatedBy("admin");
        return tx;
    }

    private void assertConflit(Fixture fx) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().payer(1L, 10L, new CommercialBonusPaymentRequest(null, null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.sycaPayPaymentService(), never()).payCommission(any());
        verify(fx.ledgerService(), never()).enregistrerBonusCommercial(any(), any(), any());
    }

    // ---------------------------------------------------------------- Eligibilite

    private ci.assurzen.dto.CommercialPerformanceResponse recalcul(boolean eligible, BigDecimal bonus) {
        return new ci.assurzen.dto.CommercialPerformanceResponse(50L, 1L, 10L, "Janvier 2026", 1,
                BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(eligible ? 1_100_000 : 800_000),
                BigDecimal.valueOf(eligible ? 110 : 80), BigDecimal.valueOf(5), bonus, eligible,
                BigDecimal.valueOf(300000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(350000),
                eligible ? StatutCommercialPerformance.ELIGIBLE : StatutCommercialPerformance.NOT_ELIGIBLE,
                Instant.now(), null, null, null, null, null,
                true, BigDecimal.valueOf(100), eligible, null, null, null, null, null, null, null, "Jean Koffi", "COM-000001");
    }

    /** Objectif atteint, pas encore valide : recalcul + validation (montant fige) puis virement, en une action. */
    @Test
    void payer_objectifAtteintNonEncoreValide_estRecalculeValidePuisPaye() {
        CommercialPerformance perf = performance(StatutCommercialPerformance.ELIGIBLE, true, BigDecimal.valueOf(50000));
        Fixture fx = newFixture(perf);
        when(fx.performanceService().recalculer(1L, 10L)).thenReturn(recalcul(true, BigDecimal.valueOf(50000)));
        when(fx.performanceService().changerStatut(eq(1L), eq(10L), eq(StatutCommercialPerformance.VALIDATED), any()))
                .thenAnswer(inv -> {
                    perf.setStatut(StatutCommercialPerformance.VALIDATED);
                    return null;
                });
        when(fx.sycaPayPaymentService().payCommission(any()))
                .thenReturn(SycaPayPaymentOutcome.of(SycaPayTransactionStatus.SUCCESS, 900L, "SYC-900", "OK"));

        CommercialBonusPaymentResponse response = fx.service().payer(1L, 10L, null);

        assertEquals("SUCCESS", response.sycapayStatus());
        assertEquals(StatutCommercialPerformance.PAID, perf.getStatut());
        org.mockito.InOrder ordre = org.mockito.Mockito.inOrder(fx.performanceService(), fx.sycaPayPaymentService());
        ordre.verify(fx.performanceService()).recalculer(1L, 10L);
        ordre.verify(fx.performanceService()).changerStatut(eq(1L), eq(10L), eq(StatutCommercialPerformance.VALIDATED), any());
        ordre.verify(fx.sycaPayPaymentService()).payCommission(any());
    }

    @Test
    void payer_objectifNonAtteint_estRejeteAvec409SansValidation() {
        Fixture fx = newFixture(performance(StatutCommercialPerformance.ELIGIBLE, true, BigDecimal.valueOf(50000)));
        // Le recalcul du jour montre que l'objectif n'est plus atteint (contrat annule entre-temps...).
        when(fx.performanceService().recalculer(1L, 10L)).thenReturn(recalcul(false, BigDecimal.ZERO));

        assertConflit(fx);
        verify(fx.performanceService(), never()).changerStatut(any(), any(), any(), any());
    }

    @Test
    void payer_bonusValideNonEligible_estRejeteAvec409() {
        assertConflit(newFixture(performance(StatutCommercialPerformance.VALIDATED, false, BigDecimal.ZERO)));
    }

    @Test
    void payer_montantNul_estRejeteAvec409() {
        assertConflit(newFixture(performance(StatutCommercialPerformance.VALIDATED, true, BigDecimal.ZERO)));
    }

    @Test
    void payer_bonusDejaPaye_estRejeteAvec409() {
        CommercialPerformance perf = performance(StatutCommercialPerformance.PAID, true, BigDecimal.valueOf(50000));
        perf.setPaidAt(Instant.now());
        assertConflit(newFixture(perf));
    }

    @Test
    void payer_performanceJamaisCalculee_estRejeteAvec409() {
        Fixture fx = newFixture(bonusValide());
        when(fx.repository().findByCommercialIdAndMoisComptableId(1L, 10L)).thenReturn(Optional.empty());
        when(fx.performanceService().recalculer(1L, 10L)).thenReturn(recalcul(false, BigDecimal.ZERO));
        assertConflit(fx);
    }

    @Test
    void payer_sansNumeroDeTelephone_estRejeteAvec409() {
        CommercialPerformance perf = bonusValide();
        perf.getCommercial().getProfile().setPhone(" ");
        assertConflit(newFixture(perf));
    }

    @Test
    void payer_sansDroitsAdministrateur_estRejeteAvec403() {
        Fixture fx = newFixture(bonusValide());
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs"))
                .when(fx.commercialService()).requireAdmin();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().payer(1L, 10L, null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fx.sycaPayPaymentService(), never()).payCommission(any());
    }

    // ---------------------------------------------------------------- Issues du virement SycaPay

    @Test
    void payer_virementReussi_passeAPaidEcritLeGrandLivreEtNotifie() {
        CommercialPerformance perf = bonusValide();
        Fixture fx = newFixture(perf);
        when(fx.sycaPayPaymentService().payCommission(any()))
                .thenReturn(SycaPayPaymentOutcome.of(SycaPayTransactionStatus.SUCCESS, 900L, "SYC-900", "OK"));
        when(fx.sycaPayTransactionRepository().findById(900L)).thenReturn(Optional.of(transaction(SycaPayTransactionStatus.SUCCESS)));

        CommercialBonusPaymentResponse response = fx.service().payer(1L, 10L, new CommercialBonusPaymentRequest("REF-1", null));

        assertEquals("SUCCESS", response.sycapayStatus());
        assertEquals(CommercialBonusPaymentService.MODE_VIREMENT, response.modePaiement());
        assertEquals(StatutCommercialPerformance.PAID, perf.getStatut());
        assertNotNull(perf.getPaidAt());
        assertEquals(900L, perf.getSycapayTransactionId());
        assertEquals("REF-1", perf.getPaymentReference());
        assertEquals(Boolean.TRUE, perf.getNotificationEnvoyee());
        verify(fx.ledgerService(), times(1)).enregistrerBonusCommercial("Jean Koffi", "Janvier 2026", 50000L);

        ArgumentCaptor<SycaPayPaymentCommand> commande = ArgumentCaptor.forClass(SycaPayPaymentCommand.class);
        verify(fx.sycaPayPaymentService()).payCommission(commande.capture());
        assertEquals(50L, commande.getValue().commercialPerformanceId());
        assertNull(commande.getValue().contractId());
        assertEquals(50000L, commande.getValue().amount());
        assertEquals("0700000000", commande.getValue().beneficiaryPhone());
    }

    @Test
    void payer_virementEchoue_resteValideRelancableSansEcritureComptable() {
        CommercialPerformance perf = bonusValide();
        Fixture fx = newFixture(perf);
        when(fx.sycaPayPaymentService().payCommission(any()))
                .thenReturn(SycaPayPaymentOutcome.of(SycaPayTransactionStatus.FAILED, 900L, "SYC-900", "Solde Wave insuffisant"));

        CommercialBonusPaymentResponse response = fx.service().payer(1L, 10L, null);

        assertEquals("FAILED", response.sycapayStatus());
        assertEquals("Solde Wave insuffisant", response.sycapayMessage());
        assertEquals(StatutCommercialPerformance.VALIDATED, perf.getStatut());
        assertNull(perf.getPaidAt());
        assertEquals(900L, perf.getSycapayTransactionId()); // trace du virement tente
        verify(fx.ledgerService(), never()).enregistrerBonusCommercial(any(), any(), any());
        verify(fx.performanceService(), never()).notifierBonusPaye(any(), any(), any());
    }

    @Test
    void payer_virementEnCoursCoteSycaPay_resteValideEnAttenteDeReconciliation() {
        CommercialPerformance perf = bonusValide();
        Fixture fx = newFixture(perf);
        when(fx.sycaPayPaymentService().payCommission(any()))
                .thenReturn(SycaPayPaymentOutcome.of(SycaPayTransactionStatus.PROCESSING, 900L, "SYC-900", "Traitement en cours"));

        CommercialBonusPaymentResponse response = fx.service().payer(1L, 10L, null);

        assertEquals("PROCESSING", response.sycapayStatus());
        assertEquals(StatutCommercialPerformance.VALIDATED, perf.getStatut());
        verify(fx.ledgerService(), never()).enregistrerBonusCommercial(any(), any(), any());
    }

    @Test
    void payer_serviceSycaPayIndisponible_renvoieRejectedSansAucunVirement() {
        CommercialPerformance perf = bonusValide();
        Fixture fx = newFixture(perf);
        when(fx.sycaPayPaymentService().payCommission(any())).thenThrow(new SycaPayException("identifiants incomplets"));

        CommercialBonusPaymentResponse response = fx.service().payer(1L, 10L, null);

        assertEquals("REJECTED", response.sycapayStatus());
        assertEquals(StatutCommercialPerformance.VALIDATED, perf.getStatut());
        verify(fx.ledgerService(), never()).enregistrerBonusCommercial(any(), any(), any());
    }

    @Test
    void payer_virementDejaEnCours_neRelanceJamaisDeSecondVirement() {
        Fixture fx = newFixture(bonusValide());
        when(fx.sycaPayTransactionRepository().findByCommercialPerformanceId(50L))
                .thenReturn(Optional.of(transaction(SycaPayTransactionStatus.UNKNOWN)));

        CommercialBonusPaymentResponse response = fx.service().payer(1L, 10L, null);

        assertEquals("UNKNOWN", response.sycapayStatus());
        verify(fx.sycaPayPaymentService(), never()).payCommission(any());
    }

    @Test
    void payer_virementDejaConfirmeMaisBonusNonEnregistre_rattrapeSansNouveauVirement() {
        CommercialPerformance perf = bonusValide();
        Fixture fx = newFixture(perf);
        when(fx.sycaPayTransactionRepository().findByCommercialPerformanceId(50L))
                .thenReturn(Optional.of(transaction(SycaPayTransactionStatus.SUCCESS)));

        CommercialBonusPaymentResponse response = fx.service().payer(1L, 10L, null);

        assertEquals("SUCCESS", response.sycapayStatus());
        assertEquals(StatutCommercialPerformance.PAID, perf.getStatut());
        verify(fx.sycaPayPaymentService(), never()).payCommission(any());
        verify(fx.ledgerService(), times(1)).enregistrerBonusCommercial(anyString(), anyString(), anyLong());
    }

    // ---------------------------------------------------------------- Mode hybride

    @Test
    void payer_sycaPayDesactive_enregistreLePaiementHorsApplication() {
        CommercialPerformance perf = bonusValide();
        Fixture fx = newFixture(perf);
        when(fx.sycaPayProperties().isEnabled()).thenReturn(false);

        CommercialBonusPaymentResponse response = fx.service().payer(1L, 10L, new CommercialBonusPaymentRequest("VIR-BANQUE-12", null));

        assertEquals(CommercialBonusPaymentService.MODE_HORS_APPLICATION, response.modePaiement());
        assertNull(response.sycapayStatus());
        assertEquals(StatutCommercialPerformance.PAID, perf.getStatut());
        assertEquals("VIR-BANQUE-12", perf.getPaymentReference());
        verify(fx.sycaPayPaymentService(), never()).payCommission(any());
        verify(fx.ledgerService(), times(1)).enregistrerBonusCommercial(anyString(), anyString(), eq(50000L));
    }

    // ---------------------------------------------------------------- Reconciliation

    @Test
    void confirmerVirementBonus_virementReconcilieReussi_passeAPaidUneSeuleFois() {
        CommercialPerformance perf = bonusValide();
        perf.setSycapayTransactionId(900L);
        Fixture fx = newFixture(perf);
        when(fx.sycaPayTransactionRepository().findById(900L)).thenReturn(Optional.of(transaction(SycaPayTransactionStatus.SUCCESS)));

        fx.service().confirmerVirementBonus(900L, "admin");
        fx.service().confirmerVirementBonus(900L, "admin"); // idempotent

        assertEquals(StatutCommercialPerformance.PAID, perf.getStatut());
        assertEquals(CommercialBonusPaymentService.MODE_VIREMENT, perf.getModePaiement());
        verify(fx.ledgerService(), times(1)).enregistrerBonusCommercial(anyString(), anyString(), anyLong());
    }

    @Test
    void confirmerVirementBonus_ignoreLesVirementsQuiNeSontPasDesBonus() {
        Fixture fx = newFixture(bonusValide());
        SycaPayTransaction commission = transaction(SycaPayTransactionStatus.SUCCESS);
        commission.setCommercialPerformanceId(null);
        commission.setContractId(5L);
        when(fx.sycaPayTransactionRepository().findById(900L)).thenReturn(Optional.of(commission));

        fx.service().confirmerVirementBonus(900L, "admin");

        verify(fx.repository(), never()).save(any());
        verify(fx.ledgerService(), never()).enregistrerBonusCommercial(any(), any(), any());
    }
}
