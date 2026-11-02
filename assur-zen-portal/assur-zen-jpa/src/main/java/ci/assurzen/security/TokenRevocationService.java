package ci.assurzen.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

/**
 * Liste de revocation des ACCESS tokens (jti) - audit de securite pre-production, decision produit
 * du 2026-08-23 : jusqu'ici, un access token JWT restait valide jusqu'a sa propre expiration (1h par
 * defaut) meme apres /auth/logout ou un changement de mot de passe, la revocation existante
 * (UserSession.revoked) ne portant que sur le REFRESH token. In-memory (Caffeine, meme pattern deja
 * utilise ailleurs dans ce projet pour la config dynamique) plutot que persiste en base : la portee
 * est volontairement limitee a la duree de vie residuelle du token revoque (quelques minutes a 1h
 * maximum), donc un redemarrage du serveur qui viderait le cache ne reintroduit qu'une fenetre
 * d'exposition deja bornee et acceptee - pas besoin de survivre a un redemarrage pour ce cas d'usage.
 * Chaque entree expire EXACTEMENT quand le token qu'elle revoque aurait de toute facon expire
 * naturellement (Expiry personnalise), pas de purge manuelle necessaire.
 *
 * Limite connue : ne couvre que le logout/changement de mot de passe SELF-SERVICE (l'appelant a son
 * propre token en main). Un reset de mot de passe declenche par un ADMIN sur un AUTRE compte
 * (PATCH /users/{id}/reset-password) ne peut pas reveoquer les access tokens deja emis pour CE compte
 * (leur jti n'est pas connu de l'admin) - seul le refresh token (UserSession) est revoque dans ce cas,
 * comme avant. Accepte comme limite residuelle, hors perimetre de ce correctif.
 */
@Service
@Slf4j
public class TokenRevocationService {

    private final Cache<String, Instant> jtisRevoques = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfter(new Expiry<String, Instant>() {
                @Override
                public long expireAfterCreate(String jti, Instant expiresAt, long currentTime) {
                    return dureeRestante(expiresAt);
                }

                @Override
                public long expireAfterUpdate(String jti, Instant expiresAt, long currentTime, long currentDuration) {
                    return dureeRestante(expiresAt);
                }

                @Override
                public long expireAfterRead(String jti, Instant expiresAt, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                private long dureeRestante(Instant expiresAt) {
                    return Math.max(0, Duration.between(Instant.now(), expiresAt).toNanos());
                }
            })
            .build();

    public void revoquer(String jti, Instant expiresAt) {
        if (!StringUtils.hasText(jti) || expiresAt == null || expiresAt.isBefore(Instant.now())) {
            return;
        }
        jtisRevoques.put(jti, expiresAt);
        log.debug("Access token revoque (jti={})", jti);
    }

    public boolean estRevoque(String jti) {
        return StringUtils.hasText(jti) && jtisRevoques.getIfPresent(jti) != null;
    }
}
