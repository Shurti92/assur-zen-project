package ci.assurzen.sycapay.service;

import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayAuthResponse;
import ci.assurzen.sycapay.exception.SycaPayAuthenticationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SycaPayAuthenticationServiceTest {

    private SycaPayProperties properties(long ttlSeconds) {
        SycaPayProperties properties = new SycaPayProperties();
        properties.setTokenTtlSeconds(ttlSeconds);
        properties.setTokenExpiryMarginSeconds(0L);
        return properties;
    }

    @Test
    void getValidToken_reappelleAuthenticateSiAucunCache() {
        SycaPayClient client = mock(SycaPayClient.class);
        when(client.authenticate()).thenReturn(new SycaPayAuthResponse(0, "tok-1", "SUCCESS"));
        SycaPayAuthenticationService service = new SycaPayAuthenticationService(client, properties(40));

        String token = service.getValidToken();

        assertEquals("tok-1", token);
        verify(client, times(1)).authenticate();
    }

    @Test
    void getValidToken_reutiliseLeCacheTantQuilEstValide() {
        SycaPayClient client = mock(SycaPayClient.class);
        when(client.authenticate()).thenReturn(new SycaPayAuthResponse(0, "tok-1", "SUCCESS"));
        SycaPayAuthenticationService service = new SycaPayAuthenticationService(client, properties(60));

        String first = service.getValidToken();
        String second = service.getValidToken();

        assertEquals(first, second);
        verify(client, times(1)).authenticate();
    }

    @Test
    void getValidToken_reauthentifieUneFoisLeTtlDepasse() throws InterruptedException {
        SycaPayClient client = mock(SycaPayClient.class);
        when(client.authenticate())
                .thenReturn(new SycaPayAuthResponse(0, "tok-1", "SUCCESS"))
                .thenReturn(new SycaPayAuthResponse(0, "tok-2", "SUCCESS"));
        // TTL tres court : le second appel doit forcement retomber apres expiration.
        SycaPayAuthenticationService service = new SycaPayAuthenticationService(client, properties(1));

        String first = service.getValidToken();
        Thread.sleep(1100);
        String second = service.getValidToken();

        assertEquals("tok-1", first);
        assertEquals("tok-2", second);
        verify(client, times(2)).authenticate();
    }

    @Test
    void getValidToken_leveUneExceptionSiSycaPayRefuse() {
        SycaPayClient client = mock(SycaPayClient.class);
        when(client.authenticate()).thenReturn(new SycaPayAuthResponse(-23, null, "EMPTY Probleme rencontre"));
        SycaPayAuthenticationService service = new SycaPayAuthenticationService(client, properties(40));

        assertThrows(SycaPayAuthenticationException.class, service::getValidToken);
    }

    @Test
    void invalidate_forceUneReauthentificationAuProchainAppel() {
        SycaPayClient client = mock(SycaPayClient.class);
        when(client.authenticate())
                .thenReturn(new SycaPayAuthResponse(0, "tok-1", "SUCCESS"))
                .thenReturn(new SycaPayAuthResponse(0, "tok-2", "SUCCESS"));
        SycaPayAuthenticationService service = new SycaPayAuthenticationService(client, properties(60));

        service.getValidToken();
        service.invalidate();
        String afterInvalidate = service.getValidToken();

        assertEquals("tok-2", afterInvalidate);
        verify(client, times(2)).authenticate();
    }
}
