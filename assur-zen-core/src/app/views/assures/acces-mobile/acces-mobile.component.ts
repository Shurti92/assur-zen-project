import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';
import { Observable } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { InsuredResponse } from '../../../core/insured/models/insured.model';
import { MobileAccountService } from '../../../core/mobile-admin/mobile-account.service';
import { MobileAccount, MobileAccountDetail } from '../../../core/mobile-admin/models/mobile-account.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

const ERROR_MESSAGES: Record<string, string> = {
  INSURED_NOT_FOUND: 'Assuré introuvable.',
  PHONE_INVALID: "Le numéro de téléphone n'est pas valide.",
  PHONE_ALREADY_REGISTERED: 'Ce numéro possède déjà un compte mobile.',
  ACCOUNT_NOT_FOUND: 'Compte introuvable.',
  ACCOUNT_INACTIVE: "Le compte est suspendu : réactivez-le avant de renvoyer l'invitation.",
};

const digits = (value: string | null | undefined): string => (value ?? '').replace(/\D/g, '');
/** Compare deux numéros sur leurs 10 derniers chiffres (indicatif pays facultatif). */
const samePhone = (a: string | null | undefined, b: string | null | undefined): boolean => {
  const x = digits(a).slice(-10);
  return x.length > 0 && x === digits(b).slice(-10);
};

/**
 * Section « Accès mobile » de la fiche assuré (rôle ADMIN uniquement).
 * Aucun endpoint « compte par assuré » : on cherche par téléphone puis on contrôle le rattachement.
 */
@Component({
  selector: 'app-acces-mobile',
  templateUrl: './acces-mobile.component.html',
  imports: [ButtonDirective, SpinnerComponent, FormsModule, AlertMessageComponent, ConfirmDialogComponent, ModalComponent],
})
export class AccesMobileComponent {
  private readonly service = inject(MobileAccountService);
  private readonly auth = inject(AuthService);

  readonly insured = input.required<InsuredResponse>();
  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Compte déjà rattaché à cet assuré. */
  readonly detail = signal<MobileAccountDetail | null>(null);
  /** Compte existant sur le même numéro mais non rattaché à cet assuré. */
  readonly existing = signal<MobileAccount | null>(null);
  readonly busy = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly createVisible = signal(false);
  readonly createPhone = signal('');
  readonly createInvite = signal(true);
  readonly createError = signal<string | null>(null);

  readonly suspendVisible = signal(false);
  readonly suspendComment = signal('');
  readonly unlinkTarget = signal<{ insuredId: number; name: string } | null>(null);

  readonly account = computed(() => this.detail()?.account ?? null);
  readonly state = computed<'none' | 'pending' | 'active' | 'suspended'>(() => {
    const a = this.account();
    if (!a) return 'none';
    if (!a.active) return 'suspended';
    // Champ absent (gateway pas encore à jour) : on ne prétend pas « invitation envoyée ».
    return a.activated === false ? 'pending' : 'active';
  });

  constructor() {
    let lastInsuredId: number | null = null;
    effect(() => {
      const insured = this.insured();
      if (!untracked(this.isAdmin)) return;
      untracked(() => {
        // Les messages d'action ne suivent pas un changement d'assuré.
        if (insured.id !== lastInsuredId) {
          this.info.set(null);
          this.actionError.set(null);
          lastInsuredId = insured.id;
        }
        this.load(insured);
      });
    });
  }

