import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { AuditService } from '../../core/audit/audit.service';
import { AuditEntry } from './audit.model';

type StatusBucket = 'success' | 'client-error' | 'server-error' | 'unknown';
type BucketFilter = 'all' | StatusBucket;

const BUCKET_LABEL: Record<StatusBucket, string> = {
  success: 'Succès',
  'client-error': 'Erreur client',
  'server-error': 'Erreur serveur',
  unknown: 'Sans statut',
};

const BUCKET_VARIANT: Record<StatusBucket, string> = {
  success: 'success',
  'client-error': 'warning',
  'server-error': 'danger',
  unknown: 'neutral',
};

const BUCKET_ORDER: StatusBucket[] = ['server-error', 'client-error', 'success', 'unknown'];

function statusBucket(statusCode: number | null): StatusBucket {
  if (statusCode === null || statusCode === undefined) {
    return 'unknown';
  }
  if (statusCode >= 500) {
    return 'server-error';
  }
  if (statusCode >= 400) {
    return 'client-error';
  }
  return 'success';
}

@Component({
  selector: 'app-audit',
  templateUrl: './audit.component.html',
  imports: [ButtonDirective, SpinnerComponent],
})
export class AuditComponent {
  private readonly auditService = inject(AuditService);

  readonly pageSize = 25;

  readonly entries = signal<AuditEntry[]>([]);
  readonly pageIndex = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly activeBucket = signal<BucketFilter>('all');

  /** Ticks periodically so relativeTime() reads a stable value within any single
   * change-detection pass — see LogTableComponent for why Date.now() can't be
   * called directly from the template. */
  private readonly now = signal(Date.now());

  constructor() {
    const intervalId = setInterval(() => this.now.set(Date.now()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(intervalId));
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auditService.list(this.pageIndex(), this.pageSize).subscribe({
      next: (result) => {
        this.entries.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("Impossible de charger le journal d'audit.");
        this.loading.set(false);
      },
    });
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) {
      return;
    }
    this.pageIndex.set(index);
    this.fetch();
  }

  readonly counts = computed(() => {
    const totals: Record<StatusBucket, number> = { success: 0, 'client-error': 0, 'server-error': 0, unknown: 0 };
    for (const entry of this.entries()) {
      totals[statusBucket(entry.statusCode)]++;
    }
    return totals;
  });

  readonly bucketPills = computed(() => {
    const counts = this.counts();
    return BUCKET_ORDER.filter((bucket) => counts[bucket] > 0).map((bucket) => ({
      bucket,
      label: BUCKET_LABEL[bucket],
      count: counts[bucket],
    }));
  });

  readonly period = computed(() => {
    const entries = this.entries();
    if (entries.length === 0) {
      return null;
    }
    const timestamps = entries.map((entry) => new Date(entry.creation).getTime()).sort((a, b) => a - b);
    const format = (ms: number) => new Date(ms).toLocaleString('fr-FR');
    const first = timestamps[0];
    const last = timestamps[timestamps.length - 1];
    return first === last ? format(first) : `${format(first)} – ${format(last)}`;
  });

  readonly filteredEntries = computed(() => {
    const bucket = this.activeBucket();
    const query = this.searchQuery().trim().toLowerCase();
    return this.entries()
      .filter((entry) => bucket === 'all' || statusBucket(entry.statusCode) === bucket)
      .filter((entry) => {
        if (!query) {
          return true;
        }
        const haystack = [entry.username, entry.route, entry.operation, entry.numeroContrat, entry.exceptionMessage]
          .filter((value): value is string => !!value)
          .join(' ')
          .toLowerCase();
        return haystack.includes(query);
      })
      .slice()
      .reverse();
  });

  setBucket(bucket: BucketFilter): void {
    this.activeBucket.set(bucket);
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  bucketLabel(bucket: StatusBucket): string {
    return BUCKET_LABEL[bucket];
  }

  bucketVariant(bucket: StatusBucket): string {
    return BUCKET_VARIANT[bucket];
  }

  entryBucket(entry: AuditEntry): StatusBucket {
    return statusBucket(entry.statusCode);
  }

  rowClass(entry: AuditEntry): string {
    const bucket = statusBucket(entry.statusCode);
    return bucket === 'server-error' ? 'dl-row--danger' : bucket === 'client-error' ? 'dl-row--warning' : '';
  }

  formatTimestamp(timestamp: string): string {
    return new Date(timestamp).toLocaleTimeString('fr-FR');
  }

  relativeTime(timestamp: string): string {
    const diffMs = this.now() - new Date(timestamp).getTime();
    const minutes = Math.round(diffMs / 60000);
    if (minutes <= 0) {
      return "à l'instant";
    }
    if (minutes < 60) {
      return `il y a ${minutes} min`;
    }
    const hours = Math.round(minutes / 60);
    if (hours < 24) {
      return `il y a ${hours} h`;
    }
    return `il y a ${Math.round(hours / 24)} j`;
  }

  formatDuration(ms: number): string {
    return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(2)} s`;
  }

  export(): void {
    const total = this.totalElements();
    if (total === 0) {
      return;
    }
    // Export always pulls the full audit trail (not just the current page): a single
    // request for `total` rows, since the export is only as useful as it is complete.
    this.exporting.set(true);
    this.auditService.list(0, total).subscribe({
      next: (result) => {
        this.downloadCsv(result.content);
        this.exporting.set(false);
      },
      error: () => {
        this.error.set("Impossible d'exporter le journal d'audit.");
        this.exporting.set(false);
      },
    });
  }

  private downloadCsv(entries: AuditEntry[]): void {
    const escape = (value: string) => `"${value.replace(/"/g, '""')}"`;
    const columns: (keyof AuditEntry)[] = [
      'id', 'code', 'creation', 'direction', 'statusCode', 'durationMs', 'username',
      'userId', 'route', 'operation', 'numeroContrat', 'ipAddress', 'exceptionMessage', 'flux',
    ];
    const header = columns.map(escape).join(',');
    const rows = entries.map((entry) =>
      columns.map((column) => escape(entry[column] === null || entry[column] === undefined ? '' : String(entry[column]))).join(',')
    );
    const csv = [header, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `assurzen-audit-${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }
}
