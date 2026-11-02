package ci.assurzen.services;

import ci.assurzen.dto.CommercialCommissionPaymentRequest;
import ci.assurzen.dto.CommercialCommissionPaymentResponse;
import ci.assurzen.dto.CommercialCommissionSummaryResponse;
import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialCommissionPayment;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.repositories.CommercialCommissionPaymentRepository;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.exception.SycaPayException;
import org.springframework.transaction.PlatformTransactionManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
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

/**
 * Couvre les regles d'eligibilite au paiement de la commission commerciale (§ "Commission commerciale
 * interne", 2026-09-08) : exclusivite mutuelle avec le partenaire, contrat signe, non deja payee,
 * montant positif, fiche commerciale existante pour le vendeur - et l'agregation par commercial.
 */
class CommercialCommissionServiceTest {

    private record Fixture(
            CommercialCommissionService service,
            ContractRepository contractRepository,
            CommercialRepository commercialRepository,
            CommercialCommissionPaymentRepository paymentRepository,
            UserRepository userRepository,
            AuthService authService,
            LedgerService ledgerService,
            SmsNotificationService smsNotificationService,
            TelegramNotificationService telegramNotificationService,
            SycaPayPaymentService sycaPayPaymentService,
            CommissionPartialPaymentRequestRepository paymentRequestRepository,
            SycaPayProperties sycaPayProperties
    ) {
    }

    private Fixture newFixture() {
        ContractRepository contractRepository = mock(ContractRepository.class);
        CommercialRepository commercialRepository = mock(CommercialRepository.class);
        CommercialCommissionPaymentRepository paymentRepository = mock(CommercialCommissionPaymentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuthService authService = mock(AuthService.class);
        LedgerService ledgerService = mock(LedgerService.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        TelegramNotificationService telegramNotificationService = mock(TelegramNotificationService.class);
        SycaPayPaymentService sycaPayPaymentService = mock(SycaPayPaymentService.class);

        // Par defaut, chaque tentative de paiement SycaPay est un succes immediat (comportement le
        // plus proche de l'ancien flux 100% declaratif) - les tests qui veulent un echec/incertitude
        // SycaPay redefinissent explicitement le stub de payCommission(any()).
        when(sycaPayPaymentService.payCommission(any())).thenAnswer(inv ->
                SycaPayPaymentOutcome.of(SycaPayTransactionStatus.SUCCESS, 999L, "SYC-TEST", "SUCCESS"));

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        AtomicLong idSeq = new AtomicLong(1);
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            CommercialCommissionPayment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(idSeq.getAndIncrement());
            }
            return p;
        });
        when(contractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommissionPartialPaymentRequestRepository paymentRequestRepository = mock(CommissionPartialPaymentRequestRepository.class);
        // SycaPay active par defaut : les tests de virement ci-dessous ; le mode hybride (desactive) a ses tests.
        SycaPayProperties sycaPayProperties = mock(SycaPayProperties.class);
        when(sycaPayProperties.isEnabled()).thenReturn(true);
        CommercialCommissionService service = new CommercialCommissionService(contractRepository, commercialRepository,
                paymentRepository, userRepository, authService, ledgerService, smsNotificationService, telegramNotificationService,
                sycaPayPaymentService, mock(SycaPayTransactionRepository.class), paymentRequestRepository, sycaPayProperties,
                mock(PlatformTransactionManager.class), mock(org.springframework.context.ApplicationEventPublisher.class));

        return new Fixture(service, contractRepository, commercialRepository, paymentRepository, userRepository,
                authService, ledgerService, smsNotificationService, telegramNotificationService, sycaPayPaymentService,
                paymentRequestRepository, sycaPayProperties);
    }

    private Commercial commercial(long id, long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("commercial" + userId);
        Profile profile = Profile.builder().id(200 + id).firstname("Jean").lastname("Koffi")
                .phone("0700000000").user(user).build();
        return Commercial.builder().id(id).employeeCode("COM-00000" + id).profile(profile).build();
    }

    private Contract contrat(long id, User salesRep, Partenaire partenaire, ContractStatus status, long commissionAmount, CommissionStatus commissionStatus) {
        return Contract.builder().id(id).policyNumber("CTR-" + id).salesRep(salesRep).partenaire(partenaire)
                .status(status).commercialCommissionAmount(commissionAmount).commercialCommissionStatus(commissionStatus)
                .createdAt(Instant.now()).build();
    }

