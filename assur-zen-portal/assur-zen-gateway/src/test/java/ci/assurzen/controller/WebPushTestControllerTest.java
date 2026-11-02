package ci.assurzen.controller;

import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.webpush.dto.WebPushMessage;
import ci.assurzen.webpush.service.WebPushNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Profile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Push de test (2026-09-26) : dev uniquement, vers les seuls appareils de l'utilisateur connecte. */
class WebPushTestControllerTest {

    @Test
    void envoieUnMessageFixeAuxAppareilsDeLUtilisateurConnecte() throws Exception {
        WebPushNotificationService pushService = mock(WebPushNotificationService.class);
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(70L, "0700000007", "PARTENAIRE"));
        when(pushService.estActif()).thenReturn(true);
        when(pushService.sendToUser(eq(70L), org.mockito.ArgumentMatchers.any())).thenReturn(2);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebPushTestController(pushService, authService))
                .addPlaceholderValue("env.ws", "/api-assur-zen/").build();

        mockMvc.perform(post("/api-assur-zen/notifications/push/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appareilsAtteints").value(2))
                .andExpect(jsonPath("$.pushActif").value(true));

        ArgumentCaptor<WebPushMessage> message = ArgumentCaptor.forClass(WebPushMessage.class);
        verify(pushService).sendToUser(eq(70L), message.capture());
        assertEquals("TEST", message.getValue().data().get("type"));
    }

    @Test
    void existeUniquementEnProfilDevOuLocal() {
        Profile profil = WebPushTestController.class.getAnnotation(Profile.class);
        assertEquals(List.of("dev", "local"), List.of(profil.value()));
        assertFalse(List.of(profil.value()).contains("prod"));
    }
}
