import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, ColComponent, RowComponent, SpinnerComponent } from '@coreui/angular';

import { MonitoringService } from '../../core/monitoring/monitoring.service';
import { parsePrometheusMetrics } from '../../core/monitoring/parse-prometheus-metrics';
import { PrometheusMetricFamily } from '../../core/monitoring/models/monitoring.model';

interface KpiCard {
  key: string;
  label: string;
  icon: string;
  color: string;
  value: string;
}

interface SampleRow {
  metric: string;
  type: string;
  labels: string;
  value: string;
}

@Component({
  selector: 'app-monitoring',
  templateUrl: './monitoring.component.html',
  styleUrl: './monitoring.component.scss',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, RowComponent, ColComponent],
})
export class MonitoringComponent {
  private readonly monitoringService = inject(MonitoringService);

  private readonly REFRESH_MS = 30_000;

  readonly families = signal<PrometheusMetricFamily[]>([]);
  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly error = signal<string | null>(null);
  readonly lastUpdated = signal<number | null>(null);
  readonly searchQuery = signal('');

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

  readonly relativeUpdate = computed(() => {
    const updated = this.lastUpdated();
    if (!updated) return null;
    const diffSeconds = Math.max(0, Math.round((this.now() - updated) / 1000));
    if (diffSeconds < 5) return "à l'instant";
    if (diffSeconds < 60) return `il y a ${diffSeconds} s`;
    return `il y a ${Math.round(diffSeconds / 60)} min`;
  });

  readonly businessKpis = computed<KpiCard[]>(() => {
    if (this.families().length === 0) return [];
    const contracts = this.sampleTotal('contracts_created_today') ?? 0;
    const payments = this.sampleTotal('payments_successful_total') ?? 0;
    const claims = this.sampleTotal('claims_declared_total') ?? 0;
    return [
      { key: 'contracts', label: "Contrats créés aujourd'hui", icon: 'cilDescription', color: 'primary', value: this.formatInt(contracts) },
      { key: 'payments', label: 'Paiements réussis', icon: 'cilCreditCard', color: 'success', value: this.formatInt(payments) },
      { key: 'claims', label: 'Sinistres déclarés', icon: 'cilWarning', color: 'warning', value: this.formatInt(claims) },
    ];
  });

  readonly healthKpis = computed<KpiCard[]>(() => {
    if (this.families().length === 0) return [];
    const cards: KpiCard[] = [];

    const uptime = this.sampleTotal('process_uptime_seconds');
    if (uptime !== null) {
      cards.push({ key: 'uptime', label: 'Disponibilité (uptime)', icon: 'cilSpeedometer', color: 'info', value: this.formatDuration(uptime) });
    }

    const cpu = this.sampleTotal('process_cpu_usage');
    if (cpu !== null) {
      cards.push({ key: 'cpu', label: 'Charge CPU', icon: 'cilChart', color: cpu > 0.8 ? 'danger' : 'primary', value: `${(cpu * 100).toFixed(1)} %` });
    }

    const heapUsed = this.sampleTotal('jvm_memory_used_bytes', (labels) => labels['area'] === 'heap');
    const heapMax = this.sampleTotal('jvm_memory_max_bytes', (labels, value) => labels['area'] === 'heap' && value > 0);
    if (heapUsed !== null && heapMax) {
      const ratio = heapUsed / heapMax;
      cards.push({ key: 'heap', label: 'Mémoire heap JVM', icon: 'cilLayers', color: ratio > 0.85 ? 'danger' : 'success', value: `${this.formatBytes(heapUsed)} / ${this.formatBytes(heapMax)}` });
    }

    const diskFree = this.sampleTotal('disk_free_bytes');
    const diskTotal = this.sampleTotal('disk_total_bytes');
    if (diskFree !== null && diskTotal) {
      cards.push({ key: 'disk', label: 'Espace disque libre', icon: 'cilSave', color: diskFree / diskTotal < 0.1 ? 'danger' : 'info', value: `${this.formatBytes(diskFree)} / ${this.formatBytes(diskTotal)}` });
    }

    const dbActive = this.sampleTotal('hikaricp_connections_active');
    const dbMax = this.sampleTotal('hikaricp_connections_max');
    if (dbActive !== null && dbMax) {
      cards.push({ key: 'db', label: 'Connexions base de données', icon: 'cilShareBoxed', color: 'primary', value: `${Math.round(dbActive)} / ${Math.round(dbMax)}` });
    }

    const httpTotal = this.sampleTotal('http_server_requests_seconds_count');
    if (httpTotal !== null) {
      const httpErrors = this.sampleTotal('http_server_requests_seconds_count', (labels) => Number(labels['status']) >= 400) ?? 0;
      const rate = httpTotal > 0 ? (httpErrors / httpTotal) * 100 : 0;
      cards.push({ key: 'http-error-rate', label: "Taux d'erreur HTTP", icon: 'cilReportSlash', color: rate > 5 ? 'danger' : 'success', value: `${rate.toFixed(2)} %` });
    }

    return cards;
  });

