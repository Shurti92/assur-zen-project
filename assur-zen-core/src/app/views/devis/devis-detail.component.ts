import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { QuoteEmailHistoryItem } from '../../core/quotes/models/quote.model';
import { ciMobileOperator, CI_OPERATOR_LABEL, formatCiPhone } from '../../core/quotes/ci-phone';
import { QUOTE_ADDRESS_ENABLED, QUOTE_SMS_ENABLED } from '../../core/quotes/quote-sms.config';
import { QUOTE_EMAIL_ENABLED } from '../../core/quotes/quote-email.config';
import { QuoteView } from '../../core/quotes/models/quote.model';
import { QuoteService } from '../../core/quotes/quote.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { SendQuoteSmsDialogComponent } from './send-quote-sms-dialog.component';
import { SendQuoteEmailDialogComponent } from './send-quote-email-dialog.component';
import { QUOTE_STATUS_LABEL, QUOTE_STATUS_VARIANT } from './devis-liste.component';

@Component({
  selector: 'app-devis-detail',
  templateUrl: './devis-detail.component.html',
  imports: [AlertMessageComponent, ButtonDirective, RouterLink, SendQuoteEmailDialogComponent, SendQuoteSmsDialogComponent, SpinnerComponent],
})
export class DevisDetailComponent {
  private readonly service = inject(QuoteService);

  private readonly route = inject(ActivatedRoute);

  /** Paramètre de route `:reference`. */
  readonly reference = signal(this.route.snapshot.paramMap.get('reference') ?? '');

  readonly statusLabel = QUOTE_STATUS_LABEL;
  readonly statusVariant = QUOTE_STATUS_VARIANT;

  readonly emailEnabled = QUOTE_EMAIL_ENABLED;
  readonly emailDialogOpen = signal(false);

  /** Adresse et SMS : inactifs tant que la gateway n'a pas confirmé l'API (quote-sms.config.ts). */
  readonly addressEnabled = QUOTE_ADDRESS_ENABLED;
  readonly smsEnabled = QUOTE_SMS_ENABLED;
  readonly smsDialogOpen = signal(false);

  /** Historique complet des envois, chargé à l'ouverture du volet. */
  readonly historyOpen = signal(false);
  readonly history = signal<QuoteEmailHistoryItem[] | null>(null);
  readonly historyLoading = signal(false);
  readonly historyError = signal<string | null>(null);

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

  toggleHistory(): void {
    const open = !this.historyOpen();
    this.historyOpen.set(open);
    if (open && this.history() === null) this.loadHistory();
  }

  loadHistory(): void {
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.service.emailHistory(this.reference()).subscribe({
      next: (items) => {
        this.history.set(items);
        this.historyLoading.set(false);
      },
      error: () => {
        this.historyError.set("Impossible de charger l'historique des envois.");
        this.historyLoading.set(false);
      },
    });
  }

  onSmsSent(updated: QuoteView): void {
    this.quote.set(updated);
    this.smsDialogOpen.set(false);
    this.success.set(`SMS envoyé au ${formatCiPhone(updated.lastSmsTo ?? updated.prospectPhone)}.`);
  }

  smsCapable(q: QuoteView): boolean {
    return q.prospectPhoneSmsCapable ?? ciMobileOperator(q.prospectPhone) !== null;
  }

  operatorLabel(q: QuoteView): string {
    const op = q.prospectPhoneOperator ?? ciMobileOperator(q.prospectPhone);
    return op ? CI_OPERATOR_LABEL[op] : '';
  }

  formatPhone(value?: string | null): string {
    return formatCiPhone(value);
  }

  onEmailSent(updated: QuoteView): void {
    this.quote.set(updated);
    this.emailDialogOpen.set(false);
    // L'historique déjà affiché doit montrer le nouvel envoi ; sinon il sera chargé à l'ouverture.
    if (this.historyOpen()) this.loadHistory();
    else this.history.set(null);
    this.success.set(`Devis envoyé à ${updated.lastEmailTo ?? updated.prospectEmail ?? 'le prospect'}.`);
  }

  formatDateTime(value?: string | null): string {
    if (!value) return '—';
    const d = new Date(value);
    return `${d.toLocaleDateString('fr-FR')} à ${d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
  }

  energyLabel(energy?: string | null): string {
    const labels: Record<string, string> = { ESSENCE: 'Essence', DIESEL: 'Diesel', HYBRIDE: 'Hybride', ELECTRIQUE: 'Électrique' };
    return energy ? (labels[energy] ?? energy) : '—';
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
