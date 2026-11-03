import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { QuoteStatus, QuoteView } from '../../core/quotes/models/quote.model';
import { QUOTE_EMAIL_ENABLED } from '../../core/quotes/quote-email.config';
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

  readonly emailEnabled = QUOTE_EMAIL_ENABLED;

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

  /** Filtre produit côté écran (l'API renvoie moto et VTC ensemble, productKey les distingue). */
  readonly product = signal<'moto' | 'automobile-vtc' | null>(null);
  readonly filtered = computed(() => {
    const p = this.product();
    return p ? this.quotes().filter((q) => this.productOf(q) === p) : this.quotes();
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filtered().length / this.pageSize)));
  readonly pageItems = computed(() => {
    const start = this.pageIndex() * this.pageSize;
    return this.filtered().slice(start, start + this.pageSize);
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

  onProductChange(value: string): void {
    this.product.set((value || null) as 'moto' | 'automobile-vtc' | null);
    this.pageIndex.set(0);
  }

  productOf(q: QuoteView): 'moto' | 'automobile-vtc' {
    return q.productKey === 'automobile-vtc' ? 'automobile-vtc' : 'moto';
  }

  productLabel(q: QuoteView): string {
    return this.productOf(q) === 'automobile-vtc' ? 'VTC' : 'Moto';
  }

  vehicleLabel(q: QuoteView): string {
    if (this.productOf(q) === 'automobile-vtc') return `${q.brand} ${q.model}`.trim();
    return `${q.vehicleType === 'TRICYCLE' ? 'Tricycle' : 'Moto'} · ${q.brand}`;
  }

  formatDayMonth(value?: string | null): string {
    if (!value) return '';
    const d = new Date(value);
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
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
