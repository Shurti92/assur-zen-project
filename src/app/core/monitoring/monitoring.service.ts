import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { BusinessMetrics, HealthStatus } from './models/monitoring-overview.model';

@Injectable({ providedIn: 'root' })
export class MonitoringService {
  constructor(private readonly http: HttpClient) {}

  /** Raw OpenMetrics/Prometheus text exposition format — see parse-prometheus-metrics.ts. */
  getPrometheusMetrics(): Observable<string> {
    return this.http.get(`${environment.actuatorUrl}/prometheus`, { responseType: 'text' });
  }

  /** Business KPIs (contracts, payments, claims, connected users…) for the "Indicateurs métier" page. */
  getBusinessMetrics(): Observable<BusinessMetrics> {
    return this.http.get<BusinessMetrics>('/monitoring/metrics');
  }

  /** Per-component technical health (Gateway, Auth, Paiement, Sinistre…) for the "État technique" page. */
  getHealthStatus(): Observable<HealthStatus> {
    return this.http.get<HealthStatus>('/monitoring/health');
  }
}
