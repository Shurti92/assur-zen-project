package ci.assurzen.metrics;

import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.UserSessionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

@Component
public class BusinessMetrics {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Abidjan");

    private final Counter paymentsSuccessful;
    private final Counter claimsDeclared;
    private final Timer subscriptionProcessingTimer;

    public BusinessMetrics(MeterRegistry registry, ContractRepository contractRepository, UserSessionRepository userSessionRepository) {
        Gauge.builder("contracts.created.today", contractRepository, repo -> {
                    Instant startOfDay = LocalDate.now(BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toInstant();
                    Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS);
                    return (double) repo.countByCreatedAtBetween(startOfDay, endOfDay);
                })
                .description("Nombre de contrats crees aujourd'hui")
                .register(registry);

        Gauge.builder("users.connected", userSessionRepository, repo -> (double) repo.countByRevokedFalse())
                .description("Nombre d'utilisateurs actuellement connectes (sessions actives non revoquees)")
                .register(registry);

        this.paymentsSuccessful = Counter.builder("payments.successful.total")
                .description("Nombre de paiements reussis (module Paiement a venir)")
                .register(registry);

        this.claimsDeclared = Counter.builder("claims.declared.total")
                .description("Nombre de sinistres declares")
                .register(registry);

        this.subscriptionProcessingTimer = Timer.builder("subscription.processing.time")
                .description("Temps de traitement d'une souscription (creation de contrat)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void incrementPaymentsSuccessful() {
        paymentsSuccessful.increment();
    }

    public void incrementClaimsDeclared() {
        claimsDeclared.increment();
    }

    public <T> T recordSubscriptionProcessing(Supplier<T> action) {
        return subscriptionProcessingTimer.record(action);
    }
}
