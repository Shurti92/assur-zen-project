package ci.assurzen.services;

import ci.assurzen.entities.CommissionPayment;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.FrequencePaiementCommission;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.repositories.CommissionPaymentRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayException;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regle metier du 2026-09-23 : paiement des commissions partenaire selon la frequence de paiement.
 * Point critique : un virement Wave REEL part sans action humaine (IMMEDIAT) - chaque garde-fou qui
 * doit l'empecher est teste explicitement.
 */
class PartenaireCommissionAutomationServiceTest {

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
    private final CommissionPaymentRepository commissionPaymentRepository = mock(CommissionPaymentRepository.class);
    private final SycaPayTransactionRepository sycaPayTransactionRepository = mock(SycaPayTransactionRepository.class);
    private final CommissionService commissionService = mock(CommissionService.class);
    private final SycaPayPaymentService sycaPayPaymentService = mock(SycaPayPaymentService.class);
    private final SycaPayProperties sycaPayProperties = mock(SycaPayProperties.class);
    private final PartenaireNotificationService notificationService = mock(PartenaireNotificationService.class);
    private final TelegramNotificationService telegramNotificationService = mock(TelegramNotificationService.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private final PartenaireCommissionAutomationService service = new PartenaireCommissionAutomationService(
            contractRepository, partenaireRepository, commissionPaymentRepository, sycaPayTransactionRepository,
            commissionService, sycaPayPaymentService, sycaPayProperties, notificationService, telegramNotificationService,
            transactionTemplate);

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        when(sycaPayProperties.isEnabled()).thenReturn(true);
    }

    private Partenaire partenaire(FrequencePaiementCommission frequence) {
        return Partenaire.builder().id(7L).nom("Koffi").prenoms("Ama").telephone("0700000007")
                .numeroWaveCommission("0757000007").frequencePaiementCommission(frequence).active(true).build();
    }

    private Contract contratSigne(Partenaire partenaire, CommissionStatus commissionStatus) {
        Contract contract = Contract.builder().id(30L).policyNumber("POL-123").partenaire(partenaire)
                .status(ContractStatus.signed).partnerShare(12000L).commissionStatus(commissionStatus)
                .createdAt(Instant.now()).build();
        when(contractRepository.findById(30L)).thenReturn(Optional.of(contract));
        return contract;
    }

    private SycaPayTransaction transactionPartenaire(SycaPayTransactionStatus status) {
        SycaPayTransaction transaction = new SycaPayTransaction();
        transaction.setId(55L);
        transaction.setTransactionReference("SYC2026000055");
        transaction.setContractId(30L);
        transaction.setPartenaireId(7L);
        transaction.setBeneficiaryType(SycaPayBeneficiaryType.PARTENAIRE);
        transaction.setAmount(12000L);
        transaction.setStatus(status);
        when(sycaPayTransactionRepository.findById(55L)).thenReturn(Optional.of(transaction));
        return transaction;
    }

    // ------------------------------------------------------------------ IMMEDIAT

    @Test
    void immediat_succes_payeSurLeNumeroWave_enregistreLePaiement_etNotifieLePartenaire() {
        contratSigne(partenaire(FrequencePaiementCommission.IMMEDIAT), CommissionStatus.pending);
        when(sycaPayPaymentService.payCommission(any())).thenReturn(
                SycaPayPaymentOutcome.of(SycaPayTransactionStatus.SUCCESS, 55L, "SYC2026000055", "OK"));
        SycaPayTransaction transaction = transactionPartenaire(SycaPayTransactionStatus.SUCCESS);
        when(commissionService.enregistrerPaiementAutomatique(30L, transaction)).thenReturn(CommissionPayment.builder().amount(12000L).build());

        service.traiterContratSigne(30L);

        ArgumentCaptor<SycaPayPaymentCommand> command = ArgumentCaptor.forClass(SycaPayPaymentCommand.class);
        verify(sycaPayPaymentService).payCommission(command.capture());
        assertEquals(SycaPayBeneficiaryType.PARTENAIRE, command.getValue().beneficiaryType());
        assertEquals(7L, command.getValue().partenaireId());
        assertEquals(12000L, command.getValue().amount());
        assertEquals("0757000007", command.getValue().beneficiaryPhone(), "numero Wave de commission, pas le telephone");
        verify(commissionService).enregistrerPaiementAutomatique(30L, transaction);
        verify(notificationService).notifier(eq(7L), eq(PartenaireNotificationType.COMMISSION_VERSEE), anyString(), anyString(),
                eq(12000L), eq(30L), eq("COMMISSION_PAYEE"), any(), eq(TypeMessageSms.commission_payee));
    }