  private readonly flattenedSamples = computed<SampleRow[]>(() => {
    const rows: SampleRow[] = [];
    for (const family of this.families()) {
      for (const sample of family.samples) {
        rows.push({
          metric: sample.metric,
          type: family.type,
          labels: Object.entries(sample.labels)
            .map(([key, value]) => `${key}=${value}`)
            .join(', '),
          value: this.formatSampleValue(sample.value),
        });
      }
    }
    return rows.sort((a, b) => a.metric.localeCompare(b.metric) || a.labels.localeCompare(b.labels));
  });

  readonly filteredSamples = computed<SampleRow[]>(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const rows = this.flattenedSamples();
    if (!query) return rows;
    return rows.filter((row) => `${row.metric} ${row.labels}`.toLowerCase().includes(query));
  });

  readonly totalSamples = computed(() => this.flattenedSamples().length);

  fetch(silent = false): void {
    if (!silent) {
      this.loading.set(true);
    } else {
      this.refreshing.set(true);
    }
    this.error.set(null);

    this.monitoringService.getPrometheusMetrics().subscribe({
      next: (raw) => {
        this.families.set(parsePrometheusMetrics(raw));
        this.lastUpdated.set(Date.now());
        this.loading.set(false);
        this.refreshing.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les métriques Prometheus.');
        this.loading.set(false);
        this.refreshing.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  private sampleTotal(
    name: string,
    filter?: (labels: Record<string, string>, value: number) => boolean
  ): number | null {
    const family = this.families().find((f) => f.name === name);
    if (!family || family.samples.length === 0) return null;
    const samples = filter ? family.samples.filter((sample) => filter(sample.labels, sample.value)) : family.samples;
    if (samples.length === 0) return null;
    return samples.reduce((sum, sample) => sum + sample.value, 0);
  }

  private formatInt(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(Math.round(value));
  }

  private formatSampleValue(value: number): string {
    if (Number.isInteger(value)) {
      return new Intl.NumberFormat('fr-FR').format(value);
    }
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 4 }).format(value);
  }

  private formatDuration(seconds: number): string {
    const totalMinutes = Math.floor(seconds / 60);
    const days = Math.floor(totalMinutes / 1440);
    const hours = Math.floor((totalMinutes % 1440) / 60);
    const minutes = totalMinutes % 60;
    if (days > 0) return `${days} j ${hours} h`;
    if (hours > 0) return `${hours} h ${minutes} min`;
    return `${minutes} min`;
  }

  private formatBytes(bytes: number): string {
    if (bytes <= 0) return '0 Mo';
    const gb = bytes / 1024 ** 3;
    if (gb >= 1) return `${gb.toFixed(1)} Go`;
    return `${(bytes / 1024 ** 2).toFixed(0)} Mo`;
  }
}
