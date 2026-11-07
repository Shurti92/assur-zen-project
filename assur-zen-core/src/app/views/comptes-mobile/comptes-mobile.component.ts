import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { InsuredService } from '../../core/insured/insured.service';
import { InsuredResponse } from '../../core/insured/models/insured.model';
import { MobileAccountService } from '../../core/mobile-admin/mobile-account.service';
import { LinkedInsured, MobileAccount, MobileAccountDetail } from '../../core/mobile-admin/models/mobile-account.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { ZenPointsClientComponent } from './zen-points-client/zen-points-client.component';

type StatusFilter = '' | 'true' | 'false';

/** Messages lisibles pour les codes d'erreur du contrat (admin-mobile.md §1). */
const ERROR_MESSAGES: Record<string, string> = {
  INSURED_NOT_FOUND: 'Assuré introuvable.',
  PHONE_INVALID: "Le numéro de téléphone n'est pas valide.",
  PHONE_ALREADY_REGISTERED: 'Ce numéro possède déjà un compte mobile.',
  ACCOUNT_NOT_FOUND: 'Compte introuvable.',
};

@Component({
  selector: 'app-comptes-mobile',
  templateUrl: './comptes-mobile.component.html',
  styleUrl: './comptes-mobile.component.scss',
  imports: [ButtonDirective, SpinnerComponent, FormsModule, AlertMessageComponent, ConfirmDialogComponent, ModalComponent, ZenPointsClientComponent],
})
export class ComptesMobileComponent {
  private readonly service = inject(MobileAccountService);
  private readonly insuredService = inject(InsuredService);

  readonly pageSize = 20;

  // ---- Liste ----
  readonly items = signal<MobileAccount[]>([]);
  readonly pageIndex = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly statusFilter = signal<StatusFilter>('');

  // ---- Bandeau de synthèse ----
  readonly statTotal = signal<number | null>(null);
  readonly statActive = signal<number | null>(null);
  readonly statInactive = signal<number | null>(null);
  readonly statPendingDeletions = signal<number | null>(null);

  // ---- Fiche compte ----
  readonly detailVisible = signal(false);
  readonly detail = signal<MobileAccountDetail | null>(null);
  /** Onglet de la fiche : informations du compte ou Zen Points. */
  readonly ficheTab = signal<'compte' | 'zen'>('compte');
  readonly detailLoading = signal(false);
  readonly detailError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly actionBusy = signal(false);

  // ---- Création ----
  readonly createVisible = signal(false);
  readonly createSaving = signal(false);
  readonly createError = signal<string | null>(null);
  readonly createInsured = signal<InsuredResponse | null>(null);
  readonly createPhone = signal('');
  readonly createInvite = signal(true);

  // ---- Sélecteur d'assuré (création et rattachement) ----
  readonly insuredQuery = signal('');
  readonly insuredResults = signal<InsuredResponse[]>([]);
  readonly insuredSearching = signal(false);

  // ---- Rattachement ----
  readonly attachVisible = signal(false);
  readonly attachInsured = signal<InsuredResponse | null>(null);

  // ---- Détachement (confirmation) ----
  readonly unlinkTarget = signal<LinkedInsured | null>(null);

