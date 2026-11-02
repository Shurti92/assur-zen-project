package ci.assurzen.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component("gateway")
public class GatewayHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        return Health.up()
                .withDetail("uptimeSeconds", uptimeMs / 1000)
                .withDetail("message", "Gateway operationnel")
                .build();
    }
}
