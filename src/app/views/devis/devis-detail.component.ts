import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { QuoteView } from '../../core/quotes/models/quote.model';
import { QuoteService } from '../../core/quotes/quote.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { QUOTE_STATUS_LABEL, QUOTE_STATUS_VARIANT } from './devis-liste.component';

@Component({
  selector: 'app-devis-detail',
  templateUrl: './devis-detail.component.html',
  imports: [AlertMessageComponent, ButtonDirective, RouterLink, SpinnerComponent],
})
export class DevisDetailComponent {
  private readonly service = inject(QuoteService);

  private readonly route = inject(ActivatedRoute);

  /** Paramètre de route `:reference`. */
  readonly reference = signal(this.route.snapshot.paramMap.get('reference') ?? '');

  readonly statusLabel = QUOTE_STATUS_LABEL;
  readonly statusVariant = QUOTE_STATUS_VARIANT;

  readonly quote = signal<QuoteView | null>(null);
  readonly loading = signal(true);
  readonly accepting = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.get(this.reference()).subscribe({
      next: (q) => {
        this.quote.set(q);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger le devis.'));
        this.loading.set(false);
      },
    });
  }

  accept(): void {
    const q = this.quote();
    if (!q?.canAccept || this.accepting()) return;
    this.accepting.set(true);
    this.error.set(null);
    this.success.set(null);
    this.service.accept(q.reference).subscribe({
      next: (updated) => {
        this.quote.set(updated);
        this.accepting.set(false);
        this.success.set('Devis marqué comme accepté.');
      },
      error: (err) => {
        this.accepting.set(false);
        if (err instanceof HttpErrorResponse && err.error?.code === 'QUOTE_EXPIRED') {
          this.error.set('Devis expiré : il ne peut plus être accepté.');
          this.load();
          return;
        }
        this.error.set(this.messageOf(err, "Impossible d'enregistrer l'accord du prospect."));
      },
    });
  }

  formatDate(value?: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatAmount(value: number): string {
    return `${value.toLocaleString('fr-FR')} F`;
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 404) return 'Devis introuvable.';
      if (err.status === 403) return 'Accès non autorisé.';
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return fallback;
  }
}
