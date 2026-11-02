package ci.assurzen.services;

import ci.assurzen.dto.MonitoringHealthResponse;
import ci.assurzen.dto.MonitoringMetricsResponse;
import ci.assurzen.health.AuthHealthIndicator;
import ci.assurzen.health.GatewayHealthIndicator;
import ci.assurzen.health.PaiementHealthIndicator;
import ci.assurzen.health.SinistreHealthIndicator;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final GatewayHealthIndicator gatewayHealthIndicator;
    private final AuthHealthIndicator authHealthIndicator;
    private final PaiementHealthIndicator paiementHealthIndicator;
    private final SinistreHealthIndicator sinistreHealthIndicator;
    private final MeterRegistry meterRegistry;

    public MonitoringHealthResponse getHealth() {
        Map<String, MonitoringHealthResponse.ComponentHealth> components = new LinkedHashMap<>();
        components.put("gateway", toComponentHealth(gatewayHealthIndicator.health()));
        components.put("auth", toComponentHealth(authHealthIndicator.health()));
        components.put("paiement", toComponentHealth(paiementHealthIndicator.health()));
        components.put("sinistre", toComponentHealth(sinistreHealthIndicator.health()));

        boolean anyDown = components.values().stream().anyMatch(c -> "DOWN".equals(c.status()));
        String overall = anyDown ? "DOWN" : "UP";

        return new MonitoringHealthResponse(overall, components);
    }

    public MonitoringMetricsResponse getMetrics() {
        long contractsToday = (long) gaugeValue("contracts.created.today");
        long usersConnected = (long) gaugeValue("users.connected");
        long paymentsSuccessful = (long) counterValue("payments.successful.total");
        long claimsDeclared = (long) counterValue("claims.declared.total");
        double avgSubscriptionSeconds = timerMeanSeconds("subscription.processing.time");
        double apiErrorRatePercent = computeApiErrorRatePercent();

        return new MonitoringMetricsResponse(
                contractsToday,
                paymentsSuccessful,
                claimsDeclared,
                usersConnected,
                avgSubscriptionSeconds,
                apiErrorRatePercent
        );
    }

    private MonitoringHealthResponse.ComponentHealth toComponentHealth(Health health) {
        return new MonitoringHealthResponse.ComponentHealth(health.getStatus().getCode(), health.getDetails());
    }

    private double gaugeValue(String name) {
        var gauge = meterRegistry.find(name).gauge();
        return gauge == null ? 0.0 : gauge.value();
    }

    private double counterValue(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double timerMeanSeconds(String name) {
        Timer timer = meterRegistry.find(name).timer();
        return timer == null ? 0.0 : timer.mean(TimeUnit.SECONDS);
    }

    private double computeApiErrorRatePercent() {
        double total = 0;
        double serverErrors = 0;

        for (Timer timer : meterRegistry.find("http.server.requests").timers()) {
            double count = timer.count();
            total += count;
            Meter.Id id = timer.getId();
            String outcome = id.getTag("outcome");
            if ("SERVER_ERROR".equals(outcome)) {
                serverErrors += count;
            }
        }

        return total == 0 ? 0.0 : (serverErrors / total) * 100.0;
    }
}
