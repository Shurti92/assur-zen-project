package ci.assurzen.webpush.service;

import ci.assurzen.entities.User;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.webpush.config.WebPushProperties;
import ci.assurzen.webpush.dto.WebPushMessage;
import ci.assurzen.webpush.entity.WebPushSubscription;
import ci.assurzen.webpush.repository.WebPushSubscriptionRepository;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Envoi de notifications Web Push (2026-09-26) : a un utilisateur, a plusieurs, a un role ou a un
 * partenaire (son compte de connexion Partenaire.profile.user), sur TOUS leurs appareils actifs.
 * Appele depuis des traitements asynchrones apres commit (jamais depuis un controller) : un echec
 * d'envoi n'annule jamais l'operation metier. Chaque methode renvoie le nombre d'appareils atteints.
 * Interrupteur sans redeploiement : cle dynamique WEBPUSH_ENABLED (defaut true, si les cles VAPID sont
 * configurees).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebPushNotificationService {

    static final String CLE_ACTIVATION = "WEBPUSH_ENABLED";
    private static final Gson GSON = new Gson();

    private final WebPushProperties properties;
    private final WebPushSender sender;
    private final WebPushSubscriptionRepository repository;
    private final UserRepository userRepository;
    private final PartenaireRepository partenaireRepository;
    private final DynamicConfigurationService dynamicConfigurationService;

    public boolean estActif() {
        return properties.isConfigured() && Boolean.TRUE.equals(dynamicConfigurationService.getBoolean(CLE_ACTIVATION, true));
    }

    String clePublique() {
        return properties.keyPair().extractPublicKeyInUncompressedFormAsString();
    }

    /** Vrai si l'utilisateur recevrait un push maintenant (push actif et au moins un appareil abonne). */
    public boolean peutRecevoir(Long userId) {
        return userId != null && estActif() && repository.existsByUserIdAndActiveTrue(userId);
    }

    public int sendToUser(Long userId, WebPushMessage message) {
        return userId == null ? 0 : envoyer(repository.findByUserIdAndActiveTrue(userId), message);
    }

    public int sendToUsers(Collection<Long> userIds, WebPushMessage message) {
        return userIds == null || userIds.isEmpty() ? 0 : envoyer(repository.findByUserIdInAndActiveTrue(userIds), message);
    }

    /** Tous les comptes dont User.roles contient ce role (ex. "ADMIN", "COMMERCIAL"). */
    public int sendToRole(String role, WebPushMessage message) {
        String cible = role.toUpperCase(Locale.ROOT);
        List<Long> userIds = userRepository.findAll().stream()
                .filter(user -> user.getRoles() != null && List.of(user.getRoles().toUpperCase(Locale.ROOT).split("\\s*,\\s*")).contains(cible))
                .map(User::getId)
                .toList();
        return sendToUsers(userIds, message);
    }

    public int sendToPartner(Long partenaireId, WebPushMessage message) {
        return sendToUser(userIdDuPartenaire(partenaireId).orElse(null), message);
    }

    /** Compte de connexion du partenaire (Partenaire.profile.user), s'il en a un. */
    public Optional<Long> userIdDuPartenaire(Long partenaireId) {
        return partenaireId == null ? Optional.empty() : partenaireRepository.findUserIdById(partenaireId);
    }

    private int envoyer(List<WebPushSubscription> abonnements, WebPushMessage message) {
        if (!estActif() || abonnements.isEmpty()) {
            return 0;
        }
        String json = GSON.toJson(message);
        int atteints = 0;
        for (WebPushSubscription abonnement : abonnements) {
            WebPushSender.Envoi envoi = sender.envoyer(abonnement, json);
            switch (envoi.resultat()) {
                case ENVOYE -> {
                    atteints++;
                    abonnement.setFailureCount(0);
                    abonnement.setLastUsedAt(Instant.now());
                }
                case INVALIDE -> {
                    abonnement.setActive(false);
                    log.info("[WEBPUSH] abonnement {} (utilisateur {}) desactive : {}", abonnement.getId(),
                            abonnement.getUser().getId(), envoi.detail());
                }
                case ECHEC -> {
                    abonnement.setFailureCount(abonnement.getFailureCount() + 1);
                    if (abonnement.getFailureCount() >= properties.getMaxFailures()) {
                        abonnement.setActive(false);
                    }
                    log.warn("[WEBPUSH] echec d'envoi a l'abonnement {} (utilisateur {}, echec n°{}) : {}", abonnement.getId(),
                            abonnement.getUser().getId(), abonnement.getFailureCount(), envoi.detail());
                }
            }
            repository.save(abonnement);
        }
        log.info("[WEBPUSH] notification \"{}\" : {}/{} appareil(s) atteint(s)", message.title(), atteints, abonnements.size());
        return atteints;
    }
}
