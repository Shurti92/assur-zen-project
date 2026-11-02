package ci.assurzen.services;

import ci.assurzen.entities.DynamicConfiguration;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache en memoire (Caffeine) des DynamicConfiguration par cle. Instance unique de l'app -> pas de
 * synchronisation cross-instance necessaire (voir DynamicConfigurationService pour l'invalidation
 * immediate a chaque ecriture, qui est ce qui garantit "pas de redemarrage necessaire", pas le TTL
 * lui-meme : le TTL n'est qu'un filet de securite en cas d'ecriture SQL directe hors API).
 */
@Service
public class ConfigurationCacheService {

    @Value("${app.config.cache-ttl-seconds:300}")
    private long ttlSeconds;

    private Cache<String, DynamicConfiguration> cache;

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(2000)
                .build();
    }

    public Optional<DynamicConfiguration> get(String configKey) {
        return Optional.ofNullable(cache.getIfPresent(configKey));
    }

    public void put(String configKey, DynamicConfiguration configuration) {
        cache.put(configKey, configuration);
    }

    public void evict(String configKey) {
        cache.invalidate(configKey);
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    /** Evince puis recharge immediatement via le loader fourni par l'appelant (qui a acces au repository). */
    public DynamicConfiguration refresh(String configKey, Supplier<DynamicConfiguration> loader) {
        cache.invalidate(configKey);
        DynamicConfiguration fresh = loader.get();
        if (fresh != null) {
            cache.put(configKey, fresh);
        }
        return fresh;
    }
}
