package ci.assurzen.dto;

public record MonitoringMetricsResponse(
        long contractsCreatedToday,
        long paymentsSuccessful,
        long claimsDeclared,
        long usersConnected,
        double avgSubscriptionProcessingSeconds,
        double apiErrorRatePercent
) {
}
