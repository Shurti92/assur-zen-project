import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SpinnerComponent } from '@coreui/angular';

import { QuoteStatus, QuoteView } from '../../core/quotes/models/quote.model';
import { formatCiPhone } from '../../core/quotes/ci-phone';
import { QUOTE_SMS_ENABLED } from '../../core/quotes/quote-sms.config';
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

type ProductFilter = 'moto' | 'automobile-vtc' | null;

@Component({
  selector: 'app-devis-liste',
  templateUrl: './devis-liste.component.html',
  styleUrl: './devis-liste.component.scss',
  imports: [RouterLink, SpinnerComponent],
})
export class DevisListeComponent {
  private readonly service = inject(QuoteService);

  readonly emailEnabled = QUOTE_EMAIL_ENABLED;
  readonly smsEnabled = QUOTE_SMS_ENABLED;

  readonly pageSize = 20;
  readonly statusLabel = QUOTE_STATUS_LABEL;
  readonly statusVariant = QUOTE_STATUS_VARIANT;

  /** Devis chargés (la recherche est faite côté serveur ; statut et produit côté écran). */
  readonly quotes = signal<QuoteView[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly status = signal<QuoteStatus | null>(null);
  readonly search = signal('');
  readonly pageIndex = signal(0);
  readonly product = signal<ProductFilter>(null);

  /** Compteurs cliquables : calculés sur les devis chargés, produit compris, statut exclu. */
  private readonly byProduct = computed(() => {
    const p = this.product();
    return p ? this.quotes().filter((q) => this.productOf(q) === p) : this.quotes();
  });
  readonly counts = computed(() => {
    const list = this.byProduct();
    const count = (s: QuoteStatus) => list.filter((q) => q.status === s).length;
    return { all: list.length, CALCULE: count('CALCULE'), ACCEPTE: count('ACCEPTE'), EXPIRE: count('EXPIRE') };
  });

  readonly filtered = computed(() => {
    const s = this.status();
    return s ? this.byProduct().filter((q) => q.status === s) : this.byProduct();
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
    this.service.list(null, this.search()).subscribe({
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

  /** Un clic sur le compteur déjà actif retire le filtre. */
  onStatusChange(value: QuoteStatus | null): void {
    this.status.set(value && value === this.status() ? null : value);
    this.pageIndex.set(0);
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.load();
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
  }

  onProductChange(value: ProductFilter): void {
    this.product.set(value);
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

  /** Initiales de l'avatar du prospect. */
  initials(q: QuoteView): string {
    const parts = (q.prospectName ?? '').trim().split(/\s+/).filter(Boolean);
    const out = parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : (parts[0] ?? '').slice(0, 2);
    return out.toUpperCase() || '?';
  }

  formatPhone(value?: string | null): string {
    return value ? formatCiPhone(value) : '—';
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
