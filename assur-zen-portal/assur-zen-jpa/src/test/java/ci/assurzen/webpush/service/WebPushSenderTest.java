package ci.assurzen.webpush.service;

import ci.assurzen.webpush.VapidTestKeys;
import ci.assurzen.webpush.config.WebPushProperties;
import ci.assurzen.webpush.entity.WebPushSubscription;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Envoi d'un message a un abonnement (2026-09-26) : vrai chiffrement et vraie signature VAPID, seul
 * l'appel reseau est simule. */
class WebPushSenderTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final WebPushProperties properties = VapidTestKeys.proprietesConfigurees();
    private final WebPushSender sender = new WebPushSender(properties, httpClient);

    private static WebPushSubscription abonnement() {
        return WebPushSubscription.builder().id(1L)
                .endpoint("https://fcm.googleapis.com/fcm/send/abc123")
                .p256dh(VapidTestKeys.nouvellePaire().publique())
                .auth(VapidTestKeys.authNavigateur())
                .build();
    }

    @SuppressWarnings("unchecked")
    private void reponse(int statut) throws Exception {
        HttpResponse<Void> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        doReturn(reponse).when(httpClient).send(any(HttpRequest.class), any());
    }

    @Test
    void accepte_messageChiffreEtSigneVapid() throws Exception {
        reponse(201);

        WebPushSender.Envoi envoi = sender.envoyer(abonnement(), "{\"title\":\"t\"}");

        assertEquals(WebPushSender.Resultat.ENVOYE, envoi.resultat());
        ArgumentCaptor<HttpRequest> requete = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requete.capture(), any());
        assertEquals("aes128gcm", requete.getValue().headers().firstValue("Content-Encoding").orElse(null));
        assertTrue(requete.getValue().headers().firstValue("Authorization").orElse("").startsWith("vapid t="));
        assertEquals("86400", requete.getValue().headers().firstValue("TTL").orElse(null));
    }

    @Test
    void abonnementExpireOuRevoque_404ou410_estInvalide() throws Exception {
        reponse(410);
        assertEquals(WebPushSender.Resultat.INVALIDE, sender.envoyer(abonnement(), "{}").resultat());
        reponse(404);
        assertEquals(WebPushSender.Resultat.INVALIDE, sender.envoyer(abonnement(), "{}").resultat());
    }

    @Test
    void serviceIndisponibleOuLimite_estUnEchecTransitoire() throws Exception {
        reponse(503);
        assertEquals(WebPushSender.Resultat.ECHEC, sender.envoyer(abonnement(), "{}").resultat());
        reponse(429);
        assertEquals(WebPushSender.Resultat.ECHEC, sender.envoyer(abonnement(), "{}").resultat());
    }

    @Test
    void timeoutEtErreurReseau_sontDesEchecsTransitoires() throws Exception {
        doThrow(new HttpTimeoutException("timeout")).when(httpClient).send(any(HttpRequest.class), any());
        assertEquals(WebPushSender.Resultat.ECHEC, sender.envoyer(abonnement(), "{}").resultat());

        doThrow(new IOException("connexion refusee")).when(httpClient).send(any(HttpRequest.class), any());
        assertEquals(WebPushSender.Resultat.ECHEC, sender.envoyer(abonnement(), "{}").resultat());
    }

    @Test
    void clesDuNavigateurInutilisables_abonnementInvalideSansAppelReseau() throws Exception {
        WebPushSubscription abonnement = abonnement();
        abonnement.setP256dh("cle-invalide");

        assertEquals(WebPushSender.Resultat.INVALIDE, sender.envoyer(abonnement, "{}").resultat());
        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @Test
    void vapidNonConfigure_echecSansAppelReseau() throws Exception {
        WebPushSender senderSansCles = new WebPushSender(new WebPushProperties(), httpClient);

        assertEquals(WebPushSender.Resultat.ECHEC, senderSansCles.envoyer(abonnement(), "{}").resultat());
        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }
}
