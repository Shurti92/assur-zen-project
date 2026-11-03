import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { SpinnerComponent } from '@coreui/angular';

import { QuoteView } from '../../core/quotes/models/quote.model';
import { QuoteService } from '../../core/quotes/quote.service';

const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const MAX_MESSAGE = 500;

const ERROR_BY_CODE: Record<string, string> = {
  QUOTE_EXPIRED: 'Devis expiré : il ne peut plus être envoyé.',
  MAIL_DISABLED: "L'envoi d'e-mails est désactivé sur ce serveur.",
  MAIL_SEND_FAILED: "L'envoi a échoué. Réessayez dans un instant.",
  TOO_MANY_REQUESTS: "Trop d'envois pour ce devis aujourd'hui. Réessayez plus tard.",
};

/** Fenêtre « Envoyer le devis par e-mail » : adresse, message personnel, PDF joint, copie à soi. */
@Component({
  selector: 'app-send-quote-email-dialog',
  templateUrl: './send-quote-email-dialog.component.html',
  styleUrl: './send-quote-email-dialog.component.scss',
  imports: [SpinnerComponent],
})
export class SendQuoteEmailDialogComponent {
  private readonly service = inject(QuoteService);

  readonly quote = input.required<QuoteView>();
  /** Émis avec le devis mis à jour après un envoi réussi. */
  readonly sent = output<QuoteView>();
  readonly closed = output<void>();

  readonly maxMessage = MAX_MESSAGE;
  readonly email = signal('');
  readonly message = signal('');
  readonly attachPdf = signal(true);
  readonly copyToMe = signal(false);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly emailError = signal<string | null>(null);

  readonly canSend = computed(() => !this.sending() && EMAIL_PATTERN.test(this.email().trim()));

  ngOnInit(): void {
    this.email.set(this.quote().prospectEmail ?? '');
  }

  setEmail(value: string): void {
    this.email.set(value);
    this.emailError.set(null);
  }

  formatAmount(value: number): string {
    return `${value.toLocaleString('fr-FR')} F`;
  }

  send(): void {
    const email = this.email().trim();
    if (!EMAIL_PATTERN.test(email)) {
      this.emailError.set('Adresse e-mail invalide.');
      return;
    }
    if (this.sending()) return;
    this.sending.set(true);
    this.error.set(null);
    const message = this.message().trim();
    this.service
      .sendEmail(this.quote().reference, {
        email,
        message: message || undefined,
        attachPdf: this.attachPdf(),
        copyToMe: this.copyToMe(),
      })
      .subscribe({
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
      if (err.status === 400 && body?.code === 'VALIDATION_FAILED') {
        if (body.field === 'email') {
          this.emailError.set(body.message ?? 'Adresse e-mail invalide.');
          return;
        }
        if (body.field === 'message') {
          this.error.set(body.message ?? 'Message invalide.');
          return;
        }
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
    this.error.set("Impossible d'envoyer le devis.");
  }
}
