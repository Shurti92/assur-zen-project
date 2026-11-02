import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { ConfirmDialogComponent } from '../confirm-dialog/confirm-dialog.component';
import { LogTableEntry, LogTableLevel } from './log-table.model';

const PILL_LABEL: Record<LogTableLevel, string> = {
  debug: 'Debug',
  info: 'Info',
  warn: 'Avertissement',
  error: 'Erreur',
};

const CHIP_LABEL: Record<LogTableLevel, string> = {
  debug: 'Debug',
  info: 'Info',
  warn: 'Avert.',
  error: 'Erreur',
};

const LEVEL_VARIANT: Record<LogTableLevel, string> = {
  debug: 'neutral',
  info: 'info',
  warn: 'warning',
  error: 'danger',
};

const LEVEL_ORDER: LogTableLevel[] = ['error', 'warn', 'info', 'debug'];

type LevelFilter = 'all' | LogTableLevel;

@Component({
  selector: 'app-log-table',
  templateUrl: './log-table.component.html',
  imports: [ButtonDirective, SpinnerComponent, ConfirmDialogComponent],
})
export class LogTableComponent {
  readonly title = input.required<string>();
  readonly entries = input.required<LogTableEntry[]>();
  readonly sourceLabel = input('Catégorie');
  readonly filenamePrefix = input('logs');
  readonly showLive = input(true);
  readonly showClear = input(false);
  readonly showRefresh = input(false);
  readonly refreshing = input(false);
  readonly error = input<string | null>(null);
  readonly emptyMessage = input('Aucun log pour le moment.');
  readonly footerNote = input<string | null>(null);

  readonly cleared = output<void>();
  readonly refresh = output<void>();

  readonly searchQuery = signal('');
  readonly activeLevel = signal<LevelFilter>('all');
  readonly clearDialogVisible = signal(false);

  /** Ticks periodically so relativeTime() reads a stable value within any single
   * change-detection pass (calling Date.now() directly in the template would trip
   * Angular's ExpressionChangedAfterItHasBeenCheckedError once real time crosses a
   * minute/hour boundary between the check and re-check passes). */
  private readonly now = signal(Date.now());

  constructor() {
    const intervalId = setInterval(() => this.now.set(Date.now()), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(intervalId));
  }

  readonly counts = computed(() => {
    const totals: Record<LogTableLevel, number> = { debug: 0, info: 0, warn: 0, error: 0 };
    for (const entry of this.entries()) {
      totals[entry.level]++;
    }
    return totals;
  });

  readonly levelPills = computed(() => {
    const counts = this.counts();
    return LEVEL_ORDER.filter((level) => counts[level] > 0).map((level) => ({
      level,
      label: PILL_LABEL[level],
      count: counts[level],
    }));
  });

  readonly period = computed(() => {
    const entries = this.entries();
    if (entries.length === 0) {
      return null;
    }
    const timestamps = entries.map((entry) => new Date(entry.timestamp).getTime()).sort((a, b) => a - b);
    const format = (ms: number) => new Date(ms).toLocaleString('fr-FR');
    const first = timestamps[0];
    const last = timestamps[timestamps.length - 1];
    return first === last ? format(first) : `${format(first)} – ${format(last)}`;
  });

  readonly filteredEntries = computed(() => {
    const level = this.activeLevel();
    const query = this.searchQuery().trim().toLowerCase();
    return this.entries()
      .filter((entry) => level === 'all' || entry.level === level)
      .filter((entry) => !query || `${entry.message} ${entry.source}`.toLowerCase().includes(query))
      .slice()
      .reverse();
  });

  setLevel(level: LevelFilter): void {
    this.activeLevel.set(level);
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  chipLabel(level: LogTableLevel): string {
    return CHIP_LABEL[level];
  }

  chipVariant(level: LogTableLevel): string {
    return LEVEL_VARIANT[level];
  }

  rowClass(level: LogTableLevel): string {
    const variant = LEVEL_VARIANT[level];
    return variant === 'danger' || variant === 'warning' ? `dl-row--${variant}` : '';
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

  requestClear(): void {
    this.clearDialogVisible.set(true);
  }

  onClearConfirmed(): void {
    this.clearDialogVisible.set(false);
    this.cleared.emit();
  }

  onClearCancelled(): void {
    this.clearDialogVisible.set(false);
  }

  onRefreshClick(): void {
    this.refresh.emit();
  }

  export(): void {
    const text = this.entries()
      .map((entry) => {
        const line = `[${entry.timestamp}] [${entry.level.toUpperCase()}] [${entry.source}] ${entry.message}`;
        return entry.details ? `${line}\n    ${entry.details}` : line;
      })
      .join('\n');
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${this.filenamePrefix()}-${new Date().toISOString().replace(/[:.]/g, '-')}.log`;
    link.click();
    URL.revokeObjectURL(url);
  }
}
