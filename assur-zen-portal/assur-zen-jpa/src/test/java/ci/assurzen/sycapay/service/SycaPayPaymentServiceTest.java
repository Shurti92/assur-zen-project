package ci.assurzen.sycapay.service;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayBalanceEntry;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.dto.SycaPayStatusResponse;
import ci.assurzen.sycapay.dto.SycaPayTransferRequest;
import ci.assurzen.sycapay.dto.SycaPayTransferResponse;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayBalanceException;
import ci.assurzen.sycapay.exception.SycaPayDuplicatePaymentException;
import ci.assurzen.sycapay.exception.SycaPayException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre le coeur de la state machine de paiement (section 11 de la specification) : succes
 * confirme, traitement asynchrone (pending), resultat incertain (timeout -> UNKNOWN, jamais FAILED),
 * echec explicite, et le seul cas de retry automatique tolere (token incorrect/expire).
 */
class SycaPayPaymentServiceTest {

    private static final SycaPayPaymentCommand COMMAND = new SycaPayPaymentCommand(10L, 50L, 25000L, "Jean Koffi", "+2250700000000");

    private SycaPayProperties enabledProperties() {
        SycaPayProperties properties = new SycaPayProperties();
        properties.setEnabled(true);
        properties.setMerchantId("C_TEST");
        properties.setApiKey("test-api-key");
        properties.setSecretKey("test-secret-key");
        return properties;
    }

    private SycaPayTransaction pendingTransaction() {
        SycaPayTransaction t = new SycaPayTransaction();
        t.setId(1L);
        t.setTransactionReference("SYC2026000001");
        t.setStatus(SycaPayTransactionStatus.PENDING);
        return t;
    }

    /** Format confirme sur un exemple reel (2026-09-22) : un element par operateur (Wave/Orange/MTN/
     * Moov), champ "solde" en chaine avec 2 decimales. */
    private void stubSoldeWaveSuffisant(SycaPayClient client) {
        when(client.checkBalance()).thenReturn(new SycaPayBalanceResponse("cashout-solde", "Cet Api permet d'obtenir le solde de votre compte", List.of(
                new SycaPayBalanceEntry("450", "0.00", "Moov", "CI", "C_MERCHANT"),
                new SycaPayBalanceEntry("451", "500000.00", "Wave", "CI", "C_MERCHANT")
        )));
    }

    @Test
    void payCommission_desactivee_leveUneException() {
        SycaPayProperties properties = new SycaPayProperties();
        properties.setEnabled(false);
        SycaPayPaymentService service = new SycaPayPaymentService(properties, mock(SycaPayTransactionWriter.class),
                mock(SycaPayAuthenticationService.class), mock(SycaPayClient.class), mock(FunctionalAuditRecorder.class),
                new SimpleMeterRegistry());

        assertThrows(SycaPayException.class, () -> service.payCommission(COMMAND));
    }

    /** Garde live (pas seulement le @PostConstruct de SycaPayProperties, qui ne voit que la valeur
     * STATIQUE) : enabled peut desormais passer a true a chaud via le Dynamic Configuration Engine
     * sans redemarrage - un appel avec des credentials incomplets doit echouer AVANT tout appel
     * reseau, jamais au moment de l'authentification. */
    @Test
    void payCommission_activeeSansCredentialsComplets_leveUneExceptionSansAppelReseau() {
        SycaPayProperties properties = new SycaPayProperties();
        properties.setEnabled(true);
        properties.setMerchantId("C_TEST");
        // apiKey/secretKey laisses vides volontairement.
        SycaPayClient client = mock(SycaPayClient.class);
        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        SycaPayPaymentService service = new SycaPayPaymentService(properties, mock(SycaPayTransactionWriter.class),
                authenticationService, client, mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        assertThrows(SycaPayException.class, () -> service.payCommission(COMMAND));
        verify(authenticationService, never()).getValidToken();
        verify(client, never()).checkBalance();
    }

    @Test
    void payCommission_contratDejaEnCours_estRejeteSansAppelReseau() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new SycaPayDuplicatePaymentException("deja en cours"));
        SycaPayClient client = mock(SycaPayClient.class);
        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer,
                mock(SycaPayAuthenticationService.class), client, mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        SycaPayPaymentOutcome outcome = service.payCommission(COMMAND);

