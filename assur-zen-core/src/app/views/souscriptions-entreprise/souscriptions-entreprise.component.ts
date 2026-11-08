import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import {
  ActivityDomain,
  ENTERPRISE_STATUS_LABEL,
  EnterpriseRequest,
  EnterpriseRequestService,
  EnterpriseRequestStatus,
  enterpriseErrorMessage,
} from '../../core/mobile-admin/enterprise-request.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { LoyaltyBadgeComponent } from '../../shared/loyalty-badge/loyalty-badge.component';
import { ModalComponent } from '../../shared/modal/modal.component';

type Tab = 'requests' | 'domains';
type Filter = 'ALL' | EnterpriseRequestStatus;
type Action = 'EN_COURS' | 'TRAITEE' | 'REFUSEE';

interface DomainRow {
  key: number;
  code: string;
  label: string;
  active: boolean;
  /** Déjà enregistré : le code n'est plus modifiable et la ligne ne peut qu'être désactivée. */
  existing: boolean;
}

const MAX_COMMENT = 500;
const CODE_PATTERN = /^[A-Z0-9_]{1,40}$/;

/** Mobile AssurZen › Souscriptions Entreprise : demandes reçues depuis l'application et domaines d'activité. */
@Component({
  selector: 'app-souscriptions-entreprise',
  templateUrl: './souscriptions-entreprise.component.html',
  styleUrl: './souscriptions-entreprise.component.scss',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    RouterLink,
    AlertMessageComponent,
    ConfirmDialogComponent,
    LoyaltyBadgeComponent,
    ModalComponent,
  ],
})
export class SouscriptionsEntrepriseComponent {
  private readonly service = inject(EnterpriseRequestService);
  private nextKey = 1;

  readonly tab = signal<Tab>('requests');
  readonly info = signal<string | null>(null);
  readonly statusLabel = ENTERPRISE_STATUS_LABEL;
  readonly maxComment = MAX_COMMENT;

