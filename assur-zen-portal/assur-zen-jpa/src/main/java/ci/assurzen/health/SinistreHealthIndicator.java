package ci.assurzen.health;

import ci.assurzen.repositories.SinistreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("sinistre")
@RequiredArgsConstructor
public class SinistreHealthIndicator implements HealthIndicator {

    private final SinistreRepository sinistreRepository;

    @Override
    public Health health() {
        try {
            long count = sinistreRepository.count();
            return Health.up()
                    .withDetail("sinistresCount", count)
                    .withDetail("message", "Module Sinistre operationnel")
                    .build();
        } catch (RuntimeException e) {
            return Health.down()
                    .withDetail("message", "Base sinistres injoignable")
                    .withException(e)
                    .build();
        }
    }
}
