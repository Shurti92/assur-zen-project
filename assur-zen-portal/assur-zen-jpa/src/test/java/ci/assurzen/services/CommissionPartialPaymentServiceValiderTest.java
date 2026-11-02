package ci.assurzen.services;

import ci.assurzen.dto.PartialPaymentRequestResponse;
import ci.assurzen.entities.CommissionPartialPaymentRequest;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayDuplicatePaymentException;
import ci.assurzen.sycapay.exception.SycaPayException;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Validation d'une demande de paiement avec virement SycaPay, en mode hybride (2026-09-26). */
class CommissionPartialPaymentServiceValiderTest {

    private final CommissionPartialPaymentRequestRepository repository = mock(CommissionPartialPaymentRequestRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final SycaPayProperties sycaPayProperties = mock(SycaPayProperties.class);
    private final SycaPayPaymentService sycaPayPaymentService = mock(SycaPayPaymentService.class);
    private final SycaPayTransactionRepository transactions = mock(SycaPayTransactionRepository.class);
    private final SmsNotificationService sms = mock(SmsNotificationService.class);
    private final LedgerService ledger = mock(LedgerService.class);

    private final CommissionPartialPaymentService service = new CommissionPartialPaymentService(repository,
            mock(PartenaireRepository.class), mock(ProfileRepository.class), userRepository, mock(CommissionService.class),
            mock(DynamicConfigurationService.class), authService, mock(TelegramNotificationService.class), sycaPayProperties,
            sycaPayPaymentService, transactions, sms, mock(PlatformTransactionManager.class), ledger, mock(org.springframework.context.ApplicationEventPublisher.class));

    private CommissionPartialPaymentRequest demande;

    @BeforeEach
    void setUp() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        User admin = new User();
        admin.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        demande = CommissionPartialPaymentRequest.builder().id(12L)
                .partenaire(Partenaire.builder().id(9L).nom("TANOH").prenoms("Stephane").build())
                .requestedAmount(12000L).status(PartialPaymentRequestStatus.pending).numeroWaveCommissionSnapshot("0707070707").build();
        when(repository.findByIdForUpdate(12L)).thenReturn(Optional.of(demande));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sycaPayProperties.isEnabled()).thenReturn(true);
    }

    private void virement(SycaPayTransactionStatus statut) {
        when(sycaPayPaymentService.payCommission(any())).thenReturn(SycaPayPaymentOutcome.of(statut, 70L, "SYC2026000070", "msg"));
        SycaPayTransaction transaction = new SycaPayTransaction();
        transaction.setId(70L);
        transaction.setTransactionReference("SYC2026000070");
        transaction.setStatus(statut);
        transaction.setPartialPaymentRequestId(12L);
        when(transactions.findByPartialPaymentRequestId(12L)).thenReturn(Optional.of(transaction));
    }

    @Test
    void sycapayDesactive_payeeCommeAvant_sansVirement() {
        when(sycaPayProperties.isEnabled()).thenReturn(false);

        PartialPaymentRequestResponse reponse = service.valider(12L);

        assertEquals(PartialPaymentRequestStatus.paid, reponse.status());
        assertEquals("ENREGISTREMENT_MANUEL", reponse.modePaiement());
        assertNotNull(demande.getValidatedAt());
        verify(ledger).enregistrerAvanceCommission("Stephane TANOH", 12000L, 12L, false);
        verify(sycaPayPaymentService, never()).payCommission(any());
    }

    @Test
    void virementReussi_payeeEtSms_montantEtNumeroRelusEnBase() {
        virement(SycaPayTransactionStatus.SUCCESS);

        PartialPaymentRequestResponse reponse = service.valider(12L);

        ArgumentCaptor<SycaPayPaymentCommand> commande = ArgumentCaptor.forClass(SycaPayPaymentCommand.class);
        verify(sycaPayPaymentService).payCommission(commande.capture());
        assertEquals(12000L, commande.getValue().amount());
        assertEquals("0707070707", commande.getValue().beneficiaryPhone());
        assertEquals(12L, commande.getValue().partialPaymentRequestId());
        assertNull(commande.getValue().contractId());
        assertEquals(SycaPayBeneficiaryType.PARTENAIRE, commande.getValue().beneficiaryType());
        assertEquals(PartialPaymentRequestStatus.paid, reponse.status());
        assertEquals("SUCCESS", reponse.sycapayStatus());
        assertEquals("VIREMENT_SYCAPAY", reponse.modePaiement());
        verify(sms).sendDemandePaiementVerseeSms("0707070707", 12000L, "SYC2026000070", false);
        verify(ledger).enregistrerAvanceCommission("Stephane TANOH", 12000L, 12L, false);
        assertEquals(Boolean.TRUE, demande.getComptabilisee());
    }

    @Test
    void virementEchoue_demandeEnEchec_relancable() {
        virement(SycaPayTransactionStatus.FAILED);

        assertEquals(PartialPaymentRequestStatus.failed, service.valider(12L).status());
        assertNull(demande.getValidatedAt());
        verify(ledger, never()).enregistrerAvanceCommission(any(), any(), any(), anyBoolean()); // rien n'est parti

        // Relance : la demande en echec repasse par un virement.
        virement(SycaPayTransactionStatus.SUCCESS);
        assertEquals(PartialPaymentRequestStatus.paid, service.valider(12L).status());
    }

    @Test
    void resultatIncertain_resteEnCours_etNouvelleValidationRefusee() {
        virement(SycaPayTransactionStatus.UNKNOWN);

        assertEquals(PartialPaymentRequestStatus.processing, service.valider(12L).status());
        assertThrows(SycaPayDuplicatePaymentException.class, () -> service.valider(12L));
    }

    @Test
    void reconciliationReussie_payeeSansAdminConnecte() {
        virement(SycaPayTransactionStatus.UNKNOWN);
        service.valider(12L);
        virement(SycaPayTransactionStatus.SUCCESS);
        SycaPayTransaction confirmee = new SycaPayTransaction();
        confirmee.setId(70L);
        confirmee.setTransactionReference("SYC2026000070");
        confirmee.setStatus(SycaPayTransactionStatus.SUCCESS);
        confirmee.setPartialPaymentRequestId(12L);
        when(transactions.findById(70L)).thenReturn(Optional.of(confirmee));

        service.confirmerVirement(70L, "admin");

        assertEquals(PartialPaymentRequestStatus.paid, demande.getStatus());
        verify(sms).sendDemandePaiementVerseeSms(anyString(), anyLong(), anyString(), anyBoolean());
    }

    @Test
    void serviceIndisponible_rejete_laDemandeRetrouveSonStatut() {
        when(sycaPayPaymentService.payCommission(any())).thenThrow(new SycaPayException("identifiants incomplets"));

        PartialPaymentRequestResponse reponse = service.valider(12L);

        assertEquals("REJECTED", reponse.sycapayStatus());
        assertEquals(PartialPaymentRequestStatus.pending, reponse.status());
        assertEquals("Service de paiement SycaPay indisponible - aucun virement n'est parti", reponse.sycapayMessage());
    }

    @Test
    void demandeDejaPayee_409() {
        demande.setStatus(PartialPaymentRequestStatus.paid);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.valider(12L));

        assertEquals(409, ex.getStatusCode().value());
        verify(sycaPayPaymentService, never()).payCommission(any());
    }

    @Test
    void demandeDeCommercial_virementVersLeCommercial() {
        demande.setPartenaire(null);
        demande.setCommercial(ci.assurzen.entities.Commercial.builder().id(4L)
                .profile(ci.assurzen.entities.Profile.builder().firstname("Jean").lastname("Koffi").build()).build());
        virement(SycaPayTransactionStatus.SUCCESS);

        service.valider(12L);

        ArgumentCaptor<SycaPayPaymentCommand> commande = ArgumentCaptor.forClass(SycaPayPaymentCommand.class);
        verify(sycaPayPaymentService).payCommission(commande.capture());
        assertEquals(SycaPayBeneficiaryType.COMMERCIAL, commande.getValue().beneficiaryType());
        assertEquals(4L, commande.getValue().commercialId());
        verify(sms).sendDemandePaiementVerseeSms(eq("0707070707"), eq(12000L), eq("SYC2026000070"), eq(true));
    }

    @Test
    void listeDeValidation_exposeLeVirementDeChaqueDemande() {
        demande.setStatus(PartialPaymentRequestStatus.processing);
        SycaPayTransaction transaction = new SycaPayTransaction();
        transaction.setId(70L);
        transaction.setPartialPaymentRequestId(12L);
        transaction.setStatus(SycaPayTransactionStatus.UNKNOWN);
        transaction.setSycapayResponseMessage("Timeout");
        when(repository.findForValidation(null)).thenReturn(java.util.List.of(demande));
        when(transactions.findByPartialPaymentRequestIdIn(any())).thenReturn(java.util.List.of(transaction));

        ci.assurzen.dto.PaymentRequestValidationItemResponse ligne = service.listerPourValidation(null).get(0);

        assertEquals("UNKNOWN", ligne.sycapayStatus());
        assertEquals(70L, ligne.sycapayTransactionPk());
        assertEquals("Timeout", ligne.sycapayMessage());
        assertEquals("VIREMENT_SYCAPAY", ligne.modePaiement());
    }
}
