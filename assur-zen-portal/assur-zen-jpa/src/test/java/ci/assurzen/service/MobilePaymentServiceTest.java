package ci.assurzen.service;

import ci.assurzen.dto.MobilePaymentDtos;
import ci.assurzen.entities.ClientAccountInsured;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.MobilePayment;
import ci.assurzen.exception.MobilePaymentException;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.MobilePaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Paiement mobile simule (demande n°4, 2026-11-02). */
class MobilePaymentServiceTest {

    private static final LocalDate AUJOURDHUI = LocalDate.now(ZoneId.of("Africa/Abidjan"));
    private MobilePaymentRepository payments;
    private ClientAccountInsuredRepository liens;
    private ContractRepository contrats;
    private MobilePaymentService service;
    private Contract contrat;

    @BeforeEach
    void setUp() {
        payments = mock(MobilePaymentRepository.class);
        liens = mock(ClientAccountInsuredRepository.class);
        contrats = mock(ContractRepository.class);
        service = new MobilePaymentService(payments, liens, contrats, mock(MobileNotificationService.class));
        service.setSimulation(true);
        when(liens.findByUserId(45L)).thenReturn(List.of(new ClientAccountInsured(45L, 627L)));

        contrat = new Contract();
        contrat.setId(655L);
        contrat.setPolicyNumber("P-655");
        contrat.setStatus(ContractStatus.signed);
        contrat.setDateEcheance(AUJOURDHUI.plusDays(12));
        contrat.setPremium(25000L);
        Insured assure = new Insured();
        assure.setId(627L);
        contrat.setInsured(assure);
        when(contrats.findByIdAndInsuredIdIn(655L, List.of(627L))).thenReturn(Optional.of(contrat));
        when(contrats.findByContratPrecedentId(655L)).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any(MobilePayment.class))).thenAnswer(inv -> {
            MobilePayment p = inv.getArgument(0);
            p.setId(3L);
            return p;
        });
        when(payments.save(any(MobilePayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payments.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(any(), any())).thenReturn(List.of());
    }

    private MobilePaymentDtos.InitiateRequest requete(String payable, Long montant) {
        return new MobilePaymentDtos.InitiateRequest(payable, "wave", "0701020304", montant, null);
    }

    private int statut(Runnable action) {
        return assertThrows(MobilePaymentException.class, action::run).getHttpStatus();
    }

    @Test
    void sansSimulation_aucunMoyenActifEtInitiationRefusee() {
        service.setSimulation(false);
        assertFalse(service.moyens().items().get(0).enabled());
        MobilePaymentException e = assertThrows(MobilePaymentException.class,
                () -> service.initier(45L, requete(MobilePaymentService.referenceRenouvellement(contrat), null), null));
        assertEquals("METHOD_DISABLED", e.getCode());
    }

    @Test
    void initiation_montantDuServeur_etReferenceDePaiement() {
        MobilePaymentDtos.Payment p = service.initier(45L, requete(MobilePaymentService.referenceRenouvellement(contrat), 25000L), "cle-1");
        assertEquals("PENDING", p.status());
        assertEquals(25000L, p.amount());
        assertTrue(p.simulated());
        assertTrue(p.reference().startsWith("PAY-"));
        assertEquals(null, p.receiptUrl());
    }

    @Test
    void montantDifferent_donne409AmountMismatch() {
        MobilePaymentException e = assertThrows(MobilePaymentException.class,
                () -> service.initier(45L, requete(MobilePaymentService.referenceRenouvellement(contrat), 1L), null));
        assertEquals("AMOUNT_MISMATCH", e.getCode());
        assertEquals(409, e.getHttpStatus());
    }

    @Test
    void referenceInconnueOuD_unTiers_donne404PayableNotFound() {
        assertEquals("PAYABLE_NOT_FOUND", assertThrows(MobilePaymentException.class,
                () -> service.initier(45L, requete("RN-999-20261114", null), null)).getCode());
        assertEquals("PAYABLE_NOT_FOUND", assertThrows(MobilePaymentException.class,
                () -> service.initier(45L, requete("n-importe-quoi", null), null)).getCode());
        // echeance modifiee depuis : l'ancienne reference n'est plus valable
        assertEquals("PAYABLE_NOT_FOUND", assertThrows(MobilePaymentException.class,
                () -> service.initier(45L, requete("RN-655-20200101", null), null)).getCode());
    }

    @Test
    void dejaPaye_donne409() {
        String ref = MobilePaymentService.referenceRenouvellement(contrat);
        when(payments.existsByPayableReferenceAndStatus(ref, "CONFIRMED")).thenReturn(true);
        assertEquals("PAYMENT_ALREADY_CONFIRMED",
                assertThrows(MobilePaymentException.class, () -> service.initier(45L, requete(ref, null), null)).getCode());
    }

    @Test
    void numeroInvalide_donne400() {
        MobilePaymentDtos.InitiateRequest r = new MobilePaymentDtos.InitiateRequest(
                MobilePaymentService.referenceRenouvellement(contrat), "wave", "0912345678", null, null);
        assertEquals(400, statut(() -> service.initier(45L, r, null)));
    }

    @Test
    void sixiemeTentativeDansLHeure_donne429() {
        MobilePayment ancien = new MobilePayment();
        when(payments.findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(ancien, ancien, ancien, ancien, ancien));
        MobilePaymentException e = assertThrows(MobilePaymentException.class,
                () -> service.initier(45L, requete(MobilePaymentService.referenceRenouvellement(contrat), null), null));
        assertEquals("RATE_LIMITED", e.getCode());
        assertTrue(e.getRetryAfterSeconds() > 0);
    }

    @Test
    void referenceDeRenouvellement_estLieeAUneEcheance() {
        assertEquals("RN-655-" + AUJOURDHUI.plusDays(12).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                MobilePaymentService.referenceRenouvellement(contrat));
    }
}
