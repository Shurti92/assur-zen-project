package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CreateFundOutflowRequest;
import ci.assurzen.dto.DisburseFundOutflowRequest;
import ci.assurzen.dto.FundOutflowDisbursementResponse;
import ci.assurzen.dto.FundOutflowResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.DisbursementMethod;
import ci.assurzen.entities.DisbursementStatus;
import ci.assurzen.entities.FundOutflow;
import ci.assurzen.entities.FundOutflowDisbursement;
import ci.assurzen.entities.FundOutflowStatus;
import ci.assurzen.entities.FundOutflowType;
import ci.assurzen.entities.Loan;
import ci.assurzen.entities.RepaymentFrequency;
import ci.assurzen.repositories.FundOutflowDisbursementRepository;
import ci.assurzen.repositories.FundOutflowRepository;
import ci.assurzen.repositories.LoanRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Couvre le moteur generique des sorties de fonds : garde-fous de transition (Regles 1-9),
 * idempotence du decaissement, exigence des conditions de pret quand outflowType=LOAN, declenchement
 * automatique de la creation du pret au decaissement. */
class FundOutflowServiceTest {

    private record Fixture(
            FundOutflowService service,
            FundOutflowRepository fundOutflowRepository,
            FundOutflowDisbursementRepository disbursementRepository,
            LoanRepository loanRepository,
            FundOutflowTypeService fundOutflowTypeService,
            PartenaireRepository partenaireRepository,
            DisbursementService disbursementService,
            LoanService loanService,
            SmsNotificationService smsNotificationService,
            AuthService authService,
            FunctionalAuditRecorder functionalAuditRecorder
    ) {
    }