  private load(insured: InsuredResponse): void {
    this.loading.set(true);
    this.error.set(null);
    this.detail.set(null);
    this.existing.set(null);
    const phones = [insured.clientPhone, insured.clientPhone2].filter((p): p is string => !!p && digits(p).length >= 8);
    if (phones.length === 0) {
      this.loading.set(false);
      return;
    }
    this.service.list(0, 10, phones[0]).subscribe({
      next: (page) => {
        const matches = page.items.filter((a) => phones.some((p) => samePhone(a.phone, p)));
        if (matches.length === 0) {
          this.loading.set(false);
          return;
        }
        this.resolve(insured, matches, 0);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, "Impossible de lire l'accès mobile."));
        this.loading.set(false);
      },
    });
  }

  private resolve(insured: InsuredResponse, matches: MobileAccount[], index: number): void {
    if (index >= matches.length) {
      this.existing.set(matches[0]);
      this.loading.set(false);
      return;
    }
    this.service.get(matches[index].userId).subscribe({
      next: (d) => {
        if (d.linkedInsureds.some((l) => l.insuredId === insured.id)) {
          this.detail.set(d);
          this.loading.set(false);
        } else {
          this.resolve(insured, matches, index + 1);
        }
      },
      error: (err) => {
        this.error.set(this.messageOf(err, "Impossible de lire l'accès mobile."));
        this.loading.set(false);
      },
    });
  }

  private reload(): void {
    this.load(this.insured());
  }

  // ---- Création ----
  openCreate(): void {
    this.createPhone.set(this.insured().clientPhone ?? '');
    this.createInvite.set(true);
    this.createError.set(null);
    this.createVisible.set(true);
  }

  submitCreate(): void {
    const insured = this.insured();
    const phone = this.createPhone().trim();
    this.busy.set(true);
    this.createError.set(null);
    this.service
      .create({
        insuredId: insured.id,
        phone: phone && phone !== (insured.clientPhone ?? '') ? phone : undefined,
        sendInvitation: this.createInvite(),
      })
      .subscribe({
        next: (d) => {
          this.busy.set(false);
          this.createVisible.set(false);
          this.detail.set(d);
          this.existing.set(null);
          this.info.set(this.createInvite() ? "Accès créé, SMS d'invitation envoyé." : 'Accès créé.');
        },
        error: (err) => {
          this.busy.set(false);
          if (err instanceof HttpErrorResponse && err.status === 409 && err.error?.code === 'PHONE_ALREADY_REGISTERED') {
            // Un compte existe déjà sur ce numéro : on l'affiche au lieu d'en recréer un.
            this.createVisible.set(false);
            this.reload();
            return;
          }
          this.createError.set(this.messageOf(err, "Impossible de créer l'accès mobile."));
        },
      });
  }

  // ---- Actions sur le compte ----
  sendInvitation(): void {
    const a = this.account();
    if (!a) return;
    this.run(this.service.sendInvitation(a.userId), "SMS d'invitation renvoyé.", "Impossible d'envoyer l'invitation.", false);
  }

  openSuspend(): void {
    this.suspendComment.set('');
    this.actionError.set(null);
    this.suspendVisible.set(true);
  }

  confirmSuspend(): void {
    const a = this.account();
    if (!a) return;
    this.run(
      this.service.setActive(a.userId, false, this.suspendComment().trim()),
      'Accès suspendu, sessions coupées.',
      'Impossible de suspendre le compte.',
      true,
      () => this.suspendVisible.set(false),
    );
  }

  reactivate(): void {
    const a = this.account();
    if (!a) return;
    this.run(this.service.setActive(a.userId, true), 'Accès réactivé.', 'Impossible de réactiver le compte.', true);
  }

  attachHere(): void {
    const a = this.existing();
    if (!a) return;
    this.run(this.service.linkInsured(a.userId, this.insured().id), 'Assuré rattaché au compte.', 'Impossible de rattacher cet assuré.', true);
  }

  confirmUnlink(): void {
    const t = this.unlinkTarget();
    const a = this.account();
    this.unlinkTarget.set(null);
    if (!t || !a) return;
    this.run(this.service.unlinkInsured(a.userId, t.insuredId), 'Assuré détaché.', 'Impossible de détacher cet assuré.', true);
  }

  private run(call: Observable<unknown>, okMessage: string, failMessage: string, reload: boolean, after?: () => void): void {
    this.busy.set(true);
    this.actionError.set(null);
    this.info.set(null);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        after?.();
        this.info.set(okMessage);
        if (reload) this.reload();
      },
      error: (err) => {
        this.busy.set(false);
        after?.();
        this.actionError.set(this.messageOf(err, failMessage));
      },
    });
  }

  initials(name: string): string {
    const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?';
  }

  formatDate(value: string | null | undefined): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      const code = err.error?.code as string | undefined;
      if (code && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code];
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return fallback;
  }
}
