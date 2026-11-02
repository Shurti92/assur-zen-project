package ci.assurzen.security;

import ci.assurzen.entities.User;
import ci.assurzen.repositories.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Ferme la fenetre d'exposition residuelle documentee dans TokenRevocationService (§ "Limite
 * connue") : jusqu'ici, un access token deja emis restait utilisable sur TOUS les endpoints
 * proteges jusqu'a sa propre expiration (1h par defaut) meme apres qu'un ADMIN desactive le compte
 * cible (PATCH /users/{id}/status, ou en cascade via Partenaire.accesActiviteCommerciale) - la
 * revocation par jti ne couvre que le logout/changement de mot de passe SELF-SERVICE, un admin
 * n'ayant pas connaissance du jti du token d'un AUTRE utilisateur (audit securite 2026-08-25).
 *
 * Meme pattern Caffeine que TokenRevocationService : cache in-memory borne dans le temps
 * (expireAfterWrite courte, filet de securite) PLUS invalidation EXPLICITE au moment meme de la
 * desactivation (UserProfileService), donnant un effet quasi immediat sans jamais imposer un aller
 * en base a chaque requete authentifiee en regime normal (cache hit).
 */
@Service
@RequiredArgsConstructor
public class UserActiveStatusCache {

    private final UserRepository userRepository;

    private final Cache<Long, Boolean> statutActif = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    public boolean isActive(Long userId) {
        Boolean cached = statutActif.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }
        boolean active = userRepository.findById(userId).map(User::isActive).orElse(false);
        statutActif.put(userId, active);
        return active;
    }

    /** Appele par UserProfileService a chaque changement de User.active, dans un sens comme dans l'autre. */
    public void invalidate(Long userId) {
        statutActif.invalidate(userId);
    }
}
