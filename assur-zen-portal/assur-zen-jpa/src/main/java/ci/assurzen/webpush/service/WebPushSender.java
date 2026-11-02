package ci.assurzen.webpush.service;

import ci.assurzen.webpush.config.WebPushProperties;
import ci.assurzen.webpush.entity.WebPushSubscription;
import com.zerodeplibs.webpush.PushSubscription;
import com.zerodeplibs.webpush.httpclient.StandardHttpClientRequestPreparer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Envoi d'UN message a UN abonnement : chiffrement (RFC 8291), en-tete VAPID (RFC 8292) et appel HTTP
 * au service push du navigateur. Ne leve jamais d'exception : le resultat dit quoi faire de l'abonnement.
 * Aucun contenu de message ni cle n'est journalise.
 */
@Component
@Slf4j
public class WebPushSender {

    public enum Resultat {
        /** Accepte par le service push (201/200/202). */
        ENVOYE,
        /** Abonnement expire ou revoque (404/410) ou cles du navigateur inutilisables : a desactiver. */
        INVALIDE,
        /** Erreur transitoire (timeout, reseau, 429, 5xx, VAPID refuse...) : a reessayer plus tard. */
        ECHEC
    }

    public record Envoi(Resultat resultat, String detail) {
    }

    private final WebPushProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public WebPushSender(WebPushProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build());
    }

    WebPushSender(WebPushProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public Envoi envoyer(WebPushSubscription abonnement, String messageJson) {
        if (!properties.isConfigured()) {
            return new Envoi(Resultat.ECHEC, "cles VAPID non configurees");
        }
        java.net.http.HttpRequest requete;
        try {
            requete = StandardHttpClientRequestPreparer.getBuilder()
                    .pushSubscription(versPushSubscription(abonnement))
                    .pushMessage(messageJson)
                    .ttl(properties.getTtlSeconds(), TimeUnit.SECONDS)
                    .urgencyNormal()
                    .vapidJWTExpiresAfter(12, TimeUnit.HOURS)
                    .vapidJWTSubject(properties.getVapid().getSubject())
                    .build(properties.keyPair())
                    .toRequestBuilder()
                    .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                    .build();
        } catch (RuntimeException e) {
            // Cles p256dh/auth du navigateur illisibles, endpoint mal forme : l'abonnement ne pourra jamais servir.
            return new Envoi(Resultat.INVALIDE, "abonnement inutilisable (" + e.getClass().getSimpleName() + ")");
        }

        try {
            HttpResponse<Void> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.discarding());
            int statut = reponse.statusCode();
            if (statut == 200 || statut == 201 || statut == 202) {
                return new Envoi(Resultat.ENVOYE, "HTTP " + statut);
            }
            if (statut == 404 || statut == 410) {
                return new Envoi(Resultat.INVALIDE, "HTTP " + statut + " (abonnement expire ou revoque)");
            }
            return new Envoi(Resultat.ECHEC, "HTTP " + statut);
        } catch (java.net.http.HttpTimeoutException e) {
            return new Envoi(Resultat.ECHEC, "timeout");
        } catch (IOException e) {
            return new Envoi(Resultat.ECHEC, "erreur reseau (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Envoi(Resultat.ECHEC, "interrompu");
        }
    }

    private static PushSubscription versPushSubscription(WebPushSubscription abonnement) {
        PushSubscription.Keys cles = new PushSubscription.Keys();
        cles.setP256dh(abonnement.getP256dh());
        cles.setAuth(abonnement.getAuth());
        PushSubscription subscription = new PushSubscription();
        subscription.setEndpoint(abonnement.getEndpoint());
        subscription.setExpirationTime(abonnement.getExpirationTime());
        subscription.setKeys(cles);
        return subscription;
    }
}