    @Test
    void immediat_aucunVirementSiUnGardeFouSApplique() {
        record Cas(String nom, Runnable preparation) {
        }
        List<Cas> cas = List.of(
                new Cas("SycaPay desactive", () -> {
                    contratSigne(partenaire(FrequencePaiementCommission.IMMEDIAT), CommissionStatus.pending);
                    when(sycaPayProperties.isEnabled()).thenReturn(false);
                }),
                new Cas("numero Wave absent", () -> {
                    Partenaire p = partenaire(FrequencePaiementCommission.IMMEDIAT);
                    p.setNumeroWaveCommission("  ");
                    contratSigne(p, CommissionStatus.pending);
                }),
                new Cas("avance partielle en cours", () -> {
                    contratSigne(partenaire(FrequencePaiementCommission.IMMEDIAT), CommissionStatus.pending);
                    when(commissionService.aDesPaiementsPartielsEnCours(7L)).thenReturn(true);
                }),
                new Cas("partenaire inactif", () -> {
                    Partenaire p = partenaire(FrequencePaiementCommission.IMMEDIAT);
                    p.setActive(false);
                    contratSigne(p, CommissionStatus.pending);
                }),
                new Cas("commission deja payee", () ->
                        contratSigne(partenaire(FrequencePaiementCommission.IMMEDIAT), CommissionStatus.paid)));

        for (Cas c : cas) {
            when(sycaPayProperties.isEnabled()).thenReturn(true);
            when(commissionService.aDesPaiementsPartielsEnCours(7L)).thenReturn(false);
            c.preparation().run();

            service.traiterContratSigne(30L);
        }

        verify(sycaPayPaymentService, never()).payCommission(any());
        verify(commissionService, never()).enregistrerPaiementAutomatique(anyLong(), any());
    }

