import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SpinnerComponent } from '@coreui/angular';

import { QuotePaymentReminderItem, QuoteEmailHistoryItem, QuoteSmsHistoryItem } from '../../core/quotes/models/quote.model';
import { ciMobileOperator, CI_OPERATOR_LABEL, formatCiPhone } from '../../core/quotes/ci-phone';
import { QUOTE_ADDRESS_ENABLED, QUOTE_SMS_ENABLED } from '../../core/quotes/quote-sms.config';
import { QUOTE_EMAIL_ENABLED } from '../../core/quotes/quote-email.config';
import { QuoteView } from '../../core/quotes/models/quote.model';
import { QuoteService } from '../../core/quotes/quote.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { SendQuoteSmsDialogComponent } from './send-quote-sms-dialog.component';
import { SendQuoteEmailDialogComponent } from './send-quote-email-dialog.component';
import { QUOTE_STATUS_LABEL } from './devis-liste.component';
import { DevisConversionComponent } from './devis-conversion.component';
import { QuoteReminderDialogComponent } from './quote-reminder-dialog.component';

/** Un envoi (SMS ou e-mail) de la frise « Envois au prospect ». */
interface SendEvent {
  kind: 'sms' | 'email';
  at: string;
  to: string;
  by?: string | null;
  /** Détails propres au canal (lien valable jusqu'au…, avec PDF, copie à…). */
  notes: string[];
}

interface Step {
  label: string;
  state: 'done' | 'now' | 'todo';
}

@Component({
  selector: 'app-devis-detail',
  templateUrl: './devis-detail.component.html',
  styleUrl: './devis-detail.component.scss',
  imports: [AlertMessageComponent, DevisConversionComponent, QuoteReminderDialogComponent, RouterLink, SendQuoteEmailDialogComponent, SendQuoteSmsDialogComponent, SpinnerComponent],
})
export class DevisDetailComponent {
  private readonly service = inject(QuoteService);

  private readonly route = inject(ActivatedRoute);

  /** Paramètre de route `:reference`. */
  readonly reference = signal(this.route.snapshot.paramMap.get('reference') ?? '');

  readonly statusLabel = QUOTE_STATUS_LABEL;

  readonly emailEnabled = QUOTE_EMAIL_ENABLED;
  readonly emailDialogOpen = signal(false);

  /** Adresse et SMS : inactifs tant que la gateway n'a pas confirmé l'API (quote-sms.config.ts). */
  readonly addressEnabled = QUOTE_ADDRESS_ENABLED;
  readonly smsEnabled = QUOTE_SMS_ENABLED;
  readonly smsDialogOpen = signal(false);

  /** Historiques des envois, chargés avec le devis et fusionnés dans une seule frise. */
  readonly emailHistory = signal<QuoteEmailHistoryItem[]>([]);
  readonly smsHistory = signal<QuoteSmsHistoryItem[]>([]);
  readonly historyError = signal<string | null>(null);

  /** Relance de paiement (devis accepté non payé). */
  readonly reminderDialogOpen = signal(false);
  readonly reminders = signal<QuotePaymentReminderItem[]>([]);

  /** Devis accepté et pas encore payé : la relance par SMS est proposée. */
  readonly canRemind = computed(() => this.quote()?.status === 'ACCEPTE');
  readonly paymentLabel = computed(() => {
    const s = this.quote()?.conversion?.status;
    return s === 'PENDING' ? 'En attente' : s === 'FAILED' ? 'Échoué' : 'Aucun paiement';
  });

  readonly quote = signal<QuoteView | null>(null);
  readonly loading = signal(true);
  readonly accepting = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  /** Envois SMS et e-mail, du plus récent au plus ancien. */
  readonly events = computed<SendEvent[]>(() => {
    const sms = this.smsHistory().map<SendEvent>((s) => ({
      kind: 'sms',
      at: s.sentAt,
      to: formatCiPhone(s.to),
      by: s.sentBy,
      notes: s.linkExpiresAt ? [`lien valable jusqu'au ${this.formatDate(s.linkExpiresAt)}`] : [],
    }));
    const emails = this.emailHistory().map<SendEvent>((e) => ({
      kind: 'email',
      at: e.sentAt,
      to: e.to,
      by: e.sentBy,
      notes: [e.withPdf ? 'avec PDF' : 'sans PDF', ...(e.copyTo ? [`copie à ${e.copyTo}`] : [])],
    }));
    return [...sms, ...emails].sort((a, b) => b.at.localeCompare(a.at));
  });