    private Fixture newFixture() {
        FundOutflowRepository fundOutflowRepository = mock(FundOutflowRepository.class);
        FundOutflowDisbursementRepository disbursementRepository = mock(FundOutflowDisbursementRepository.class);
        LoanRepository loanRepository = mock(LoanRepository.class);
        FundOutflowTypeService fundOutflowTypeService = mock(FundOutflowTypeService.class);
        PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
        DisbursementService disbursementService = mock(DisbursementService.class);
        LoanService loanService = mock(LoanService.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        when(codeGeneratorService.generate(any())).thenReturn("DEC2026000001");
        when(loanRepository.findByFundOutflowId(any())).thenReturn(Optional.empty());
        when(disbursementRepository.lockToutesPourSortie(any())).thenReturn(List.of());
        when(disbursementRepository.findFirstByFundOutflowIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

        AtomicLong idSeq = new AtomicLong(1);
        when(fundOutflowRepository.save(any())).thenAnswer(inv -> {
            FundOutflow o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(idSeq.getAndIncrement());
            }
            return o;
        });
        when(disbursementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FundOutflowService service = new FundOutflowService(fundOutflowRepository, disbursementRepository, loanRepository,
                fundOutflowTypeService, partenaireRepository, disbursementService, loanService, smsNotificationService,
                codeGeneratorService, authService, functionalAuditRecorder);

        return new Fixture(service, fundOutflowRepository, disbursementRepository, loanRepository, fundOutflowTypeService,
                partenaireRepository, disbursementService, loanService, smsNotificationService, authService, functionalAuditRecorder);
    }

    private FundOutflowType type(long id, String code) {
        return FundOutflowType.builder().id(id).code(code).nom(code).build();
    }

    private FundOutflow outflow(long id, FundOutflowStatus status) {
        return FundOutflow.builder().id(id).uuid("uuid-" + id).reference("DEC2026000001")
                .outflowType(type(1L, "EXPENSE")).amount(100000L).currency("XOF").status(status).build();
    }

    private CreateFundOutflowRequest createRequest() {
        return new CreateFundOutflowRequest(1L, null, null, null, 100000L, null, "desc", "raison",
                null, null, null, null, null, null, null);
    }

    // ---------------------------------------------------------------- Creation

    @Test
    void create_typeExpense_neRequiertPasDeConditionsDePret() {
        Fixture fx = newFixture();
        when(fx.fundOutflowTypeService().getEntity(1L)).thenReturn(type(1L, "EXPENSE"));
        when(fx.fundOutflowTypeService().isLoanType(any())).thenReturn(false);

        FundOutflowResponse response = fx.service().create(createRequest());

        assertEquals(FundOutflowStatus.DRAFT, response.status());
        assertEquals("XOF", response.currency());
    }

    @Test
    void create_typeLoanSansBeneficiairePartenaire_estRejeteAvec400_Regle4() {
        Fixture fx = newFixture();
        when(fx.fundOutflowTypeService().getEntity(1L)).thenReturn(type(1L, "LOAN"));
        when(fx.fundOutflowTypeService().isLoanType(any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().create(createRequest()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(fx.smsNotificationService(), never()).sendLoanCreatedSms(any(), any(), any(), any(), any());
    }

    @Test
    void create_typeLoanBeneficiairePartenaireInexistant_estRejeteAvec404() {
        Fixture fx = newFixture();
        when(fx.fundOutflowTypeService().getEntity(1L)).thenReturn(type(1L, "LOAN"));
        when(fx.fundOutflowTypeService().isLoanType(any())).thenReturn(true);
        when(fx.partenaireRepository().findById(33L)).thenReturn(Optional.empty());

        CreateFundOutflowRequest request = new CreateFundOutflowRequest(1L, "PARTNER", 33L, null, 2000000L, "XOF",
                "desc", "avance exceptionnelle", null, null, null, null, null, null, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().create(request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void create_typeLoanSansConditionsDePret_estAccepteEtEnvoieUnSmsAuPartenaire() {
        Fixture fx = newFixture();
        when(fx.fundOutflowTypeService().getEntity(1L)).thenReturn(type(1L, "LOAN"));
        when(fx.fundOutflowTypeService().isLoanType(any())).thenReturn(true);
        ci.assurzen.entities.Partenaire partenaire = ci.assurzen.entities.Partenaire.builder()
                .id(33L).nom("Kouassi").prenoms("Jean").telephone("0700000000").build();
        when(fx.partenaireRepository().findById(33L)).thenReturn(Optional.of(partenaire));

        // Aucune condition de pret (loanStartDate/loanExpectedEndDate/loanRepaymentFrequency/
        // loanNumberOfInstallments toutes null) - optionnelles a la creation.
        CreateFundOutflowRequest request = new CreateFundOutflowRequest(1L, "PARTNER", 33L, null, 2000000L, "XOF",
                "desc", "avance exceptionnelle", null, null, null, null, null, null, null);

        FundOutflowResponse response = fx.service().create(request);

        assertEquals(FundOutflowStatus.DRAFT, response.status());
        verify(fx.smsNotificationService()).sendLoanCreatedSms("0700000000", "Jean Kouassi", response.reference(), 2000000L, "XOF");
    }

    @Test
    void create_typeLoanAvecConditionsCompletes_estAccepte() {
        Fixture fx = newFixture();
        when(fx.fundOutflowTypeService().getEntity(1L)).thenReturn(type(1L, "LOAN"));
        when(fx.fundOutflowTypeService().isLoanType(any())).thenReturn(true);
        when(fx.partenaireRepository().findById(33L)).thenReturn(Optional.of(ci.assurzen.entities.Partenaire.builder().id(33L).build()));

        CreateFundOutflowRequest request = new CreateFundOutflowRequest(1L, "PARTNER", 33L, null, 2000000L, "XOF",
                "desc", "avance exceptionnelle", null, null, BigDecimal.ZERO,
                LocalDate.now(), LocalDate.now().plusMonths(5), RepaymentFrequency.MONTHLY, 5);

        FundOutflowResponse response = fx.service().create(request);
        assertEquals(FundOutflowStatus.DRAFT, response.status());
        // Regression : FundOutflowResponse doit exposer les conditions du pret saisies, pas
        // seulement les accepter en ecriture - signale par l'equipe frontend (formulaire "Modifier"
        // ne pouvait pas se pre-remplir sans ce retour).
        assertEquals(BigDecimal.ZERO, response.loanInterestRate());
        assertEquals(RepaymentFrequency.MONTHLY, response.loanRepaymentFrequency());
        assertEquals(5, response.loanNumberOfInstallments());
    }

    @Test
    void create_typeLoanSansConditionsDePret_lesExposeCommeNullDansLaReponse() {
        Fixture fx = newFixture();
        when(fx.fundOutflowTypeService().getEntity(1L)).thenReturn(type(1L, "LOAN"));
        when(fx.fundOutflowTypeService().isLoanType(any())).thenReturn(true);
        when(fx.partenaireRepository().findById(33L)).thenReturn(Optional.of(ci.assurzen.entities.Partenaire.builder().id(33L).build()));

        CreateFundOutflowRequest request = new CreateFundOutflowRequest(1L, "PARTNER", 33L, null, 300000L, "XOF",
                "desc", "avance exceptionnelle", null, null, null, null, null, null, null);

        FundOutflowResponse response = fx.service().create(request);

        assertEquals(null, response.loanRepaymentFrequency());
        assertEquals(null, response.loanNumberOfInstallments());
    }

    @Test
    void update_completeLesConditionsDePret_lesExposeEnsuiteDansLaReponse() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.DRAFT);
        outflow.setOutflowType(type(1L, "LOAN"));
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));
        when(fx.fundOutflowTypeService().getEntity(1L)).thenReturn(type(1L, "LOAN"));
        when(fx.fundOutflowTypeService().isLoanType(any())).thenReturn(true);
        when(fx.partenaireRepository().findById(33L)).thenReturn(Optional.of(ci.assurzen.entities.Partenaire.builder().id(33L).build()));

        ci.assurzen.dto.UpdateFundOutflowRequest request = new ci.assurzen.dto.UpdateFundOutflowRequest(
                1L, "PARTNER", 33L, null, 300000L, "XOF", null, "r", null, null,
                BigDecimal.ZERO, LocalDate.of(2026, 9, 3), LocalDate.of(2026, 12, 3), RepaymentFrequency.MONTHLY, 3);

        FundOutflowResponse response = fx.service().update(1L, request);

        assertEquals(RepaymentFrequency.MONTHLY, response.loanRepaymentFrequency());
        assertEquals(3, response.loanNumberOfInstallments());
        assertEquals(LocalDate.of(2026, 9, 3), response.loanStartDate());
    }

    // ---------------------------------------------------------------- Transitions

    @Test
    void submit_depuisDraft_passeASubmitted() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.DRAFT);
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));

        FundOutflowResponse response = fx.service().submit(1L);

        assertEquals(FundOutflowStatus.SUBMITTED, response.status());
        verify(fx.functionalAuditRecorder()).recordAction(any(), any(), any(), any(), any());
    }

