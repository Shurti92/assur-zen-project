package ci.assurzen.sycapay.service;

import ci.assurzen.sycapay.client.SycaPayClient;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayAuthResponse;
import ci.assurzen.sycapay.exception.SycaPayAuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cache thread-safe du token SycaPay (section 7 de la specification). Duree de vie annoncee tres
 * courte et contradictoire par la documentation SycaPay elle-meme (40 a 60 secondes selon la page du
 * PDF - cf. SycaPayProperties.tokenTtlSeconds) : ce cache reste utile pour regrouper des appels
 * rapproches dans la meme operation metier, mais NE dispensera PAS la grande majorite des paiements
 * (espaces dans le temps) d'une reauthentification - c'est un comportement attendu, pas un defaut de
 * conception.
 *
 * Le token n'est necessaire QUE pour checktransferwave. checkBalance() et getStatus() n'en ont pas
 * besoin (verifie sur le contrat reel) - ce service n'est donc sollicite que par SycaPayPaymentService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SycaPayAuthenticationService {

    private final SycaPayClient sycaPayClient;
    private final SycaPayProperties properties;

    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    /** Ne journalise jamais le token lui-meme (uniquement sa duree de validite calculee). */
    public String getValidToken() {
        CachedToken current = cache.get();
        Instant now = Instant.now();
        if (current != null && current.expiresAt().isAfter(now)) {
            return current.token();
        }

        SycaPayAuthResponse response = sycaPayClient.authenticate();
        if (!response.isSuccess()) {
            throw new SycaPayAuthenticationException(
                    "Authentification SycaPay refusee (code=" + response.code() + ", description=" + response.description() + ")");
        }

        long ttl = Math.max(1, properties.getTokenTtlSeconds() - properties.getTokenExpiryMarginSeconds());
        CachedToken fresh = new CachedToken(response.token(), now.plusSeconds(ttl));
        cache.set(fresh);
        log.info("[SYCAPAY] nouveau token obtenu, valide jusqu'a {}", fresh.expiresAt());
        return fresh.token();
    }

    /** Invalide le cache : utilise quand SycaPay signale un token incorrect/expire (-19/-20) malgre un
     * cache local encore "valide" a nos yeux (horloge non synchronisee, marge insuffisante...). */
    public void invalidate() {
        cache.set(null);
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
