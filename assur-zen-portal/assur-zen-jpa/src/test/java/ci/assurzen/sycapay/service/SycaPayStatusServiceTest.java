package ci.assurzen.sycapay.service;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.events.SycaPayPaymentConfirmedEvent;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.dto.SycaPayStatusResponse;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reconciliation (getStatusByOrderID) d'une transaction PROCESSING/UNKNOWN. Regle centrale : seul un
 * statut EXPLICITE et connu de SycaPay tranche - une commande introuvable (-32/-33) ou une absence de
 * reponse laisse la transaction inchangee (jamais FAILED, qui la rendrait re-payable).
 */
class SycaPayStatusServiceTest {

    private final SycaPayTransactionRepository repository = mock(SycaPayTransactionRepository.class);
    private final SycaPayTransactionWriter writer = mock(SycaPayTransactionWriter.class);
    private final SycaPayClient client = mock(SycaPayClient.class);
    private final AuthService authService = mock(AuthService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SycaPayStatusService service =
            new SycaPayStatusService(repository, writer, client, mock(FunctionalAuditRecorder.class), authService, eventPublisher);

    private SycaPayTransaction transactionEn(SycaPayTransactionStatus status) {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        SycaPayTransaction transaction = new SycaPayTransaction();
        transaction.setId(6L);
        transaction.setTransactionReference("DEMO-UNKNOWN");
        transaction.setStatus(status);
        when(repository.findById(6L)).thenReturn(Optional.of(transaction));
        return transaction;
    }

    private static SycaPayStatusResponse reponse(int code, String status) {
        return new SycaPayStatusResponse(code, status, null, "Wave", "40000", "REF-SYCA", "TX-1",
                "0700000006", "DEMO-UNKNOWN", "", "");
    }

    /** Regression reelle du 2026-09-23 : SycaPay a repondu code=-33 + status="failed" pour une commande
     * qu'il ne connait pas, et la transaction etait passee en FAILED. */
    @Test
    void commandeIntrouvable_avecStatusFailed_laisseLaTransactionInchangee() {
        for (int codeIntrouvable : new int[]{-32, -33}) {
            SycaPayTransaction unknown = transactionEn(SycaPayTransactionStatus.UNKNOWN);
            when(client.getStatus("DEMO-UNKNOWN")).thenReturn(reponse(codeIntrouvable, "failed"));

            SycaPayTransaction result = service.reconcile(6L);

            assertSame(unknown, result);
            assertEquals(SycaPayTransactionStatus.UNKNOWN, result.getStatus(), "code " + codeIntrouvable);
        }
        verify(writer, never()).finalizeTransaction(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aucuneReponse_laisseLaTransactionInchangee() {
        transactionEn(SycaPayTransactionStatus.PROCESSING);
        when(client.getStatus(anyString())).thenReturn(null);

        assertEquals(SycaPayTransactionStatus.PROCESSING, service.reconcile(6L).getStatus());
        verify(writer, never()).finalizeTransaction(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void succesConfirme_finaliseEnSuccess() {
        transactionEn(SycaPayTransactionStatus.UNKNOWN);
        when(client.getStatus("DEMO-UNKNOWN")).thenReturn(reponse(0, "success"));
        SycaPayTransaction success = new SycaPayTransaction();
        success.setId(6L);
        when(writer.finalizeTransaction(eq(6L), eq(SycaPayTransactionStatus.SUCCESS), any(), any(), any(), any(), any(), any()))
                .thenReturn(success);

        service.reconcile(6L);

        verify(writer).finalizeTransaction(eq(6L), eq(SycaPayTransactionStatus.SUCCESS), eq("REF-SYCA"), eq("TX-1"),
                eq(0), any(), any(), any());
        // Commission partenaire : le paiement est enregistre apres coup (PartenaireCommissionEventListener).
        verify(eventPublisher).publishEvent(new SycaPayPaymentConfirmedEvent(6L, "admin"));
    }

    /** code=0 + status="failed" (exemple reel Postman) : echec connu de SycaPay, seul cas qui tranche FAILED. */
    @Test
    void echecExpliciteConnuDeSycaPay_finaliseEnFailed() {
        transactionEn(SycaPayTransactionStatus.UNKNOWN);
        when(client.getStatus("DEMO-UNKNOWN")).thenReturn(reponse(0, "failed"));
        SycaPayTransaction failed = new SycaPayTransaction();
        failed.setId(6L);
        when(writer.finalizeTransaction(eq(6L), eq(SycaPayTransactionStatus.FAILED), any(), any(), any(), any(), any(), any()))
                .thenReturn(failed);

        service.reconcile(6L);

        verify(writer).finalizeTransaction(eq(6L), eq(SycaPayTransactionStatus.FAILED), any(), any(), any(), any(), any(), any());
        // Jamais de confirmation de paiement ; seulement l'evenement d'echec (demande de paiement liee, 2026-09-26).
        verify(eventPublisher, never()).publishEvent(any(ci.assurzen.events.SycaPayPaymentConfirmedEvent.class));
        verify(eventPublisher).publishEvent(new ci.assurzen.events.SycaPayPaymentFailedEvent(6L));
    }

    @Test
    void transactionDejaTerminee_renvoie409SansAppelerSycaPay() {
        transactionEn(SycaPayTransactionStatus.SUCCESS);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.reconcile(6L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(client, never()).getStatus(anyString());
    }
}