  /** Parcours du devis : créé → calculé → envoyé → accepté → payé → contrat. */
  readonly steps = computed<Step[]>(() => {
    const q = this.quote();
    if (!q) return [];
    const sent = (q.smsSentCount ?? 0) > 0 || (q.emailSentCount ?? 0) > 0 || !!q.lastSmsSentAt || !!q.lastEmailSentAt;
    const converted = q.status === 'CONVERTI';
    const paid = q.status === 'PAYE' || converted;
    const accepted = q.status === 'ACCEPTE' || paid;
    return [
      { label: 'Créé', state: 'done' },
      { label: 'Calculé', state: 'done' },
      { label: 'Envoyé', state: sent || accepted ? 'done' : 'now' },
      { label: 'Accepté', state: accepted ? 'done' : sent ? 'now' : 'todo' },
      { label: 'Payé', state: paid ? 'done' : accepted ? 'now' : 'todo' },
      { label: 'Contrat', state: converted ? 'done' : paid ? 'now' : 'todo' },
    ];
  });

  /** Part de la durée de validité déjà écoulée (0-100). */
  readonly validityElapsed = computed(() => {
    const q = this.quote();
    if (!q) return 0;
    const start = new Date(q.createdAt).getTime();
    // « Valable jusqu'au » est une date : le devis reste valable toute cette journée.
    const end = new Date(q.validUntil).getTime() + (q.validUntil.length <= 10 ? 86_399_000 : 0);
    if (!(end > start)) return 100;
    return Math.min(100, Math.max(0, Math.round(((Date.now() - start) / (end - start)) * 100)));
  });

  /** Initiales du prospect pour l'avatar. */
  readonly initials = computed(() => {
    const name = this.quote()?.prospectName?.trim() ?? '';
    const parts = name.split(/\s+/).filter(Boolean);
    return (parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : name.slice(0, 2)).toUpperCase() || '?';
  });

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
        this.loadHistories();
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger le devis.'));
        this.loading.set(false);
      },
    });
  }

  loadHistories(): void {
    this.historyError.set(null);
    if (this.smsEnabled) this.loadSmsHistory();
    if (this.emailEnabled) this.loadEmailHistory();
    if (this.canRemind()) this.loadReminders();
  }

  private loadReminders(): void {
    this.service.paymentReminders(this.reference()).subscribe({
      next: (items) => this.reminders.set(items),
      error: () => this.historyError.set("Impossible de charger l'historique des relances."),
    });
  }

  onReminderSent(updated: QuoteView): void {
    this.quote.set(updated);
    this.reminderDialogOpen.set(false);
    this.loadReminders();
    this.success.set('SMS de relance envoyé.');
  }

  /** Le devis vient d'être converti : on affiche le contrat créé. */
  onConverted(updated: QuoteView): void {
    this.quote.set(updated);
    this.success.set(updated.status === 'CONVERTI' ? `Contrat créé${updated.policyNumber ? ' : police ' + updated.policyNumber : ''}.` : null);
    if (updated.status !== 'CONVERTI') this.load();
  }

  private loadSmsHistory(): void {
    this.service.smsHistory(this.reference()).subscribe({
      next: (items) => this.smsHistory.set(items),
      error: () => this.historyError.set("Impossible de charger l'historique des envois."),
    });
  }

  private loadEmailHistory(): void {
    this.service.emailHistory(this.reference()).subscribe({
      next: (items) => this.emailHistory.set(items),
      error: () => this.historyError.set("Impossible de charger l'historique des envois."),
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

  onSmsSent(updated: QuoteView): void {
    this.quote.set(updated);
    this.smsDialogOpen.set(false);
    this.loadSmsHistory();
    this.success.set(`SMS envoyé au ${formatCiPhone(updated.lastSmsTo ?? updated.prospectPhone)}.`);
  }

  onEmailSent(updated: QuoteView): void {
    this.quote.set(updated);
    this.emailDialogOpen.set(false);
    this.loadEmailHistory();
    this.success.set(`Devis envoyé à ${updated.lastEmailTo ?? updated.prospectEmail ?? 'le prospect'}.`);
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

  /** Largeur (%) d'un poste dans la barre de répartition du prix. */
  share(part: number | null | undefined, total: number): number {
    return total > 0 ? Math.max(0, ((part ?? 0) / total) * 100) : 0;
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