  // ---- Désactivation ----
  readonly deactivateVisible = signal(false);
  readonly deactivateComment = signal('');

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;
  private insuredDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.searchDebounce) clearTimeout(this.searchDebounce);
      if (this.insuredDebounce) clearTimeout(this.insuredDebounce);
    });
    this.fetch();
    this.fetchStats();
  }

  // ================= Liste =================

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    const q = this.searchQuery().trim() || undefined;
    this.service.list(this.pageIndex(), this.pageSize, q, this.activeParam()).subscribe({
      next: (result) => {
        this.items.set(result.items);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger les comptes mobile.'));
        this.loading.set(false);
      },
    });
  }

  refresh(): void {
    this.fetch();
    this.fetchStats();
  }

  private activeParam(): boolean | undefined {
    const value = this.statusFilter();
    return value === '' ? undefined : value === 'true';
  }

  private fetchStats(): void {
    // Totaux lus via la pagination (size=1) : aucun endpoint de synthèse n'existe côté gateway.
    this.service.list(0, 1).subscribe({ next: (r) => this.statTotal.set(r.totalElements), error: () => this.statTotal.set(null) });
    this.service.list(0, 1, undefined, true).subscribe({ next: (r) => this.statActive.set(r.totalElements), error: () => this.statActive.set(null) });
    this.service.list(0, 1, undefined, false).subscribe({ next: (r) => this.statInactive.set(r.totalElements), error: () => this.statInactive.set(null) });
    this.service.pendingDeletionCount().subscribe({
      next: (n) => this.statPendingDeletions.set(n),
      error: () => this.statPendingDeletions.set(null),
    });
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
    this.fetch();
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.pageIndex.set(0);
      this.fetch();
    }, 400);
  }

  onStatusFilterChange(value: string): void {
    this.statusFilter.set(value as StatusFilter);
    this.pageIndex.set(0);
    this.fetch();
  }

  // ================= Fiche compte =================

  openDetail(account: MobileAccount): void {
    this.ficheTab.set('compte');
    this.detail.set({ account, linkedInsureds: [] });
    this.detailError.set(null);
    this.actionError.set(null);
    this.detailVisible.set(true);
    this.loadDetail(account.userId);
  }

  private loadDetail(userId: number): void {
    this.detailLoading.set(true);
    this.service.get(userId).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.detailLoading.set(false);
      },
      error: (err) => {
        this.detailError.set(this.messageOf(err, 'Impossible de charger la fiche du compte.'));
        this.detailLoading.set(false);
      },
    });
  }

  closeDetail(): void {
    this.detailVisible.set(false);
    this.attachVisible.set(false);
    this.deactivateVisible.set(false);
  }

  // ================= Activation / désactivation =================

  openDeactivate(): void {
    this.deactivateComment.set('');
    this.actionError.set(null);
    this.deactivateVisible.set(true);
  }

  confirmDeactivate(): void {
    this.changeActive(false, this.deactivateComment().trim());
  }

  reactivate(): void {
    this.changeActive(true);
  }

  private changeActive(active: boolean, comment?: string): void {
    const userId = this.detail()?.account.userId;
    if (userId === undefined) return;
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.service.setActive(userId, active, comment).subscribe({
      next: () => {
        this.actionBusy.set(false);
        this.deactivateVisible.set(false);
        this.loadDetail(userId);
        this.refresh();
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(this.messageOf(err, 'Impossible de modifier le statut du compte.'));
      },
    });
  }

  // ================= Rattachement / détachement =================

  openAttach(): void {
    this.resetInsuredPicker();
    this.attachInsured.set(null);
    this.actionError.set(null);
    this.attachVisible.set(true);
  }

  confirmAttach(): void {
    const userId = this.detail()?.account.userId;
    const insured = this.attachInsured();
    if (userId === undefined || !insured) return;
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.service.linkInsured(userId, insured.id).subscribe({
      next: () => {
        this.actionBusy.set(false);
        this.attachVisible.set(false);
        this.loadDetail(userId);
        this.fetch();
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(this.messageOf(err, 'Impossible de rattacher cet assuré.'));
      },
    });
  }

  /** Demande confirmation (fenêtre du portail) avant de détacher un assuré. */
  askUnlink(insured: LinkedInsured): void {
    this.unlinkTarget.set(insured);
  }

  cancelUnlink(): void {
    this.unlinkTarget.set(null);
  }

  confirmUnlink(): void {
    const insured = this.unlinkTarget();
    this.unlinkTarget.set(null);
    if (insured) this.unlink(insured);
  }

  private unlink(insured: LinkedInsured): void {
    const userId = this.detail()?.account.userId;
    if (userId === undefined) return;
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.service.unlinkInsured(userId, insured.insuredId).subscribe({
      next: () => {
        this.actionBusy.set(false);
        this.loadDetail(userId);
        this.fetch();
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(this.messageOf(err, 'Impossible de détacher cet assuré.'));
      },
    });
  }

  // ================= Création =================

  openCreate(): void {
    this.resetInsuredPicker();
    this.createInsured.set(null);
    this.createPhone.set('');
    this.createInvite.set(true);
    this.createError.set(null);
    this.createVisible.set(true);
  }

  selectCreateInsured(insured: InsuredResponse): void {
    this.createInsured.set(insured);
    this.createPhone.set(insured.clientPhone ?? '');
    this.resetInsuredPicker();
  }

  clearCreateInsured(): void {
    this.createInsured.set(null);
    this.createPhone.set('');
  }

  submitCreate(): void {
    const insured = this.createInsured();
    if (!insured) {
      this.createError.set('Sélectionnez un assuré.');
      return;
    }
    const phone = this.createPhone().trim();
    this.createSaving.set(true);
    this.createError.set(null);
    this.service
      .create({
        insuredId: insured.id,
        // Le téléphone n'est envoyé que s'il diffère de celui de l'assuré (défaut côté serveur).
        phone: phone && phone !== (insured.clientPhone ?? '') ? phone : undefined,
        sendInvitation: this.createInvite(),
      })
      .subscribe({
        next: (detail) => {
          this.createSaving.set(false);
          this.createVisible.set(false);
          this.refresh();
          this.detail.set(detail);
          this.detailError.set(null);
          this.actionError.set(null);
          this.detailVisible.set(true);
        },
        error: (err) => {
          this.createSaving.set(false);
          this.createError.set(this.messageOf(err, 'Impossible de créer le compte mobile.'));
        },
      });
  }

  // ================= Sélecteur d'assuré =================

  onInsuredQueryInput(value: string): void {
    this.insuredQuery.set(value);
    if (this.insuredDebounce) clearTimeout(this.insuredDebounce);
    const q = value.trim();
    if (q.length < 2) {
      this.insuredResults.set([]);
      return;
    }
    this.insuredDebounce = setTimeout(() => {
      this.insuredSearching.set(true);
      this.insuredService.list(0, 8, q).subscribe({
        next: (page) => {
          this.insuredResults.set(page.content);
          this.insuredSearching.set(false);
        },
        error: () => {
          this.insuredResults.set([]);
          this.insuredSearching.set(false);
        },
      });
    }, 350);
  }

  selectAttachInsured(insured: InsuredResponse): void {
    this.attachInsured.set(insured);
    this.resetInsuredPicker();
  }

  private resetInsuredPicker(): void {
    this.insuredQuery.set('');
    this.insuredResults.set([]);
  }

  // ================= Utilitaires =================

  initials(name: string): string {
    const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?';
  }

  displayName(account: MobileAccount): string {
    return account.fullName?.trim() || 'Compte sans nom';
  }

  formatDate(value: string | null | undefined): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  insuredLabel(insured: InsuredResponse): string {
    return `${insured.nom} ${insured.prenoms}`.trim();
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
