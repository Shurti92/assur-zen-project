import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { SpinnerComponent } from '@coreui/angular';

import { ciMobileOperator, formatCiPhone } from '../../core/quotes/ci-phone';
import { QuoteView } from '../../core/quotes/models/quote.model';
import { QuoteService } from '../../core/quotes/quote.service';

const ERROR_BY_CODE: Record<string, string> = {
  QUOTE_NOT_ACCEPTED: "Le devis n'est pas accepté : il ne peut pas être relancé.",
  QUOTE_EXPIRED: 'Devis expiré : il ne peut plus être relancé.',
  QUOTE_ALREADY_PAID: 'Ce devis est déjà payé : aucune relance nécessaire.',
};

/** Fenêtre « Relancer le client par SMS » (devis accepté non payé) : numéro modifiable, texte exact du SMS. */
@Component({
  selector: 'app-quote-reminder-dialog',
  templateUrl: './quote-reminder-dialog.component.html',
  styleUrl: './send-quote-email-dialog.component.scss',
  imports: [SpinnerComponent],
})
export class QuoteReminderDialogComponent {
  private readonly service = inject(QuoteService);

  readonly quote = input.required<QuoteView>();
  /** Émis avec le devis mis à jour après un envoi réussi. */
  readonly sent = output<QuoteView>();
  readonly closed = output<void>();

  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  /** Numéro saisi (null tant que le conseiller n'y a pas touché : numéro du devis). */
  readonly edited = signal<string | null>(null);

  readonly phone = computed(() => this.edited() ?? formatCiPhone(this.quote().prospectPhone));
  readonly valid = computed(() => ciMobileOperator(this.phone()) !== null);

  formatAmount(value: number): string {
    return value.toLocaleString('fr-FR');
  }

  send(): void {
    if (this.sending() || !this.valid()) return;
    this.sending.set(true);
    this.error.set(null);
    const digits = this.phone().replace(/\D/g, '');
    const changed = digits !== (this.quote().prospectPhone ?? '').replace(/\D/g, '');
    this.service.sendPaymentReminder(this.quote().reference, changed ? digits : undefined).subscribe({
      next: (q) => {
        this.sending.set(false);
        this.sent.emit(q);
      },
      error: (err) => {
        this.sending.set(false);
        this.error.set(this.messageOf(err));
      },
    });
  }

  private messageOf(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (err.status === 429) return "Limite atteinte : 5 SMS par devis et par 24 h. Réessayez plus tard.";
      if (err.status === 502 || err.status === 503) return "Envoi impossible pour le moment : le service SMS est indisponible.";
      if (err.status === 403) return 'Accès non autorisé.';
      const mapped = typeof body?.code === 'string' ? ERROR_BY_CODE[body.code] : undefined;
      if (mapped) return mapped;
      if (err.status === 400) return "Ce numéro n'est pas un mobile ivoirien valide : le SMS ne peut pas être envoyé.";
      if (typeof body?.message === 'string' && body.message) return body.message;
    }
    return "Impossible d'envoyer le SMS.";
  }
}
