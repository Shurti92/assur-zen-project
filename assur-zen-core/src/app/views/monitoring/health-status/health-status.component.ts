import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, ColComponent, RowComponent, SpinnerComponent } from '@coreui/angular';

import { MonitoringService } from '../../../core/monitoring/monitoring.service';
import { HealthStatus } from '../../../core/monitoring/models/monitoring-overview.model';

type StatusVariant = 'success' | 'danger' | 'neutral';

interface HealthCard {
  name: string;
  status: string;
  variant: StatusVariant;
  icon: string;
  details: [string, string][];
}

const COMPONENT_ICON: Record<string, string> = {
  gateway: 'cilShareBoxed',
  auth: 'cilLockLocked',
  paiement: 'cilCreditCard',
  payment: 'cilCreditCard',
  sinistre: 'cilWarning',
  claim: 'cilWarning',
};

function statusVariant(status: string | undefined): StatusVariant {
  const normalized = (status ?? '').toUpperCase();
  if (normalized === 'UP') return 'success';
  if (normalized === 'DOWN' || normalized === 'OUT_OF_SERVICE') return 'danger';
  return 'neutral';
}

function componentIcon(name: string): string {
  const key = Object.keys(COMPONENT_ICON).find((candidate) => name.toLowerCase().includes(candidate));
  return key ? COMPONENT_ICON[key] : 'cilSettings';
}

@Component({
  selector: 'app-health-status',
  templateUrl: './health-status.component.html',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, RowComponent, ColComponent],
})
export class HealthStatusComponent {
  private readonly monitoringService = inject(MonitoringService);

  private readonly REFRESH_MS = 30_000;

  readonly health = signal<HealthStatus | null>(null);
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

  readonly overallVariant = computed<StatusVariant>(() => statusVariant(this.health()?.status));

  readonly components = computed<HealthCard[]>(() => {
    const h = this.health();
    if (!h) return [];
    return Object.entries(h.components).map(([name, comp]) => ({
      name,
      status: comp.status,
      variant: statusVariant(comp.status),
      icon: componentIcon(name),
      details: Object.entries(comp.details ?? {}),
    }));
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

    this.monitoringService.getHealthStatus().subscribe({
      next: (health) => {
        this.health.set(health);
        this.lastUpdated.set(Date.now());
        this.loading.set(false);
        this.refreshing.set(false);
      },
      error: () => {
        this.error.set("Impossible de charger l'état technique.");
        this.loading.set(false);
        this.refreshing.set(false);
      },
    });
  }

  variantLabel(variant: StatusVariant): string {
    return variant === 'success' ? 'Opérationnel' : variant === 'danger' ? 'Indisponible' : 'Inconnu';
  }
}
