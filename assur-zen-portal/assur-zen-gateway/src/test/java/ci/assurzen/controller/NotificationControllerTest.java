package ci.assurzen.controller;

import ci.assurzen.services.PartenaireNotificationService;
import ci.assurzen.webpush.dto.PushPublicKeyResponse;
import ci.assurzen.webpush.dto.PushSubscriptionResponse;
import ci.assurzen.webpush.service.WebPushSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat notifications-push.md de l'equipe EP (2026-09-26) : codes HTTP et validation du payload. */
class NotificationControllerTest {

    private static final String BASE = "/api-assur-zen/notifications";
    private static final String ABONNEMENT = """
            {"endpoint":"https://fcm.googleapis.com/fcm/send/abc","expirationTime":null,
             "keys":{"p256dh":"BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM","auth":"tBHItJI5svbpez7KI4CCXg"}}
            """;

    private MockMvc mockMvc;
    private WebPushSubscriptionService subscriptionService;
    private PartenaireNotificationService notificationService;

    @BeforeEach
    void setUp() {
        subscriptionService = mock(WebPushSubscriptionService.class);
        notificationService = mock(PartenaireNotificationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationService, subscriptionService,
                        mock(ci.assurzen.services.NotificationPreferenceService.class)))
                .addPlaceholderValue("env.ws", "/api-assur-zen/")
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void abonnement_valide_201() throws Exception {
        when(subscriptionService.enregistrer(any(), any())).thenReturn(new PushSubscriptionResponse(1L, true, Instant.now(), Instant.now()));

        mockMvc.perform(post(BASE + "/push-subscriptions").contentType(MediaType.APPLICATION_JSON).content(ABONNEMENT)
                        .header("User-Agent", "Mozilla/5.0"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
        verify(subscriptionService).enregistrer(any(), eq("Mozilla/5.0"));
    }

    @Test
    void abonnement_endpointNonHttpsOuClesManquantes_400() throws Exception {
        mockMvc.perform(post(BASE + "/push-subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content(ABONNEMENT.replace("https://", "http://")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/push-subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/abc\",\"expirationTime\":null}"))
                .andExpect(status().isBadRequest());
        verify(subscriptionService, never()).enregistrer(any(), any());
    }

    @Test
    void desabonnement_204() throws Exception {
        mockMvc.perform(delete(BASE + "/push-subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/abc\"}"))
                .andExpect(status().isNoContent());
        verify(subscriptionService).supprimer("https://fcm.googleapis.com/fcm/send/abc");
    }

    @Test
    void clePublique_etCentreDeNotifications() throws Exception {
        when(subscriptionService.clePublique()).thenReturn(new PushPublicKeyResponse(true, "BPublique"));
        when(notificationService.compterMesNonLues()).thenReturn(3L);
        when(notificationService.marquerToutesMesNotificationsLues()).thenReturn(3);

        mockMvc.perform(get(BASE + "/push/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicKey").value("BPublique"));
        mockMvc.perform(get(BASE + "/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
        mockMvc.perform(put(BASE + "/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }
}
