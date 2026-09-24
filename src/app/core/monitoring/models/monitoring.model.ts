export interface PrometheusSample {
  metric: string;
  labels: Record<string, string>;
  value: number;
}

export interface PrometheusMetricFamily {
  name: string;
  help: string;
  type: string;
  samples: PrometheusSample[];
}