    // ---------------------------------------------------------------- pay()

    @Test
    void pay_succes_marqueLeContratPayeEtEnvoieUnSmsAgrege() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 50);
        Contract contrat = contrat(10, c.getProfile().getUser(), null, ContractStatus.signed, 25000, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));
        when(fx.commercialRepository().findByProfile_UserId(50L)).thenReturn(Optional.of(c));
        when(fx.smsNotificationService().sendFromTemplate(eq("0700000000"), eq("COMMISSION_COMMERCIALE_PAYEE"), any(), any()))
                .thenReturn(true);

        List<CommercialCommissionPaymentResponse> responses = fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), "REF-1"));

        assertEquals(1, responses.size());
        assertEquals(0, Long.compare(25000L, responses.get(0).amount()));
        assertEquals(Boolean.TRUE, responses.get(0).notificationEnvoyee());
        assertEquals("SUCCESS", responses.get(0).sycapayStatus());
        assertEquals("SYC-TEST", responses.get(0).sycapayTransactionReference());
        assertEquals(999L, responses.get(0).sycapayTransactionPk());
        verify(fx.paymentRepository()).save(org.mockito.ArgumentMatchers.argThat(p -> Long.valueOf(999L).equals(p.getSycapayTransactionId())));
        assertEquals(CommissionStatus.paid, contrat.getCommercialCommissionStatus());
        verify(fx.ledgerService()).enregistrerCommissionCommerciale(contrat, 25000L);
        verify(fx.telegramNotificationService()).envoyer(any());
    }

    // ---------------------------------------------------------------- mode hybride (2026-09-26)

    @Test
    void pay_sycapayDesactive_enregistrementHorsApplicationSansVirement() {
        Fixture fx = newFixture();
        when(fx.sycaPayProperties().isEnabled()).thenReturn(false);
        Commercial c = commercial(1, 50);
        Contract a = contrat(10, c.getProfile().getUser(), null, ContractStatus.signed, 25000, CommissionStatus.pending);
        Contract b = contrat(11, c.getProfile().getUser(), null, ContractStatus.signed, 5000, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(a));
        when(fx.contractRepository().findById(11L)).thenReturn(Optional.of(b));
        when(fx.commercialRepository().findByProfile_UserId(50L)).thenReturn(Optional.of(c));
        when(fx.smsNotificationService().sendFromTemplate(eq("0700000000"), eq("COMMISSION_COMMERCIALE_PAYEE"), any(), any()))
                .thenReturn(true);

        List<CommercialCommissionPaymentResponse> responses = fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L, 11L), "REF-1"));

        verify(fx.sycaPayPaymentService(), never()).payCommission(any());
        assertEquals(CommissionStatus.paid, a.getCommercialCommissionStatus());
        assertEquals(CommissionStatus.paid, b.getCommercialCommissionStatus());
        assertEquals(2, responses.size());
        assertEquals("ENREGISTREMENT_MANUEL", responses.get(0).modePaiement());
        assertEquals(null, responses.get(0).sycapayStatus());
        assertEquals(Boolean.TRUE, responses.get(0).notificationEnvoyee());
        verify(fx.ledgerService()).enregistrerCommissionCommerciale(a, 25000L);
    }

    @Test
    void pay_sycapayActifMaisIndisponible_contratRejeteSansFaireEchouerLaRequete() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 50);
        Contract contrat = contrat(10, c.getProfile().getUser(), null, ContractStatus.signed, 25000, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));
        when(fx.commercialRepository().findByProfile_UserId(50L)).thenReturn(Optional.of(c));
        when(fx.sycaPayPaymentService().payCommission(any())).thenThrow(new SycaPayException("identifiants incomplets"));

        CommercialCommissionPaymentResponse reponse = fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), "REF-1")).get(0);

        assertEquals("REJECTED", reponse.sycapayStatus());
        assertEquals("Service de paiement SycaPay indisponible - aucun virement n'est parti", reponse.sycapayMessage());
        assertEquals("VIREMENT_SYCAPAY", reponse.modePaiement());
        assertEquals(CommissionStatus.pending, contrat.getCommercialCommissionStatus());
    }

    @Test
    void pay_virementReussi_modePaiementVirement() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 50);
        Contract contrat = contrat(10, c.getProfile().getUser(), null, ContractStatus.signed, 25000, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));
        when(fx.commercialRepository().findByProfile_UserId(50L)).thenReturn(Optional.of(c));

        assertEquals("VIREMENT_SYCAPAY", fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), "REF-1")).get(0).modePaiement());
    }

    /** Anti double versement (2026-09-25) : un commercial qui a des demandes de paiement (pending/paid)
     * n'est plus payable contrat par contrat - aucun virement SycaPay n'est tente. */
    @Test
    void pay_commercialAvecDemandesDePaiement_estRejeteAvec409SansVirement() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 50);
        Contract contrat = contrat(10, c.getProfile().getUser(), null, ContractStatus.signed, 25000, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));
        when(fx.commercialRepository().findByProfile_UserId(50L)).thenReturn(Optional.of(c));
        when(fx.paymentRequestRepository().existsByCommercialIdAndStatusIn(eq(1L), any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), "REF-1")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.sycaPayPaymentService(), never()).payCommission(any());
        assertEquals(CommissionStatus.pending, contrat.getCommercialCommissionStatus());
    }

    /** Coeur de l'integration SycaPay (2026-09-22) : un virement Wave non confirme ne doit JAMAIS se
     * traduire par un CommercialCommissionPayment ni par un contrat marque "paid" - la commission doit
     * rester payable ulterieurement (retry explicite ou reconciliation), jamais silencieusement perdue
     * ni silencieusement doublee. */
    @Test
    void pay_echecSycaPay_neCreeAucunPaiementEtNeMarquePasLeContratPaye() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 50);
        Contract contrat = contrat(10, c.getProfile().getUser(), null, ContractStatus.signed, 25000, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));
        when(fx.commercialRepository().findByProfile_UserId(50L)).thenReturn(Optional.of(c));
        when(fx.sycaPayPaymentService().payCommission(any())).thenReturn(
                SycaPayPaymentOutcome.of(SycaPayTransactionStatus.FAILED, 999L, "SYC-TEST", "Montant incorrect pour cet operateur"));

        List<CommercialCommissionPaymentResponse> responses = fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), "REF-1"));

        assertEquals(1, responses.size());
        CommercialCommissionPaymentResponse response = responses.get(0);
        assertEquals("FAILED", response.sycapayStatus());
        assertEquals(999L, response.sycapayTransactionPk(), "id technique requis pour /sycapay/transactions/{id}/reconcile");
        assertEquals(null, response.id());
        assertEquals(null, response.paidAt());
        assertEquals(CommissionStatus.pending, contrat.getCommercialCommissionStatus());
        verify(fx.paymentRepository(), never()).save(any());
        verify(fx.ledgerService(), never()).enregistrerCommissionCommerciale(any(), any());
        verify(fx.smsNotificationService(), never()).sendFromTemplate(any(), any(), any(), any());
    }

    /** Garde d'idempotence SycaPay declenchee avant toute creation de transaction : REJECTED, sans
     * reference ni id technique (le frontend ne doit pas proposer de reconciliation dans ce cas). */
    @Test
    void pay_paiementDejaEnCours_renvoieRejectedSansReferenceNiIdTechnique() {
        Fixture fx = newFixture();
        Commercial c = commercial(1, 50);
        Contract contrat = contrat(10, c.getProfile().getUser(), null, ContractStatus.signed, 25000, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));
        when(fx.commercialRepository().findByProfile_UserId(50L)).thenReturn(Optional.of(c));
        when(fx.sycaPayPaymentService().payCommission(any())).thenReturn(
                SycaPayPaymentOutcome.rejected("Transaction SycaPay deja active pour ce contrat"));

        CommercialCommissionPaymentResponse response =
                fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), "REF-1")).get(0);

        assertEquals("REJECTED", response.sycapayStatus());
        assertEquals(null, response.sycapayTransactionReference());
        assertEquals(null, response.sycapayTransactionPk());
        assertEquals(CommissionStatus.pending, contrat.getCommercialCommissionStatus());
        verify(fx.paymentRepository(), never()).save(any());
    }

    @Test
    void pay_contratAvecPartenaire_estRejeteAvec409() {
        Fixture fx = newFixture();
        User salesRep = new User();
        salesRep.setId(50L);
        Partenaire partenaire = Partenaire.builder().id(7L).build();
        Contract contrat = contrat(10, salesRep, partenaire, ContractStatus.signed, 25000, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(fx.contractRepository(), never()).save(any());
        verify(fx.sycaPayPaymentService(), never()).payCommission(any());
    }

    @Test
    void pay_contratNonSigne_estRejeteAvec409() {
        Fixture fx = newFixture();
        User salesRep = new User();
        salesRep.setId(50L);
        Contract contrat = contrat(10, salesRep, null, ContractStatus.pending, 25000, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void pay_commissionDejaPayee_estRejeteAvec409() {
        Fixture fx = newFixture();
        User salesRep = new User();
        salesRep.setId(50L);
        Contract contrat = contrat(10, salesRep, null, ContractStatus.signed, 25000, CommissionStatus.paid);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void pay_aucuneCommissionDue_estRejeteAvec409() {
        Fixture fx = newFixture();
        User salesRep = new User();
        salesRep.setId(50L);
        Contract contrat = contrat(10, salesRep, null, ContractStatus.signed, 0, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void pay_sansFicheCommercialeLieeAuVendeur_estRejeteAvec409() {
        Fixture fx = newFixture();
        User salesRep = new User();
        salesRep.setId(50L);
        Contract contrat = contrat(10, salesRep, null, ContractStatus.signed, 25000, CommissionStatus.pending);
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contrat));
        when(fx.commercialRepository().findByProfile_UserId(50L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void pay_sansDroitsAdministrateur_estRejeteAvec403() {
        Fixture fx = newFixture();
        when(fx.authService().currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "commercial2", "COMMERCIAL"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().pay(new CommercialCommissionPaymentRequest(List.of(10L), null)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fx.contractRepository(), never()).findById(any());
    }

    // ---------------------------------------------------------------- listCommercialSummaries()

    @Test
    void listCommercialSummaries_agregeParCommercialEtIgnoreLesContratsPartenaire() {
        Fixture fx = newFixture();
        Commercial c1 = commercial(1, 50);
        Commercial c2 = commercial(2, 60);
        Contract pending1 = contrat(10, c1.getProfile().getUser(), null, ContractStatus.signed, 25000, CommissionStatus.pending);
        Contract paid1 = contrat(11, c1.getProfile().getUser(), null, ContractStatus.signed, 15000, CommissionStatus.paid);
        Contract pending2 = contrat(12, c2.getProfile().getUser(), null, ContractStatus.signed, 30000, CommissionStatus.pending);
        when(fx.contractRepository().findByStatusAndPartenaireIsNull(ContractStatus.signed))
                .thenReturn(List.of(pending1, paid1, pending2));
        when(fx.commercialRepository().findAll()).thenReturn(List.of(c1, c2));

        List<CommercialCommissionSummaryResponse> summaries = fx.service().listCommercialSummaries();

        assertEquals(2, summaries.size());
        CommercialCommissionSummaryResponse s1 = summaries.stream().filter(s -> s.commercialId().equals(1L)).findFirst().orElseThrow();
        assertEquals(1, s1.pendingContractsCount());
        assertEquals(1, s1.paidContractsCount());
        assertEquals(25000, s1.totalPending());
        assertEquals(15000, s1.totalPaid());
    }

    // ---------------------------------------------------------------- mySummary() (self-service, §2026-09-08)

    @Test
    void mySummary_neRenvoieQueLesContratsDuCommercialConnecte() {
        Fixture fx = newFixture();
        Commercial moi = commercial(1, 50);
        when(fx.authService().currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(50L, "commercial50", "COMMERCIAL"));
        when(fx.commercialRepository().findByProfile_UserId(50L)).thenReturn(Optional.of(moi));
        Contract pending = contrat(10, moi.getProfile().getUser(), null, ContractStatus.signed, 25000, CommissionStatus.pending);
        Contract paid = contrat(11, moi.getProfile().getUser(), null, ContractStatus.signed, 15000, CommissionStatus.paid);
        when(fx.contractRepository().findByStatusAndPartenaireIsNullAndSalesRepId(ContractStatus.signed, 50L))
                .thenReturn(List.of(pending, paid));

        ci.assurzen.dto.CommercialCommissionSelfResponse resume = fx.service().mySummary();

        assertEquals(1, resume.pendingContractsCount());
        assertEquals(1, resume.paidContractsCount());
        assertEquals(25000, resume.totalPending());
        assertEquals(15000, resume.totalPaid());
        assertEquals(2, resume.contracts().size());
    }

    @Test
    void mySummary_sansFicheCommerciale_estRejeteAvec404() {
        Fixture fx = newFixture();
        when(fx.authService().currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(99L, "sansfiche", "COMMERCIAL"));
        when(fx.commercialRepository().findByProfile_UserId(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().mySummary());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
