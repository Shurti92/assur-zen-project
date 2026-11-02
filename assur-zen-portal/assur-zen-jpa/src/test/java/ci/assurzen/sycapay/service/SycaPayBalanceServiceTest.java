package ci.assurzen.sycapay.service;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayBalanceEntry;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Couvre le cache de solde (endpoint de polling frontend) : ne doit PAS re-appeler SycaPay a chaque
 * requete, mais doit toujours pouvoir etre contourne explicitement (refresh=true). */
class SycaPayBalanceServiceTest {

    private SycaPayBalanceResponse balanceReponse() {
        return new SycaPayBalanceResponse("cashout-solde", "desc", List.of(
                new SycaPayBalanceEntry("451", "500000.00", "Wave", "CI", "C_MERCHANT")));
    }

    private AuthService adminAuthService() {
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        return authService;
    }

    @Test
    void checkBalance_reutiliseLeCacheTantQuilEstValide() {
        SycaPayClient client = mock(SycaPayClient.class);
        when(client.checkBalance()).thenReturn(balanceReponse());
        SycaPayProperties properties = new SycaPayProperties();
        properties.setBalanceCacheTtlSeconds(60L);
        SycaPayBalanceService service = new SycaPayBalanceService(client, mock(FunctionalAuditRecorder.class), adminAuthService(), properties);

        service.checkBalance();
        service.checkBalance();
        service.checkBalance();

        verify(client, times(1)).checkBalance();
    }

    @Test
    void checkBalance_forceRefresh_contourneToujoursLeCache() {
        SycaPayClient client = mock(SycaPayClient.class);
        when(client.checkBalance()).thenReturn(balanceReponse());
        SycaPayProperties properties = new SycaPayProperties();
        properties.setBalanceCacheTtlSeconds(60L);
        SycaPayBalanceService service = new SycaPayBalanceService(client, mock(FunctionalAuditRecorder.class), adminAuthService(), properties);

        service.checkBalance(true);
        service.checkBalance(true);

        verify(client, times(2)).checkBalance();
    }

    @Test
    void checkBalance_sansDroitsAdministrateur_estRejeteAvec403() {
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "commercial", "COMMERCIAL"));
        SycaPayBalanceService service = new SycaPayBalanceService(mock(SycaPayClient.class), mock(FunctionalAuditRecorder.class),
                authService, new SycaPayProperties());

        assertThrows(ResponseStatusException.class, service::checkBalance);
    }
}