  // ---- Demandes ----
  readonly items = signal<EnterpriseRequest[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly filter = signal<Filter>('ALL');
  readonly filtered = computed(() => {
    const f = this.filter();
    return f === 'ALL' ? this.items() : this.items().filter((i) => i.status === f);
  });
  readonly counts = computed(() => {
    const c: Record<EnterpriseRequestStatus, number> = { RECUE: 0, EN_COURS: 0, TRAITEE: 0, REFUSEE: 0 };
    for (const i of this.items()) c[i.status]++;
    return c;
  });

  readonly selected = signal<EnterpriseRequest | null>(null);
  readonly comment = signal('');
  readonly busy = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly confirmAction = signal<Action | null>(null);

  // ---- Domaines d'activité ----
  readonly domains = signal<DomainRow[]>([]);
  private readonly domainsSaved = signal('[]');
  readonly domainsLoading = signal(false);
  readonly domainsSaving = signal(false);
  readonly domainsError = signal<string | null>(null);
  private domainsLoaded = false;
  readonly domainLabelByCode = signal<Record<string, string>>({});
  readonly domainsDirty = computed(() => this.fingerprint(this.domains()) !== this.domainsSaved());
  readonly domainErrors = computed(() => this.validateDomains(this.domains()));
  readonly canSaveDomains = computed(
    () => this.domainsDirty() && this.domainErrors().size === 0 && !this.domainsSaving() && !this.domainsLoading(),
  );

  constructor() {
    this.fetch();
    this.loadDomains(false);
  }

  setTab(tab: Tab): void {
    this.tab.set(tab);
    this.info.set(null);
  }

  // ------------------------------------------------------------------ demandes

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.service.pendingCount.set(items.filter((i) => i.status === 'RECUE' || i.status === 'EN_COURS').length);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(enterpriseErrorMessage(err, 'Impossible de charger les demandes.'));
        this.loading.set(false);
      },
    });
  }

  open(item: EnterpriseRequest): void {
    this.selected.set(item);
    this.comment.set('');
    this.actionError.set(null);
    this.confirmAction.set(null);
  }

  close(): void {
    this.selected.set(null);
    this.confirmAction.set(null);
  }

  /** Statuts atteignables depuis le statut courant (une demande traitée ou refusée est en lecture seule). */
  actions(item: EnterpriseRequest): Action[] {
    if (item.status === 'RECUE') return ['EN_COURS', 'TRAITEE', 'REFUSEE'];
    if (item.status === 'EN_COURS') return ['TRAITEE', 'REFUSEE'];
    return [];
  }

  actionLabel(action: Action): string {
    return action === 'EN_COURS' ? 'Passer « En cours »' : action === 'TRAITEE' ? 'Marquer « Traitée »' : 'Refuser';
  }

  /** Refuser exige un motif (montré au client) ; « En cours » s'applique sans confirmation. */
  requestAction(action: Action): void {
    this.actionError.set(null);
    if (action === 'REFUSEE' && !this.comment().trim()) {
      this.actionError.set('Le refus demande un commentaire : il est montré au client.');
      return;
    }
    if (action === 'EN_COURS') {
      this.apply(action);
      return;
    }
    this.confirmAction.set(action);
  }

  apply(action: Action): void {
    const item = this.selected();
    if (!item) return;
    this.confirmAction.set(null);
    this.busy.set(true);
    this.actionError.set(null);
    this.service.setStatus(item.id, action, this.comment().trim() || undefined).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.items.update((list) => list.map((i) => (i.id === updated.id ? { ...i, ...updated } : i)));
        this.service.pendingCount.set(this.items().filter((i) => i.status === 'RECUE' || i.status === 'EN_COURS').length);
        this.info.set(`${updated.reference} : ${ENTERPRISE_STATUS_LABEL[updated.status].toLowerCase()}. Le client a été notifié.`);
        this.selected.set(null);
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(enterpriseErrorMessage(err, 'Impossible de mettre à jour la demande.'));
        // Déjà traitée ailleurs : on recharge pour refléter l'état réel.
        if (err?.error?.code === 'REQUEST_ALREADY_PROCESSED') this.fetch();
      },
    });
  }

  confirmTitle(): string {
    const a = this.confirmAction();
    return a === 'TRAITEE' ? 'Marquer la demande comme traitée ?' : 'Refuser la demande ?';
  }

  confirmMessage(): string {
    return 'Le client est notifié dans l’application' + (this.comment().trim() ? ' avec votre commentaire.' : '.');
  }

  statusChip(status: EnterpriseRequestStatus): string {
    return status === 'RECUE'
      ? 'dl-chip dl-chip--warning'
      : status === 'EN_COURS'
        ? 'dl-chip dl-chip--info'
        : status === 'TRAITEE'
          ? 'dl-chip dl-chip--success'
          : 'dl-chip dl-chip--danger';
  }

  domainLabel(code: string | null | undefined): string {
    if (!code) return '—';
    return this.domainLabelByCode()[code] ?? code;
  }

  formatDate(value?: string | null): string {
    if (!value) return '—';
    const d = new Date(value);
    const p = (n: number) => String(n).padStart(2, '0');
    return `${p(d.getDate())}/${p(d.getMonth() + 1)} ${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  formatAmount(value: number): string {
    return `${value.toLocaleString('fr-FR')} F`;
  }

  // ------------------------------------------------------------------ domaines d'activité

  loadDomains(force: boolean): void {
    if (this.domainsLoaded && !force) return;
    this.domainsLoaded = true;
    this.domainsLoading.set(true);
    this.domainsError.set(null);
    this.service.domains().subscribe({
      next: (list) => {
        this.setDomains(list);
        this.domainsLoading.set(false);
      },
      error: (err) => {
        this.domainsError.set(enterpriseErrorMessage(err, 'Impossible de charger les domaines d’activité.'));
        this.domainsLoading.set(false);
        this.domainsLoaded = false;
      },
    });
  }

  private setDomains(list: ActivityDomain[]): void {
    const rows: DomainRow[] = [...list]
      .sort((a, b) => a.order - b.order)
      .map((d) => ({ key: this.nextKey++, code: d.code, label: d.label, active: d.active, existing: true }));
    this.domains.set(rows);
    this.domainsSaved.set(this.fingerprint(rows));
    this.domainLabelByCode.set(Object.fromEntries(list.map((d) => [d.code, d.label])));
  }

  addDomain(): void {
    this.info.set(null);
    this.domains.update((rows) => [...rows, { key: this.nextKey++, code: '', label: '', active: true, existing: false }]);
  }

  patchDomain(key: number, change: Partial<DomainRow>): void {
    this.info.set(null);
    this.domains.update((rows) => rows.map((r) => (r.key === key ? { ...r, ...change } : r)));
  }

  removeDomain(key: number): void {
    this.domains.update((rows) => rows.filter((r) => r.key !== key));
  }

  moveDomain(index: number, direction: -1 | 1): void {
    this.domains.update((rows) => {
      const target = index + direction;
      if (target < 0 || target >= rows.length) return rows;
      const next = [...rows];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  private validateDomains(rows: DomainRow[]): Map<number, string> {
    const errors = new Map<number, string>();
    const seen = new Set<string>();
    for (const r of rows) {
      const code = r.code.trim();
      if (!CODE_PATTERN.test(code)) {
        errors.set(r.key, 'Code : majuscules, chiffres et _ uniquement (40 caractères max).');
      } else if (seen.has(code)) {
        errors.set(r.key, 'Ce code existe déjà dans la liste.');
      } else if (!r.label.trim()) {
        errors.set(r.key, 'Le libellé est obligatoire.');
      } else if (r.label.trim().length > 100) {
        errors.set(r.key, 'Le libellé fait 100 caractères au plus.');
      }
      seen.add(code);
    }
    return errors;
  }

  private fingerprint(rows: DomainRow[]): string {
    return JSON.stringify(rows.map((r) => [r.code.trim(), r.label.trim(), r.active]));
  }

  saveDomains(): void {
    if (!this.canSaveDomains()) return;
    this.domainsSaving.set(true);
    this.domainsError.set(null);
    this.service
      .saveDomains(this.domains().map((r) => ({ code: r.code.trim(), label: r.label.trim(), active: r.active })))
      .subscribe({
        next: (list) => {
          this.setDomains(list);
          this.domainsSaving.set(false);
          this.info.set('Domaines d’activité enregistrés.');
        },
        error: (err) => {
          this.domainsSaving.set(false);
          this.domainsError.set(enterpriseErrorMessage(err, 'Impossible d’enregistrer les domaines d’activité.'));
        },
      });
  }

  cancelDomains(): void {
    this.info.set(null);
    this.loadDomains(true);
  }
}
