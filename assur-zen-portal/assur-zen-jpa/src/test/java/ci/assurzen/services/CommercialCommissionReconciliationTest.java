package ci.assurzen.services;

import ci.assurzen.entities.Commercial;
import ci.assurzen.entities.CommercialCommissionPayment;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Profile;
import ci.assurzen.entities.User;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.repositories.CommercialCommissionPaymentRepository;
import ci.assurzen.repositories.CommercialRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import org.junit.jupiter.api.BeforeEach;
import ci.assurzen.sycapay.config.SycaPayProperties;
import org.springframework.transaction.PlatformTransactionManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Commission COMMERCIALE dont le virement SycaPay (reste PROCESSING/UNKNOWN) est confirme par une
 * reconciliation (2026-09-23) : le paiement doit alors etre enregistre, comme pour les partenaires.
 */
class CommercialCommissionReconciliationTest {

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final CommercialRepository commercialRepository = mock(CommercialRepository.class);
    private final CommercialCommissionPaymentRepository paymentRepository = mock(CommercialCommissionPaymentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LedgerService ledgerService = mock(LedgerService.class);
    private final SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
    private final TelegramNotificationService telegramNotificationService = mock(TelegramNotificationService.class);
    private final SycaPayTransactionRepository sycaPayTransactionRepository = mock(SycaPayTransactionRepository.class);
    private final CommercialCommissionService service = new CommercialCommissionService(contractRepository, commercialRepository,
            paymentRepository, userRepository, mock(AuthService.class), ledgerService, smsNotificationService,
            telegramNotificationService, mock(SycaPayPaymentService.class), sycaPayTransactionRepository,
            mock(CommissionPartialPaymentRequestRepository.class), mock(SycaPayProperties.class),
            mock(PlatformTransactionManager.class), mock(org.springframework.context.ApplicationEventPublisher.class));

    private Contract contract;
    private SycaPayTransaction transaction;

    @BeforeEach
    void setUp() {
        contract = Contract.builder().id(10L).policyNumber("CTR-10").status(ContractStatus.signed)
                .commercialCommissionAmount(25000L).commercialCommissionStatus(CommissionStatus.pending).createdAt(Instant.now()).build();
        when(contractRepository.findById(10L)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Profile profile = Profile.builder().id(200L).firstname("Jean").lastname("Koffi").phone("0700000000").build();
        when(commercialRepository.findById(1L)).thenReturn(Optional.of(Commercial.builder().id(1L).profile(profile).build()));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        transaction = new SycaPayTransaction();
        transaction.setId(77L);
        transaction.setTransactionReference("SYC2026000077");
        transaction.setContractId(10L);
        transaction.setCommercialId(1L);
        transaction.setBeneficiaryType(SycaPayBeneficiaryType.COMMERCIAL);
        transaction.setAmount(25000L);
        transaction.setStatus(SycaPayTransactionStatus.SUCCESS);
        transaction.setCreatedBy("admin.initiateur");
        when(sycaPayTransactionRepository.findById(77L)).thenReturn(Optional.of(transaction));
    }

    private static User utilisateur(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    @Test
    void virementConfirme_enregistreLePaiement_auNomDeLAdminInitiateur_etNotifieLeCommercial() {
        User initiateur = utilisateur(5L, "admin.initiateur");
        when(userRepository.findByUsername("admin.initiateur")).thenReturn(Optional.of(initiateur));

        service.enregistrerVirementConfirme(77L, "admin.reconciliateur");

        ArgumentCaptor<CommercialCommissionPayment> payment = ArgumentCaptor.forClass(CommercialCommissionPayment.class);
        verify(paymentRepository).save(payment.capture());
        assertEquals(initiateur, payment.getValue().getPaidBy());
        assertEquals(77L, payment.getValue().getSycapayTransactionId());
        assertEquals("SYC2026000077", payment.getValue().getReference());
        assertEquals(CommissionStatus.paid, contract.getCommercialCommissionStatus());
        verify(ledgerService).enregistrerCommissionCommerciale(contract, 25000L);
        verify(smsNotificationService).sendFromTemplate(eq("0700000000"), any(), any(), any());
    }

    /** Transaction anterieure au remplissage de created_by : l'admin qui reconcilie est retenu. */
    @Test
    void sansInitiateurConnu_retientLAdminQuiReconcilie() {
        transaction.setCreatedBy(null);
        User reconciliateur = utilisateur(6L, "admin.reconciliateur");
        when(userRepository.findByUsername("admin.reconciliateur")).thenReturn(Optional.of(reconciliateur));

        service.enregistrerVirementConfirme(77L, "admin.reconciliateur");

        ArgumentCaptor<CommercialCommissionPayment> payment = ArgumentCaptor.forClass(CommercialCommissionPayment.class);
        verify(paymentRepository).save(payment.capture());
        assertEquals(reconciliateur, payment.getValue().getPaidBy());
    }

    @Test
    void ignoreLesTransactionsPartenaire_nonReussies_ouDejaEnregistrees() {
        transaction.setBeneficiaryType(SycaPayBeneficiaryType.PARTENAIRE);
        service.enregistrerVirementConfirme(77L, "admin");

        transaction.setBeneficiaryType(SycaPayBeneficiaryType.COMMERCIAL);
        transaction.setStatus(SycaPayTransactionStatus.UNKNOWN);
        service.enregistrerVirementConfirme(77L, "admin");

        transaction.setStatus(SycaPayTransactionStatus.SUCCESS);
        when(paymentRepository.existsBySycapayTransactionId(77L)).thenReturn(true);
        service.enregistrerVirementConfirme(77L, "admin");

        verify(paymentRepository, never()).save(any());
        verify(ledgerService, never()).enregistrerCommissionCommerciale(any(), anyLong());
    }

    @Test
    void commissionDejaPayee_alerteDoublePaiement_sansRienEnregistrer() {
        contract.setCommercialCommissionStatus(CommissionStatus.paid);

        service.enregistrerVirementConfirme(77L, "admin");

        verify(telegramNotificationService).envoyer(contains("double paiement"));
        verify(paymentRepository, never()).save(any());
    }

    /** Le resultat du SMS au commercial est conserve sur le paiement (historique). */
    @Test
    void virementConfirme_resultatDuSms_conserveSurLePaiement() {
        when(userRepository.findByUsername("admin.initiateur")).thenReturn(Optional.of(utilisateur(5L, "admin.initiateur")));
        when(smsNotificationService.sendFromTemplate(any(), any(), any(), any())).thenReturn(true);

        service.enregistrerVirementConfirme(77L, "admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<CommercialCommissionPayment>> saved = ArgumentCaptor.forClass(java.util.List.class);
        verify(paymentRepository).saveAll(saved.capture());
        assertEquals(Boolean.TRUE, saved.getValue().get(0).getNotificationEnvoyee());
    }
}
