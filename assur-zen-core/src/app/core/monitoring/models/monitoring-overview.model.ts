export interface BusinessMetrics {
  contractsCreatedToday: number;
  paymentsSuccessful: number;
  claimsDeclared: number;
  usersConnected: number;
  avgSubscriptionProcessingSeconds: number;
  apiErrorRatePercent: number;
}

export interface ComponentHealth {
  status: string;
  details: Record<string, string>;
}

export interface HealthStatus {
  status: string;
  components: Record<string, ComponentHealth>;
}
