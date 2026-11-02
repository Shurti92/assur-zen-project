package ci.assurzen.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("paiement")
public class PaiementHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return Health.unknown()
                .withDetail("message", "Module Paiement non encore implemente")
                .build();
    }
}
