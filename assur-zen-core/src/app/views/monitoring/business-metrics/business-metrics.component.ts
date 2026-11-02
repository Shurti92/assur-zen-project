import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, ColComponent, RowComponent, SpinnerComponent } from '@coreui/angular';

import { MonitoringService } from '../../../core/monitoring/monitoring.service';
import { BusinessMetrics } from '../../../core/monitoring/models/monitoring-overview.model';

interface KpiCard {
  key: string;
  label: string;
  icon: string;
  color: string;
  value: string;
}

@Component({
  selector: 'app-business-metrics',
  templateUrl: './business-metrics.component.html',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, RowComponent, ColComponent],
})
export class BusinessMetricsComponent {
  private readonly monitoringService = inject(MonitoringService);

  private readonly REFRESH_MS = 30_000;

  readonly metrics = signal<BusinessMetrics | null>(null);
  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly error = signal<string | null>(null);
  readonly lastUpdated = signal<number | null>(null);

  /** Ticks every second so relativeUpdate() reads a stable value within a single
   * change-detection pass — see AuditComponent for why Date.now() can't be called
   * directly from the template. */
  private readonly now = signal(Date.now());

  constructor() {
    const tickId = setInterval(() => this.now.set(Date.now()), 1000);
    const refreshId = setInterval(() => this.fetch(true), this.REFRESH_MS);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(tickId);
      clearInterval(refreshId);
    });
    this.fetch();
  }

  readonly volumeKpis = computed<KpiCard[]>(() => {
    const m = this.metrics();
    if (!m) return [];
    return [
      { key: 'contracts', label: "Contrats créés aujourd'hui", icon: 'cilDescription', color: 'primary', value: this.formatInt(m.contractsCreatedToday) },
      { key: 'payments', label: 'Paiements réussis', icon: 'cilCreditCard', color: 'success', value: this.formatInt(m.paymentsSuccessful) },
      { key: 'claims', label: 'Sinistres déclarés', icon: 'cilWarning', color: 'warning', value: this.formatInt(m.claimsDeclared) },
      { key: 'users', label: 'Utilisateurs connectés', icon: 'cilPeople', color: 'info', value: this.formatInt(m.usersConnected) },
    ];
  });

  readonly performanceKpis = computed<KpiCard[]>(() => {
    const m = this.metrics();
    if (!m) return [];
    return [
      { key: 'processing-time', label: 'Délai moyen de souscription', icon: 'cilSpeedometer', color: 'primary', value: `${m.avgSubscriptionProcessingSeconds.toFixed(2)} s` },
      { key: 'error-rate', label: "Taux d'erreur API", icon: 'cilReportSlash', color: m.apiErrorRatePercent > 5 ? 'danger' : 'success', value: `${m.apiErrorRatePercent.toFixed(2)} %` },
    ];
  });

  readonly relativeUpdate = computed(() => {
    const updated = this.lastUpdated();
    if (!updated) return null;
    const diffSeconds = Math.max(0, Math.round((this.now() - updated) / 1000));
    if (diffSeconds < 5) return "à l'instant";
    if (diffSeconds < 60) return `il y a ${diffSeconds} s`;
    return `il y a ${Math.round(diffSeconds / 60)} min`;
  });

  fetch(silent = false): void {
    if (!silent) {
      this.loading.set(true);
    } else {
      this.refreshing.set(true);
    }
    this.error.set(null);

    this.monitoringService.getBusinessMetrics().subscribe({
      next: (metrics) => {
        this.metrics.set(metrics);
        this.lastUpdated.set(Date.now());
        this.loading.set(false);
        this.refreshing.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les indicateurs métier.');
        this.loading.set(false);
        this.refreshing.set(false);
      },
    });
  }

  private formatInt(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(Math.round(value));
  }
}
