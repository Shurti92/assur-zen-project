import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { SpinnerComponent } from '@coreui/angular';

import { formatCiPhone } from '../../core/quotes/ci-phone';
import { QuoteView } from '../../core/quotes/models/quote.model';
import { QuoteService } from '../../core/quotes/quote.service';

const ERROR_BY_CODE: Record<string, string> = {
  QUOTE_EXPIRED: 'Devis expiré : il ne peut plus être envoyé.',
  SMS_SEND_FAILED: "L'envoi du SMS a échoué. Réessayez dans un instant.",
  SMS_DISABLED: "L'envoi de SMS est désactivé sur ce serveur.",
  TOO_MANY_REQUESTS: "Trop de SMS pour ce devis aujourd'hui. Réessayez plus tard.",
};

const LINK_VALIDITY_DAYS = 14;

/** Fenêtre « Envoyer le lien du devis par SMS » : numéro du prospect, aperçu du SMS, lien valable 14 jours. */
@Component({
  selector: 'app-send-quote-sms-dialog',
  templateUrl: './send-quote-sms-dialog.component.html',
  styleUrl: './send-quote-email-dialog.component.scss',
  imports: [SpinnerComponent],
})
export class SendQuoteSmsDialogComponent {
  private readonly service = inject(QuoteService);

  readonly quote = input.required<QuoteView>();
  /** Émis avec le devis mis à jour après un envoi réussi. */
  readonly sent = output<QuoteView>();
  readonly closed = output<void>();

  readonly sending = signal(false);
  readonly error = signal<string | null>(null);

  readonly phone = computed(() => formatCiPhone(this.quote().prospectPhone));
  /** Date d'expiration du lien affichée dans l'aperçu : envoi + 14 jours. */
  readonly linkExpiry = computed(() => {
    const d = new Date();
    d.setDate(d.getDate() + LINK_VALIDITY_DAYS);
    return d.toLocaleDateString('fr-FR');
  });

  formatAmount(value: number): string {
    return value.toLocaleString('fr-FR');
  }

  send(): void {
    if (this.sending()) return;
    this.sending.set(true);
    this.error.set(null);
    this.service.sendSms(this.quote().reference).subscribe({
      next: (q) => {
        this.sending.set(false);
        this.sent.emit(q);
      },
      error: (err) => {
        this.sending.set(false);
        this.handleError(err);
      },
    });
  }

  private handleError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (err.status === 400 && body?.field === 'prospectPhone') {
        this.error.set('Ce numéro n\'est pas un mobile ivoirien valide : le SMS ne peut pas être envoyé.');
        return;
      }
      if (err.status === 429) {
        this.error.set(ERROR_BY_CODE['TOO_MANY_REQUESTS']);
        return;
      }
      const mapped = typeof body?.code === 'string' ? ERROR_BY_CODE[body.code] : undefined;
      if (mapped) {
        this.error.set(mapped);
        return;
      }
      if (err.status === 403) {
        this.error.set('Accès non autorisé.');
        return;
      }
      if (typeof body?.message === 'string' && body.message) {
        this.error.set(body.message);
        return;
      }
    }
    this.error.set("Impossible d'envoyer le SMS.");
  }
}
