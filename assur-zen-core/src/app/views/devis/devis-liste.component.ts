import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { QuoteStatus, QuoteView } from '../../core/quotes/models/quote.model';
import { QuoteService } from '../../core/quotes/quote.service';

export const QUOTE_STATUS_LABEL: Record<QuoteStatus, string> = {
  CALCULE: 'Calculé',
  ACCEPTE: 'Accepté',
  EXPIRE: 'Expiré',
};

export const QUOTE_STATUS_VARIANT: Record<QuoteStatus, string> = {
  CALCULE: 'info',
  ACCEPTE: 'success',
  EXPIRE: 'secondary',
};

@Component({
  selector: 'app-devis-liste',
  templateUrl: './devis-liste.component.html',
  imports: [ButtonDirective, RouterLink, SpinnerComponent],
})
export class DevisListeComponent {
  private readonly service = inject(QuoteService);

  readonly pageSize = 20;
  readonly statusLabel = QUOTE_STATUS_LABEL;
  readonly statusVariant = QUOTE_STATUS_VARIANT;
  readonly statuses = Object.keys(QUOTE_STATUS_LABEL) as QuoteStatus[];

  readonly quotes = signal<QuoteView[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly status = signal<QuoteStatus | null>(null);
  readonly search = signal('');
  readonly pageIndex = signal(0);

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.quotes().length / this.pageSize)));
  readonly pageItems = computed(() => {
    const start = this.pageIndex() * this.pageSize;
    return this.quotes().slice(start, start + this.pageSize);
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list(this.status(), this.search()).subscribe({
      next: (list) => {
        this.quotes.set(list);
        this.pageIndex.set(0);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err));
        this.loading.set(false);
      },
    });
  }

  onStatusChange(value: string): void {
    this.status.set((value || null) as QuoteStatus | null);
    this.load();
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.load();
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
  }

  vehicleLabel(q: QuoteView): string {
    return `${q.vehicleType === 'MOTO' ? 'Moto' : 'Tricycle'} · ${q.brand}`;
  }

  formatDate(value?: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatAmount(value: number): string {
    return `${value.toLocaleString('fr-FR')} F`;
  }

  private messageOf(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 403) return 'Accès non autorisé.';
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return 'Impossible de charger les devis.';
  }
}
