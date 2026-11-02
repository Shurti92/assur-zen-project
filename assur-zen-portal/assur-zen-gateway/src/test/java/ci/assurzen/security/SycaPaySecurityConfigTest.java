package ci.assurzen.security;

import ci.assurzen.audit.AuditService;
import ci.assurzen.controller.CommercialCommissionController;
import ci.assurzen.controller.SycaPayController;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.services.CommercialCommissionService;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import ci.assurzen.sycapay.dto.SycaPayDashboardResponse;
import ci.assurzen.sycapay.service.SycaPayBalanceService;
import ci.assurzen.sycapay.service.SycaPayMonitoringService;
import ci.assurzen.sycapay.service.SycaPayStatusService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Regles d'acces REELLES de SecurityConfig (vraie chaine de filtres, vrai BearerTokenAuthenticationFilter)
 * sur les endpoints SycaPay et /commercial-commissions/** : reserves au role ADMIN. Seuls JwtTokenService
 * (jeton -> principal) et les services metier sont mockes. Protege contre une regle permitAll ou
 * authenticated() ajoutee plus haut dans la chaine qui ouvrirait ces routes par erreur (Spring Security
 * retient le PREMIER matcher qui correspond).
 */
class SycaPaySecurityConfigTest {

    private static final String ADMIN_TOKEN = "jeton-admin";
    private static final String COMMERCIAL_TOKEN = "jeton-commercial";
    private static final String INVALID_TOKEN = "jeton-expire";

    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mockMvc;

    @BeforeAll
    static void startContext() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("env.ws", "/api-assur-zen/",
                        // Jamais lu : JwtTokenService est mocke, mais Spring injecte quand meme ses @Value herites
                        "app.jwt.secret", "secret-de-test-non-utilise")));
        context.register(TestConfig.class);
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @Test
    void endpointsSycaPay_admin_autorise() throws Exception {
        for (MockHttpServletRequestBuilder request : sycaPayRequests()) {
            assertEquals(200, statusOf(request.header("Authorization", "Bearer " + ADMIN_TOKEN)),
                    "ADMIN doit acceder a " + describe(request));
        }
    }

    @Test
    void endpointsSycaPay_utilisateurNonAdmin_refuse403() throws Exception {
        for (MockHttpServletRequestBuilder request : allProtectedRequests()) {
            assertEquals(403, statusOf(request.header("Authorization", "Bearer " + COMMERCIAL_TOKEN)),
                    "Un non-admin ne doit pas acceder a " + describe(request));
        }
    }

    @Test
    void endpointsSycaPay_anonyme_refuse() throws Exception {
        for (MockHttpServletRequestBuilder request : allProtectedRequests()) {
            int status = statusOf(request);
            assertEquals(true, status == 401 || status == 403,
                    "Un anonyme ne doit pas acceder a " + describe(request) + " (statut " + status + ")");
        }
    }

    @Test
    void endpointsSycaPay_jetonInvalide_refuse401() throws Exception {
        for (MockHttpServletRequestBuilder request : allProtectedRequests()) {
            assertEquals(401, statusOf(request.header("Authorization", "Bearer " + INVALID_TOKEN)),
                    "Un jeton rejete doit donner 401 sur " + describe(request));
        }
    }

    @Test
    void paiementCommissions_admin_autorise() throws Exception {
        assertEquals(201, statusOf(payRequest().header("Authorization", "Bearer " + ADMIN_TOKEN)));
    }

    private static List<MockHttpServletRequestBuilder> sycaPayRequests() {
        return List.of(
                get("/api-assur-zen/sycapay/balance"),
                get("/api-assur-zen/sycapay/dashboard"),
                get("/api-assur-zen/sycapay/transactions"),
                post("/api-assur-zen/sycapay/transactions/1/reconcile"));
    }

    private static List<MockHttpServletRequestBuilder> allProtectedRequests() {
        List<MockHttpServletRequestBuilder> requests = new java.util.ArrayList<>(sycaPayRequests());
        requests.add(get("/api-assur-zen/sycapay/transactions/1"));
        requests.add(get("/api-assur-zen/sycapay/transactions/status-counts"));
        requests.add(payRequest());
        requests.add(get("/api-assur-zen/commercial-commissions/payments"));
        return requests;
    }

    private static MockHttpServletRequestBuilder payRequest() {
        return post("/api-assur-zen/commercial-commissions/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contractIds\":[10]}");
    }

    private static int statusOf(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private static String describe(MockHttpServletRequestBuilder request) {
        var built = request.buildRequest(new MockServletContext());
        return built.getMethod() + " " + built.getRequestURI();
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        JwtTokenService jwtTokenService() {
            JwtTokenService jwtTokenService = mock(JwtTokenService.class);
            when(jwtTokenService.toPrincipal(anyString())).thenAnswer(invocation -> switch (invocation.<String>getArgument(0)) {
                case ADMIN_TOKEN -> new AuthenticatedUserPrincipal(1L, "admin", "ADMIN");
                case COMMERCIAL_TOKEN -> new AuthenticatedUserPrincipal(2L, "commercial", "COMMERCIAL");
                default -> throw new IllegalArgumentException("jeton expire");
            });
            return jwtTokenService;
        }

        @Bean
        AuditService auditService() {
            return mock(AuditService.class);
        }

        @Bean
        SycaPayController sycaPayController() {
            SycaPayBalanceService balanceService = mock(SycaPayBalanceService.class);
            when(balanceService.checkBalance(anyBoolean())).thenReturn(new SycaPayBalanceResponse("checkcashoutsolde", "OK", List.of()));
            SycaPayMonitoringService monitoringService = mock(SycaPayMonitoringService.class);
            when(monitoringService.dashboard()).thenReturn(new SycaPayDashboardResponse(0, 0, 0, 0, 0, 0, false, null, null));
            when(monitoringService.search(null, null, null, null, null, null, null, null, 0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
            SycaPayStatusService statusService = mock(SycaPayStatusService.class);
            ci.assurzen.sycapay.entity.SycaPayTransaction transaction = new ci.assurzen.sycapay.entity.SycaPayTransaction();
            transaction.setId(1L);
            when(statusService.reconcile(1L)).thenReturn(transaction);
            return new SycaPayController(balanceService, monitoringService, statusService);
        }

        @Bean
        CommercialCommissionController commercialCommissionController() {
            CommercialCommissionService service = mock(CommercialCommissionService.class);
            when(service.pay(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            return new CommercialCommissionController(service);
        }
    }
}