    @Test
    void submit_depuisAutreStatut_estRejeteAvec409() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.SUBMITTED);
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().submit(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void disburse_depuisDraft_estRejeteAvec409_Regle1() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.DRAFT);
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().disburse(1L, new DisburseFundOutflowRequest(DisbursementMethod.BANK_TRANSFER, null), null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.disbursementService(), never()).process(any());
    }

    @Test
    void reject_sansMotif_estRejeteAvec400() {
        Fixture fx = newFixture();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().reject(1L, ""));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(fx.fundOutflowRepository(), never()).findById(any());
    }

    @Test
    void cancel_depuisApproved_estRejeteAvec409() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.APPROVED);
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().cancel(1L, "motif"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void close_depuisApproved_estRejeteAvec409() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.APPROVED);
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().close(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void update_horsDraft_estRejeteAvec409() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.SUBMITTED);
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));

        ci.assurzen.dto.UpdateFundOutflowRequest request = new ci.assurzen.dto.UpdateFundOutflowRequest(
                1L, null, null, null, 5000L, null, null, "r", null, null, null, null, null, null, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().update(1L, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void update_completeLesConditionsDePretLaisseesOptionnellesALaCreation() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.DRAFT);
        outflow.setOutflowType(type(1L, "LOAN"));
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));
        when(fx.fundOutflowTypeService().getEntity(1L)).thenReturn(type(1L, "LOAN"));
        when(fx.fundOutflowTypeService().isLoanType(any())).thenReturn(true);
        when(fx.partenaireRepository().findById(33L)).thenReturn(Optional.of(ci.assurzen.entities.Partenaire.builder().id(33L).build()));

        ci.assurzen.dto.UpdateFundOutflowRequest request = new ci.assurzen.dto.UpdateFundOutflowRequest(
                1L, "PARTNER", 33L, null, 300000L, "XOF", null, "r", null, null,
                BigDecimal.ZERO, LocalDate.of(2026, 9, 3), LocalDate.of(2026, 12, 3), RepaymentFrequency.MONTHLY, 3);
        fx.service().update(1L, request);

        assertEquals(RepaymentFrequency.MONTHLY, outflow.getLoanRepaymentFrequency());
        assertEquals(3, outflow.getLoanNumberOfInstallments());
        assertEquals(LocalDate.of(2026, 9, 3), outflow.getLoanStartDate());
    }

    @Test
    void delete_depuisDisbursed_estRejeteAvec409_Regle7() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.DISBURSED);
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().delete(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    // ---------------------------------------------------------------- Decaissement / idempotence

    @Test
    void disburse_depuisAuthorized_reussitEtDeclencheLeStatutDisbursed() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.AUTHORIZED);
        outflow.setOutflowType(type(1L, "EXPENSE"));
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));
        when(fx.fundOutflowTypeService().isLoanType(any())).thenReturn(false);
        when(fx.disbursementService().process(any())).thenAnswer(inv -> {
            FundOutflowDisbursement d = inv.getArgument(0);
            d.setStatus(DisbursementStatus.SUCCESS);
            return d;
        });

        FundOutflowDisbursementResponse response = fx.service().disburse(1L,
                new DisburseFundOutflowRequest(DisbursementMethod.BANK_TRANSFER, "REF-1"), "idem-key-1");

        assertEquals(DisbursementStatus.SUCCESS, response.status());
        assertEquals(FundOutflowStatus.DISBURSED, outflow.getStatus());
        verify(fx.loanService(), never()).createFromDisbursedOutflow(any());
        // Regression : signale par l'equipe frontend (integration) le 2026-09-03 - l'action DISBURSE
        // etait auditee sous entityName="FundOutflowDisbursement", jamais retrouvee par
        // GET /fund-outflows/{id}/history qui filtre sur entityName="FundOutflow".
        verify(fx.functionalAuditRecorder()).recordAction(eq("SORTIES_FONDS"), eq("FundOutflow"), eq(outflow.getReference()), eq(ActionAudit.DISBURSE), any());
    }

    @Test
    void disburse_memeCleIdempotenceMemeMethode_renvoieLeMemeDecaissementSansRejouer() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.AUTHORIZED);
        FundOutflowDisbursement existant = FundOutflowDisbursement.builder().id(9L).fundOutflow(outflow)
                .reference("FOD2026000001").amount(100000L).currency("XOF")
                .method(DisbursementMethod.BANK_TRANSFER).status(DisbursementStatus.SUCCESS)
                .idempotencyKey("idem-key-1").createdAt(Instant.now()).build();
        when(fx.disbursementRepository().findByFundOutflowIdAndIdempotencyKey(1L, "idem-key-1")).thenReturn(Optional.of(existant));

        FundOutflowDisbursementResponse response = fx.service().disburse(1L,
                new DisburseFundOutflowRequest(DisbursementMethod.BANK_TRANSFER, "REF-1"), "idem-key-1");

        assertEquals(9L, response.id());
        verify(fx.fundOutflowRepository(), never()).findById(any());
        verify(fx.disbursementService(), never()).process(any());
    }

    @Test
    void disburse_memeCleIdempotenceMethodeDifferente_estRejeteAvec409() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.AUTHORIZED);
        FundOutflowDisbursement existant = FundOutflowDisbursement.builder().id(9L).fundOutflow(outflow)
                .reference("FOD2026000001").amount(100000L).currency("XOF")
                .method(DisbursementMethod.BANK_TRANSFER).status(DisbursementStatus.SUCCESS)
                .idempotencyKey("idem-key-1").createdAt(Instant.now()).build();
        when(fx.disbursementRepository().findByFundOutflowIdAndIdempotencyKey(1L, "idem-key-1")).thenReturn(Optional.of(existant));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().disburse(1L,
                new DisburseFundOutflowRequest(DisbursementMethod.WAVE, "REF-1"), "idem-key-1"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void disburse_typeLoan_declencheLaCreationDuPret() {
        Fixture fx = newFixture();
        FundOutflow outflow = outflow(1L, FundOutflowStatus.AUTHORIZED);
        outflow.setOutflowType(type(1L, "LOAN"));
        when(fx.fundOutflowRepository().findById(1L)).thenReturn(Optional.of(outflow));
        when(fx.fundOutflowTypeService().isLoanType(any())).thenReturn(true);
        when(fx.disbursementService().process(any())).thenAnswer(inv -> {
            FundOutflowDisbursement d = inv.getArgument(0);
            d.setStatus(DisbursementStatus.SUCCESS);
            return d;
        });
        when(fx.loanService().createFromDisbursedOutflow(any())).thenReturn(mock(Loan.class));

        fx.service().disburse(1L, new DisburseFundOutflowRequest(DisbursementMethod.WAVE, null), null);

        verify(fx.loanService()).createFromDisbursedOutflow(outflow);
    }
}
