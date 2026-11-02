package ci.assurzen.controller;

import ci.assurzen.dto.AuthTokens;
import ci.assurzen.dto.Credentials;
import ci.assurzen.dto.LoginResponse;
import ci.assurzen.service.AuthService;
import ci.assurzen.services.OtpService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression du 25/08/2026 (audit de securite pre-production) : le Path du cookie refresh_token
 * etait calcule comme wsPrefix + "auth" (ex. "/api-assur-zen/auth"), qui ne correspond au chemin
 * reellement vu par le navigateur qu'en DEV. En RECETTE/PROD, le frontend appelle l'API derriere un
 * prefixe de reverse-proxy ("/assur-zen-gateway(-recette)/") que cette classe ignorait totalement -
 * le cookie n'aurait donc jamais ete renvoye par le navigateur sur /auth/refresh dans ces
 * environnements. Path("/") est la seule valeur qui reste correcte quel que soit ce prefixe.
 */
class LoginControllerCookieTest {

    @Test
    void login_poseUnCookieRefreshTokenAvecPathRacine() {
        AuthService authService = mock(AuthService.class);
        OtpService otpService = mock(OtpService.class);
        LoginController controller = new LoginController(authService, otpService);
        ReflectionTestUtils.setField(controller, "refreshTtlSeconds", 604800L);

        AuthTokens tokens = new AuthTokens("access-123", "refresh-456", "Bearer", 3600);
        when(authService.login(new Credentials("0700000000", "motdepasse"))).thenReturn(new LoginResponse(null, tokens));

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        controller.login(new Credentials("0700000000", "motdepasse"), httpResponse);

        String setCookieHeader = httpResponse.getHeader("Set-Cookie");
        assertTrue(setCookieHeader != null && setCookieHeader.contains("refresh_token=refresh-456"),
                "Le cookie refresh_token doit porter le refresh token emis");
        assertTrue(setCookieHeader.contains("HttpOnly"), "Le cookie doit etre HttpOnly (inaccessible en JS)");
        assertTrue(setCookieHeader.contains("Secure"), "Le cookie doit exiger HTTPS");
        assertTrue(setCookieHeader.contains("SameSite=None"), "SameSite=None requis pour un frontend cross-site (assurzen.ci)");
        assertTrue(setCookieHeader.contains("Path=/"), "Path=/ - pas de prefixe reverse-proxy suppose (regression RECETTE/PROD)");

        Cookie servletCookie = httpResponse.getCookie("refresh_token");
        assertEquals("/", servletCookie.getPath());

        assertLegacyPathAussiEfface(httpResponse);
    }

    /** Regression du 25/08/2026 (2e correctif, meme jour) : un navigateur utilise avant le passage a
     * Path("/") peut porter un cookie sur l'ancien Path ("/api-assur-zen/auth"). Comme les deux
     * cookies coexistent sous le meme nom, le navigateur envoie l'ancien EN PREMIER (plus specifique)
     * - extraireRefreshTokenCookie() ne lit que le premier trouve, donc l'ancien (potentiellement
     * deja revoque) masquait systematiquement le nouveau. Chaque reponse doit donc aussi effacer
     * explicitement l'ancien Path, sans attendre son expiration naturelle (jusqu'a 7 jours). */
    private void assertLegacyPathAussiEfface(MockHttpServletResponse httpResponse) {
        List<String> setCookieHeaders = httpResponse.getHeaders("Set-Cookie");
        assertEquals(2, setCookieHeaders.size(), "Une reponse qui pose/efface le cookie doit aussi effacer l'ancien Path");
        boolean legacyCleared = setCookieHeaders.stream()
                .anyMatch(h -> h.contains("Path=/api-assur-zen/auth") && h.contains("Max-Age=0"));
        assertTrue(legacyCleared, "Doit contenir un Set-Cookie expirant explicitement l'ancien Path=/api-assur-zen/auth");
    }

    @Test
    void logout_effaceLeCookieAvecLeMemePathQueCeluiPose() {
        AuthService authService = mock(AuthService.class);
        OtpService otpService = mock(OtpService.class);
        LoginController controller = new LoginController(authService, otpService);

        org.springframework.mock.web.MockHttpServletRequest httpRequest = new org.springframework.mock.web.MockHttpServletRequest();
        httpRequest.addHeader("Authorization", "Bearer access-123");
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        controller.logout(httpRequest, httpResponse);

        Cookie servletCookie = httpResponse.getCookie("refresh_token");
        assertEquals("/", servletCookie.getPath());
        assertEquals(0, servletCookie.getMaxAge());

        assertLegacyPathAussiEfface(httpResponse);
    }
}