        assertTrue(outcome.rejected());
        verify(client, never()).checkBalance();
        verify(client, never()).transferWave(anyString(), any());
    }

    @Test
    void payCommission_succesConfirme_finaliseEnSuccess() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        SycaPayTransaction pending = pendingTransaction();
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pending);
        SycaPayTransaction success = pendingTransaction();
        success.setStatus(SycaPayTransactionStatus.SUCCESS);
        when(writer.finalizeTransaction(eq(1L), eq(SycaPayTransactionStatus.SUCCESS), any(), any(), any(), any(), any(), any()))
                .thenReturn(success);

        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        when(authenticationService.getValidToken()).thenReturn("tok-1");

        SycaPayClient client = mock(SycaPayClient.class);
        stubSoldeWaveSuffisant(client);
        when(client.transferWave(eq("tok-1"), any())).thenReturn(
                new SycaPayTransferResponse(0, "success", "DEPOT EFFECTUE AVEC SUCCESS", "25000", "XOF", "REFTRANSFER", "SYC2026000001", "Jean Koffi", "Wave"));

        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        SycaPayPaymentOutcome outcome = service.payCommission(COMMAND);

        assertTrue(outcome.isSuccess());
        assertEquals(SycaPayTransactionStatus.SUCCESS, outcome.status());
    }

    @Test
    void payCommission_reponsePending_finaliseEnProcessingSansCreerDePaiement() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        SycaPayTransaction pending = pendingTransaction();
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pending);
        SycaPayTransaction processing = pendingTransaction();
        processing.setStatus(SycaPayTransactionStatus.PROCESSING);
        when(writer.finalizeTransaction(eq(1L), eq(SycaPayTransactionStatus.PROCESSING), any(), any(), any(), any(), any(), any()))
                .thenReturn(processing);

        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        when(authenticationService.getValidToken()).thenReturn("tok-1");
        SycaPayClient client = mock(SycaPayClient.class);
        stubSoldeWaveSuffisant(client);
        when(client.transferWave(eq("tok-1"), any())).thenReturn(
                new SycaPayTransferResponse(0, "pending", "TRAITEMENT EN COURS", "25000", "F CFA", "REFTRANSFER", "SYC2026000001", null, null));

        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        SycaPayPaymentOutcome outcome = service.payCommission(COMMAND);

        assertEquals(SycaPayTransactionStatus.PROCESSING, outcome.status());
        assertTrue(!outcome.isSuccess());
    }

    @Test
    void payCommission_timeoutReseau_finaliseEnUnknownJamaisEnFailed() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        SycaPayTransaction pending = pendingTransaction();
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pending);
        SycaPayTransaction unknown = pendingTransaction();
        unknown.setStatus(SycaPayTransactionStatus.UNKNOWN);
        when(writer.finalizeTransaction(eq(1L), eq(SycaPayTransactionStatus.UNKNOWN), any(), any(), any(), any(), any(), any()))
                .thenReturn(unknown);

        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        when(authenticationService.getValidToken()).thenReturn("tok-1");
        SycaPayClient client = mock(SycaPayClient.class);
        stubSoldeWaveSuffisant(client);
        // Simule un timeout : le client retourne null (cf. SycaPayClientImpl, jamais d'exception ici).
        when(client.transferWave(eq("tok-1"), any())).thenReturn(null);

        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        SycaPayPaymentOutcome outcome = service.payCommission(COMMAND);

        assertEquals(SycaPayTransactionStatus.UNKNOWN, outcome.status());
        verify(writer, never()).finalizeTransaction(eq(1L), eq(SycaPayTransactionStatus.FAILED), any(), any(), any(), any(), any(), any());
    }

    @Test
    void payCommission_tokenIncorrect_reessaieUneSeuleFoisAvecNouveauToken() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        SycaPayTransaction pending = pendingTransaction();
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pending);
        SycaPayTransaction success = pendingTransaction();
        success.setStatus(SycaPayTransactionStatus.SUCCESS);
        when(writer.finalizeTransaction(eq(1L), eq(SycaPayTransactionStatus.SUCCESS), any(), any(), any(), any(), any(), any()))
                .thenReturn(success);

        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        when(authenticationService.getValidToken()).thenReturn("tok-perime", "tok-frais");

        SycaPayClient client = mock(SycaPayClient.class);
        stubSoldeWaveSuffisant(client);
        when(client.transferWave(eq("tok-perime"), any())).thenReturn(
                new SycaPayTransferResponse(-19, "failed", "TOKEN EST INCORRECT", null, null, null, null, null, null));
        when(client.transferWave(eq("tok-frais"), any())).thenReturn(
                new SycaPayTransferResponse(0, "success", "DEPOT EFFECTUE AVEC SUCCESS", "25000", "XOF", "REFTRANSFER", "SYC2026000001", "Jean Koffi", "Wave"));

        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        SycaPayPaymentOutcome outcome = service.payCommission(COMMAND);

        assertTrue(outcome.isSuccess());
        verify(authenticationService).invalidate();
        verify(client, times(2)).transferWave(anyString(), any(SycaPayTransferRequest.class));
    }

    @Test
    void payCommission_echecExplicite_finaliseEnFailed() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        SycaPayTransaction pending = pendingTransaction();
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pending);
        SycaPayTransaction failed = pendingTransaction();
        failed.setStatus(SycaPayTransactionStatus.FAILED);
        when(writer.finalizeTransaction(eq(1L), eq(SycaPayTransactionStatus.FAILED), any(), any(), any(), any(), any(), any()))
                .thenReturn(failed);

        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        when(authenticationService.getValidToken()).thenReturn("tok-1");
        SycaPayClient client = mock(SycaPayClient.class);
        stubSoldeWaveSuffisant(client);
        when(client.transferWave(eq("tok-1"), any())).thenReturn(
                new SycaPayTransferResponse(-26, "failed", "Incorrect amount for this operator", null, null, null, null, null, null));

        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        SycaPayPaymentOutcome outcome = service.payCommission(COMMAND);

        assertEquals(SycaPayTransactionStatus.FAILED, outcome.status());
    }

    /** Exigence explicite (2026-09-22) : le solde doit TOUJOURS etre verifie avant un paiement - un
     * solde insuffisant doit finaliser la transaction en FAILED SANS jamais appeler checktransferwave
     * (aucune ambiguite possible : aucun virement n'a ete tente). */
    @Test
    void payCommission_soldeWaveInsuffisant_finaliseEnFailedSansAppelerLeTransfert() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        SycaPayTransaction pending = pendingTransaction();
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pending);
        SycaPayTransaction failed = pendingTransaction();
        failed.setStatus(SycaPayTransactionStatus.FAILED);
        when(writer.finalizeTransaction(eq(1L), eq(SycaPayTransactionStatus.FAILED), any(), any(), any(), any(), any(), any()))
                .thenReturn(failed);

        SycaPayClient client = mock(SycaPayClient.class);
        when(client.checkBalance()).thenReturn(new SycaPayBalanceResponse("cashout-solde", "desc", List.of(
                new SycaPayBalanceEntry("451", "100.00", "Wave", "CI", "C_MERCHANT"))));
        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);

        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        // COMMAND demande 25000, le solde Wave disponible n'est que de 100.
        SycaPayPaymentOutcome outcome = service.payCommission(COMMAND);

        assertEquals(SycaPayTransactionStatus.FAILED, outcome.status());
        verify(authenticationService, never()).getValidToken();
        verify(client, never()).transferWave(anyString(), any());
    }

    @Test
    void payCommission_soldeIndisponible_finaliseEnFailedSansAppelerLeTransfert() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        SycaPayTransaction pending = pendingTransaction();
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pending);
        SycaPayTransaction failed = pendingTransaction();
        failed.setStatus(SycaPayTransactionStatus.FAILED);
        when(writer.finalizeTransaction(eq(1L), eq(SycaPayTransactionStatus.FAILED), any(), any(), any(), any(), any(), any()))
                .thenReturn(failed);

        SycaPayClient client = mock(SycaPayClient.class);
        when(client.checkBalance()).thenThrow(new SycaPayBalanceException("SycaPay indisponible"));
        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);

        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        SycaPayPaymentOutcome outcome = service.payCommission(COMMAND);

        assertEquals(SycaPayTransactionStatus.FAILED, outcome.status());
        verify(client, never()).transferWave(anyString(), any());
    }

    /** Correctif 2026-09-23 (processing_date) : la date d'envoi est persistee AVANT l'appel HTTP, et
     * uniquement quand le virement part reellement. */
    @Test
    void payCommission_dateEnvoiPosee_avantLAppelDeVirement() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pendingTransaction());
        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        when(authenticationService.getValidToken()).thenReturn("tok-1");
        SycaPayClient client = mock(SycaPayClient.class);
        stubSoldeWaveSuffisant(client);
        when(client.transferWave(eq("tok-1"), any())).thenReturn(null);
        when(writer.finalizeTransaction(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(pendingTransaction());
        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        service.payCommission(COMMAND);

        InOrder ordre = inOrder(writer, client);
        ordre.verify(writer).markTransferSent(1L);
        ordre.verify(client).transferWave(eq("tok-1"), any());
    }

    @Test
    void payCommission_soldeInsuffisant_nePosePasDeDateEnvoi() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pendingTransaction());
        when(writer.finalizeTransaction(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(pendingTransaction());
        SycaPayClient client = mock(SycaPayClient.class);
        when(client.checkBalance()).thenReturn(new SycaPayBalanceResponse("cashout-solde", "solde", List.of(
                new SycaPayBalanceEntry("451", "10.00", "Wave", "CI", "C_MERCHANT"))));
        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer,
                mock(SycaPayAuthenticationService.class), client, mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        service.payCommission(COMMAND);

        verify(writer, never()).markTransferSent(any());
    }

    /** Correctif 2026-09-23 (sycapay_transaction_id) : checktransferwave ne le renvoie pas - recupere
     * via getStatusByOrderID apres un succes confirme. */
    @Test
    void payCommission_succes_recupereLIdentifiantDeTransactionSycaPay() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pendingTransaction());
        SycaPayTransaction success = pendingTransaction();
        success.setStatus(SycaPayTransactionStatus.SUCCESS);
        when(writer.finalizeTransaction(eq(1L), eq(SycaPayTransactionStatus.SUCCESS), any(), any(), any(), any(), any(), any()))
                .thenReturn(success);
        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        when(authenticationService.getValidToken()).thenReturn("tok-1");
        SycaPayClient client = mock(SycaPayClient.class);
        stubSoldeWaveSuffisant(client);
        when(client.transferWave(eq("tok-1"), any())).thenReturn(
                new SycaPayTransferResponse(0, "success", "DEPOT EFFECTUE AVEC SUCCESS", "25000", "XOF", "REFTRANSFER", "SYC2026000001", "Jean Koffi", "Wave"));
        when(client.getStatus("SYC2026000001")).thenReturn(new SycaPayStatusResponse(0, "success", "2026-09-23 10:00:00", "Wave",
                "25000", "REFTRANSFER", "TXWAVE-123", "+2250700000000", "SYC2026000001", "OK", "OK"));
        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        SycaPayPaymentOutcome outcome = service.payCommission(COMMAND);

        assertTrue(outcome.isSuccess());
        verify(writer).recordSycapayTransactionId(1L, "TXWAVE-123");
    }

    @Test
    void payCommission_succes_echecDeRecuperationDeLIdentifiant_neRemetPasEnCauseLePaiement() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pendingTransaction());
        SycaPayTransaction success = pendingTransaction();
        success.setStatus(SycaPayTransactionStatus.SUCCESS);
        when(writer.finalizeTransaction(eq(1L), eq(SycaPayTransactionStatus.SUCCESS), any(), any(), any(), any(), any(), any()))
                .thenReturn(success);
        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        when(authenticationService.getValidToken()).thenReturn("tok-1");
        SycaPayClient client = mock(SycaPayClient.class);
        stubSoldeWaveSuffisant(client);
        when(client.transferWave(eq("tok-1"), any())).thenReturn(
                new SycaPayTransferResponse(0, "success", "DEPOT EFFECTUE AVEC SUCCESS", "25000", "XOF", "REFTRANSFER", "SYC2026000001", "Jean Koffi", "Wave"));
        when(client.getStatus(anyString())).thenThrow(new IllegalStateException("reponse illisible"));
        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        SycaPayPaymentOutcome outcome = service.payCommission(COMMAND);

        assertTrue(outcome.isSuccess());
        verify(writer, never()).recordSycapayTransactionId(any(), any());
    }

    /** Paiement des commissions partenaire (2026-09-23) : type de beneficiaire transmis au writer et
     * libelle du virement "Commission partenaire". */
    @Test
    void payCommission_partenaire_creeUneTransactionPartenaire_etLibelleLeVirement() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(pendingTransaction());
        when(writer.finalizeTransaction(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(pendingTransaction());
        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        when(authenticationService.getValidToken()).thenReturn("tok-1");
        SycaPayClient client = mock(SycaPayClient.class);
        stubSoldeWaveSuffisant(client);
        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());

        service.payCommission(SycaPayPaymentCommand.forPartenaire(30L, 7L, 12000L, "Ama Koffi", "0757000007"));

        verify(writer).createOrReactivatePending(eq(30L), isNull(), eq(SycaPayBeneficiaryType.PARTENAIRE), isNull(), eq(7L), eq(12000L),
                any(), eq("Ama Koffi"), eq("0757000007"), eq(false));
        ArgumentCaptor<SycaPayTransferRequest> request = ArgumentCaptor.forClass(SycaPayTransferRequest.class);
        verify(client).transferWave(eq("tok-1"), request.capture());
        assertTrue(request.getValue().toString().contains("Commission partenaire"), request.getValue().toString());
    }

    /** MODE TEST (hors production, 2026-09-24) : numero et montant forces AVANT la transaction - le solde
     * verifie, le montant signe et le numero credite sont ceux du test, et la transaction est marquee. */
    @Test
    void payCommission_modeTest_redirigeNumeroEtMontant_etMarqueLaTransaction() {
        SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
        when(writer.createOrReactivatePending(any(), any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(pendingTransaction());
        when(writer.finalizeTransaction(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(pendingTransaction());
        SycaPayAuthenticationService authenticationService = mock(SycaPayAuthenticationService.class);
        when(authenticationService.getValidToken()).thenReturn("tok-1");
        SycaPayClient client = mock(SycaPayClient.class);
        stubSoldeWaveSuffisant(client);
        SycaPayPaymentService service = new SycaPayPaymentService(enabledProperties(), writer, authenticationService, client,
                mock(FunctionalAuditRecorder.class), new SimpleMeterRegistry());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "modeTest",
                new ci.assurzen.sycapay.config.SycaPayModeTest(true, "0700000099", 100L, new String[]{"dev"}));

        service.payCommission(SycaPayPaymentCommand.forPartenaire(30L, 7L, 12000L, "Ama Koffi", "0757000007"));

        verify(writer).createOrReactivatePending(eq(30L), isNull(), eq(SycaPayBeneficiaryType.PARTENAIRE), isNull(), eq(7L), eq(100L),
                any(), eq("Ama Koffi"), eq("0700000099"), eq(true));
        ArgumentCaptor<SycaPayTransferRequest> request = ArgumentCaptor.forClass(SycaPayTransferRequest.class);
        verify(client).transferWave(eq("tok-1"), request.capture());
        assertEquals("100", request.getValue().amount());
        assertEquals("+2250700000099", request.getValue().recipientmobile());
    }
}
