package ci.assurzen.webpush.service;

import ci.assurzen.entities.User;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.service.AuthService;
import ci.assurzen.webpush.dto.PushPublicKeyResponse;
import ci.assurzen.webpush.dto.PushSubscriptionRequest;
import ci.assurzen.webpush.dto.PushSubscriptionResponse;
import ci.assurzen.webpush.entity.WebPushSubscription;
import ci.assurzen.webpush.repository.WebPushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Abonnements Web Push de l'utilisateur AUTHENTIFIE (2026-09-26) - l'utilisateur vient toujours du JWT,
 * jamais du payload : impossible d'abonner ou de desabonner le navigateur d'un autre compte.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebPushSubscriptionService {

    private final WebPushSubscriptionRepository repository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final WebPushNotificationService notificationService;

    /**
     * Cree ou met a jour l'abonnement de ce navigateur (endpoint) et le reactive. Un endpoint deja
     * enregistre par un AUTRE compte (navigateur partage, deconnexion/reconnexion) est rattache au compte
     * connecte : c'est lui qui recevra desormais les notifications sur ce navigateur.
     */
    @Transactional
    public PushSubscriptionResponse enregistrer(PushSubscriptionRequest requete, String userAgent) {
        User utilisateur = utilisateurConnecte();
        String hash = hash(requete.endpoint());
        WebPushSubscription abonnement = repository.findByEndpointHash(hash).orElseGet(WebPushSubscription::new);
        if (abonnement.getId() != null && !Objects.equals(abonnement.getUser().getId(), utilisateur.getId())) {
            log.info("[WEBPUSH] abonnement {} rattache a l'utilisateur {} (etait {})", abonnement.getId(),
                    utilisateur.getId(), abonnement.getUser().getId());
        }
        abonnement.setUser(utilisateur);
        abonnement.setEndpoint(requete.endpoint());
        abonnement.setEndpointHash(hash);
        abonnement.setP256dh(requete.keys().p256dh());
        abonnement.setAuth(requete.keys().auth());
        abonnement.setExpirationTime(requete.expirationTime());
        abonnement.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length())));
        abonnement.setActive(true);
        abonnement.setFailureCount(0);
        WebPushSubscription saved = repository.save(abonnement);
        return new PushSubscriptionResponse(saved.getId(), saved.isActive(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    /** Desactive l'abonnement de ce navigateur s'il appartient a l'utilisateur connecte ; sans effet
     * (et sans reveler son existence) sinon - reponse identique dans les deux cas. */
    @Transactional
    public void supprimer(String endpoint) {
        Long userId = authService.currentPrincipal().userId();
        repository.findByEndpointHash(hash(endpoint))
                .filter(abonnement -> Objects.equals(abonnement.getUser().getId(), userId))
                .ifPresent(abonnement -> {
                    abonnement.setActive(false);
                    repository.save(abonnement);
                });
    }

    public PushPublicKeyResponse clePublique() {
        return notificationService.estActif()
                ? new PushPublicKeyResponse(true, notificationService.clePublique())
                : new PushPublicKeyResponse(false, null);
    }

    private User utilisateurConnecte() {
        Long userId = authService.currentPrincipal().userId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }

    static String hash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