    @Test
    void immediat_resultatIncertain_nEnregistreAucunPaiement() {
        contratSigne(partenaire(FrequencePaiementCommission.IMMEDIAT), CommissionStatus.pending);
        when(sycaPayPaymentService.payCommission(any())).thenReturn(
                SycaPayPaymentOutcome.of(SycaPayTransactionStatus.UNKNOWN, 55L, "SYC2026000055", "timeout"));

        service.traiterContratSigne(30L);

        verify(commissionService, never()).enregistrerPaiementAutomatique(anyLong(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void immediat_exceptionSycaPay_estAbsorbee_sansPaiement() {
        contratSigne(partenaire(FrequencePaiementCommission.IMMEDIAT), CommissionStatus.pending);
        when(sycaPayPaymentService.payCommission(any())).thenThrow(new SycaPayException("identifiants incomplets"));

        service.traiterContratSigne(30L);

        verify(commissionService, never()).enregistrerPaiementAutomatique(anyLong(), any());
    }

    // ------------------------------------------------------------------ autres frequences a la signature

    @Test
    void aLaDemande_notifieLaCommissionGagneeAvecLeTotalDisponible_sansPayer() {
        contratSigne(partenaire(FrequencePaiementCommission.A_LA_DEMANDE), CommissionStatus.pending);
        when(commissionService.totalPendingFor(7L)).thenReturn(1250000L);

        service.traiterContratSigne(30L);

        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).notifier(eq(7L), eq(PartenaireNotificationType.COMMISSION_GAGNEE), anyString(), anyString(),
                eq(12000L), eq(30L), eq("COMMISSION_GAGNEE"), variables.capture(), eq(TypeMessageSms.commission_gagnee));
        assertEquals(Map.of("montant", "12 000", "police", "POL-123", "total", "1 250 000"), variables.getValue());
        verify(sycaPayPaymentService, never()).payCommission(any());
    }

    @Test
    void frequencePeriodique_rienALaSignature() {
        for (FrequencePaiementCommission frequence : EnumSet.of(FrequencePaiementCommission.HEBDOMADAIRE,
                FrequencePaiementCommission.QUINZAINE, FrequencePaiementCommission.MENSUELLE)) {
            contratSigne(partenaire(frequence), CommissionStatus.pending);
            service.traiterContratSigne(30L);
        }
        verifyNoInteractions(sycaPayPaymentService, notificationService);
    }

    // ------------------------------------------------------------------ virement confirme (reconciliation)

    @Test
    void virementConfirme_ignoreLesCommerciauxEtLesVirementsDejaEnregistres() {
        SycaPayTransaction commerciale = transactionPartenaire(SycaPayTransactionStatus.SUCCESS);
        commerciale.setBeneficiaryType(SycaPayBeneficiaryType.COMMERCIAL);
        service.enregistrerVirementConfirme(55L);

        transactionPartenaire(SycaPayTransactionStatus.SUCCESS);
        when(commissionPaymentRepository.existsBySycapayTransactionId(55L)).thenReturn(true);
        service.enregistrerVirementConfirme(55L);

        verify(commissionService, never()).enregistrerPaiementAutomatique(anyLong(), any());
    }

    @Test
    void virementConfirme_surCommissionDejaPayee_alerteDoublePaiement() {
        SycaPayTransaction transaction = transactionPartenaire(SycaPayTransactionStatus.SUCCESS);
        when(commissionService.enregistrerPaiementAutomatique(30L, transaction)).thenReturn(null);

        service.enregistrerVirementConfirme(55L);

        verify(telegramNotificationService).envoyer(org.mockito.ArgumentMatchers.contains("double paiement"));
        verifyNoInteractions(notificationService);
    }

    // ------------------------------------------------------------------ rappels periodiques

    @Test
    void frequencesDues_lundi_1er_16() {
        assertEquals(EnumSet.of(FrequencePaiementCommission.HEBDOMADAIRE),
                PartenaireCommissionAutomationService.frequencesDues(LocalDate.of(2026, 9, 28)));
        assertEquals(EnumSet.of(FrequencePaiementCommission.QUINZAINE, FrequencePaiementCommission.MENSUELLE),
                PartenaireCommissionAutomationService.frequencesDues(LocalDate.of(2026, 10, 1)));
        assertEquals(EnumSet.of(FrequencePaiementCommission.QUINZAINE),
                PartenaireCommissionAutomationService.frequencesDues(LocalDate.of(2026, 9, 16)));
        assertEquals(EnumSet.of(FrequencePaiementCommission.HEBDOMADAIRE, FrequencePaiementCommission.QUINZAINE,
                        FrequencePaiementCommission.MENSUELLE),
                PartenaireCommissionAutomationService.frequencesDues(LocalDate.of(2026, 6, 1)));
        assertTrue(PartenaireCommissionAutomationService.frequencesDues(LocalDate.of(2026, 9, 23)).isEmpty());
    }

    @Test
    void rappels_uniquementSiCommissionsDisponibles_etUnEchecNeBloquePasLesAutres() {
        Partenaire avecSolde = Partenaire.builder().id(1L).nom("A").build();
        Partenaire sansSolde = Partenaire.builder().id(2L).nom("B").build();
        Partenaire enErreur = Partenaire.builder().id(3L).nom("C").build();
        Partenaire apresErreur = Partenaire.builder().id(4L).nom("D").build();
        when(partenaireRepository.findByActiveTrueAndFrequencePaiementCommissionIn(EnumSet.of(FrequencePaiementCommission.HEBDOMADAIRE)))
                .thenReturn(List.of(avecSolde, sansSolde, enErreur, apresErreur));
        when(commissionService.totalPendingFor(1L)).thenReturn(25000L);
        when(commissionService.totalPendingFor(2L)).thenReturn(0L);
        when(commissionService.totalPendingFor(3L)).thenThrow(new IllegalStateException("base indisponible"));
        when(commissionService.totalPendingFor(4L)).thenReturn(8000L);

        int envoyes = service.envoyerRappels(LocalDate.of(2026, 9, 28));

        assertEquals(2, envoyes);
        verify(notificationService).notifier(eq(1L), eq(PartenaireNotificationType.COMMISSION_DISPONIBLE), anyString(), anyString(),
                eq(25000L), isNull(), eq("COMMISSION_DISPONIBLE"), eq(Map.of("montant", "25 000")), eq(TypeMessageSms.commission_disponible));
        verify(notificationService).notifier(eq(4L), any(), anyString(), anyString(), eq(8000L), isNull(), any(), any(), any());
        verify(notificationService, never()).notifier(eq(2L), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rappels_jourSansEcheance_neLitMemePasLesPartenaires() {
        assertEquals(0, service.envoyerRappels(LocalDate.of(2026, 9, 23)));
        verifyNoInteractions(partenaireRepository);
    }

    /** Paiement automatique : le resultat reel du SMS (smsEnvoye) est reporte sur le paiement. */
    @Test
    void virementConfirme_reporteLeResultatDuSmsSurLePaiement() {
        SycaPayTransaction transaction = transactionPartenaire(SycaPayTransactionStatus.SUCCESS);
        when(commissionService.enregistrerPaiementAutomatique(30L, transaction))
                .thenReturn(CommissionPayment.builder().id(77L).amount(12000L).build());
        when(notificationService.notifier(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ci.assurzen.entities.PartenaireNotification.builder().smsEnvoye(true).build());

        service.enregistrerVirementConfirme(55L);

        verify(commissionService).enregistrerResultatNotification(77L, true);
    }
}
