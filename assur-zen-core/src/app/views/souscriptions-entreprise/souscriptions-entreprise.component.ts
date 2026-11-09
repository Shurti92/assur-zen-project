import { Component, DestroyRef, HostListener, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SpinnerComponent } from '@coreui/angular';

import {
  ActivityDomain,
  ENTERPRISE_STATUS_LABEL,
  EmailStatus,
  EnterpriseRequest,
  EnterpriseRequestService,
  EnterpriseRequestStatus,
  enterpriseErrorMessage,
} from '../../core/mobile-admin/enterprise-request.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { LoyaltyBadgeComponent } from '../../shared/loyalty-badge/loyalty-badge.component';

type Tab = 'requests' | 'domains';
type Filter = 'TODO' | 'RECUE' | 'EN_COURS' | 'TRAITEE' | 'REFUSEE' | 'ALL';
type Sort = 'oldest' | 'newest' | 'amount';
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
/** Une demande reçue depuis plus longtemps que ce délai est signalée comme à traiter en priorité. */
const LATE_HOURS = 48;
const HOUR = 3_600_000;

const EMAIL_LABEL: Record<EmailStatus, string> = {
  EN_ATTENTE: 'En attente',
  ENVOYE: 'Envoyé',
  ECHEC: 'Échec',
  NON_CONFIGURE: 'Non configuré',
  DESACTIVE: 'Désactivé',
};

/** Mobile AssurZen › Souscriptions Entreprise : suivi des demandes reçues depuis l'application et domaines d'activité. */
@Component({
  selector: 'app-souscriptions-entreprise',
  templateUrl: './souscriptions-entreprise.component.html',
  styleUrl: './souscriptions-entreprise.component.scss',
  imports: [SpinnerComponent, RouterLink, AlertMessageComponent, ConfirmDialogComponent, LoyaltyBadgeComponent],
})
export class SouscriptionsEntrepriseComponent {
  private readonly service = inject(EnterpriseRequestService);
  private readonly destroyRef = inject(DestroyRef);
  private nextKey = 1;

  readonly tab = signal<Tab>('requests');
  readonly info = signal<string | null>(null);
  readonly statusLabel = ENTERPRISE_STATUS_LABEL;
  readonly maxComment = MAX_COMMENT;
  readonly lateHours = LATE_HOURS;

  // ---- Demandes ----
  readonly items = signal<EnterpriseRequest[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly updatedAt = signal<Date | null>(null);
  /** Horloge pour les durées « il y a … » (rafraîchie chaque minute). */
  private readonly now = signal(Date.now());

  readonly filter = signal<Filter>('TODO');
  readonly search = signal('');
  readonly domainFilter = signal('');
  readonly sort = signal<Sort>('oldest');

  readonly counts = computed(() => {
    const c: Record<EnterpriseRequestStatus, number> = { RECUE: 0, EN_COURS: 0, TRAITEE: 0, REFUSEE: 0 };
    for (const i of this.items()) c[i.status]++;
    return c;
  });
  readonly todoCount = computed(() => this.counts().RECUE + this.counts().EN_COURS);
  readonly lateCount = computed(() => this.items().filter((i) => this.isLate(i)).length);
  /** Demandes dont au moins un e-mail est en échec. */
  readonly failedMails = computed(() => this.items().filter((i) => i.alertStatus === 'ECHEC' || i.confirmationStatus === 'ECHEC'));
  /** Alertes au conseiller non parties : à traiter en premier, le conseiller n'a pas été prévenu. */
  readonly failedAlerts = computed(() => this.items().filter((i) => i.alertStatus === 'ECHEC'));

  readonly domainOptions = computed(() => {
    const labels = this.domainLabelByCode();
    const codes = new Set(this.items().map((i) => i.activityDomain).filter((c): c is string => !!c));
    return [...codes].map((code) => ({ code, label: labels[code] ?? code })).sort((a, b) => a.label.localeCompare(b.label, 'fr'));
  });

  readonly visible = computed(() => {
    const f = this.filter();
    const q = this.normalize(this.search());
    const domain = this.domainFilter();
    let list = this.items().filter((i) => {
      if (f === 'TODO' && i.status !== 'RECUE' && i.status !== 'EN_COURS') return false;
      if (f !== 'TODO' && f !== 'ALL' && i.status !== f) return false;
      if (domain && i.activityDomain !== domain) return false;
      if (!q) return true;
      return [i.companyName, i.reference, i.representativeName, i.rccmNumber, i.formulaName, i.formulaId, i.location, this.domainLabel(i.activityDomain)]
        .some((v) => this.normalize(v ?? '').includes(q));
    });
    const mode = this.sort();
    const open = (i: EnterpriseRequest) => (i.status === 'RECUE' || i.status === 'EN_COURS' ? 0 : 1);
    const time = (i: EnterpriseRequest) => new Date(i.createdAt).getTime();
    list = [...list].sort((a, b) => {
      // Les demandes à traiter passent toujours en premier.
      if (open(a) !== open(b)) return open(a) - open(b);
      if (mode === 'amount') return b.quotedTotal - a.quotedTotal;
      if (mode === 'newest') return time(b) - time(a);
      // « Plus anciennes d'abord » pour ce qui reste à traiter, les plus récentes d'abord pour l'historique.
      return open(a) === 0 ? time(a) - time(b) : time(b) - time(a);
    });
    return list;
  });
  readonly hasFilters = computed(() => this.search().trim() !== '' || this.domainFilter() !== '' || this.filter() !== 'TODO');

  readonly selected = signal<EnterpriseRequest | null>(null);
  readonly comment = signal('');
  readonly busy = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly confirmAction = signal<Action | null>(null);
  readonly copied = signal(false);

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
  /** Nombre de demandes par domaine (sur les 200 dernières demandes chargées). Le champ de la demande porte le libellé choisi par le client. */
  readonly requestsByDomain = computed(() => {
    const m: Record<string, number> = {};
    for (const i of this.items()) {
      if (!i.activityDomain) continue;
      const k = this.normalize(i.activityDomain);
      m[k] = (m[k] ?? 0) + 1;
    }
    return m;
  });

  /** Demandes rattachées à un domaine : on compare le libellé et le code (la Gateway renvoie le libellé). */
  requestCount(row: { code: string; label: string }): number {
    const m = this.requestsByDomain();
    const byLabel = m[this.normalize(row.label)] ?? 0;
    const byCode = this.normalize(row.code) === this.normalize(row.label) ? 0 : (m[this.normalize(row.code)] ?? 0);
    return byLabel + byCode;
  }

  constructor() {
    this.fetch();
    this.loadDomains(false);
    // Horloge : met à jour les « il y a … » sans recharger les données.
    const timer = setInterval(() => this.now.set(Date.now()), 60_000);
    this.destroyRef.onDestroy(() => clearInterval(timer));
    // Le volet reflète toujours la version courante de la demande ouverte.
    effect(() => {
      const open = untracked(() => this.selected());
      const list = this.items();
      if (open) {
        const fresh = list.find((i) => i.id === open.id);
        if (fresh && fresh !== open) untracked(() => this.selected.set(fresh));
      }
    });
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
        this.updatedAt.set(new Date());
        this.now.set(Date.now());
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(enterpriseErrorMessage(err, 'Impossible de charger les demandes.'));
        this.loading.set(false);
      },
    });
  }

  clearFilters(): void {
    this.search.set('');
    this.domainFilter.set('');
    this.filter.set('TODO');
  }

  /** Ouvre la première demande dont l'alerte e-mail n'est pas partie. */
  openFirstFailed(): void {
    const first = this.failedAlerts()[0] ?? this.failedMails()[0];
    if (first) this.open(first);
  }

  open(item: EnterpriseRequest): void {
    this.selected.set(item);
    this.comment.set('');
    this.actionError.set(null);
    this.confirmAction.set(null);
    this.copied.set(false);
    // Le focus passe au volet (accessibilité clavier).
    setTimeout(() => document.getElementById('se-drawer-close')?.focus(), 0);
  }

  close(): void {
    this.selected.set(null);
    this.confirmAction.set(null);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.confirmAction()) this.confirmAction.set(null);
    else if (this.selected()) this.close();
  }

  /** Statuts atteignables depuis le statut courant (une demande traitée ou refusée est en lecture seule). */
  actions(item: EnterpriseRequest): Action[] {
    if (item.status === 'RECUE') return ['EN_COURS', 'TRAITEE', 'REFUSEE'];
    if (item.status === 'EN_COURS') return ['TRAITEE', 'REFUSEE'];
    return [];
  }

  actionLabel(action: Action): string {
    return action === 'EN_COURS' ? 'Passer « En cours »' : action === 'TRAITEE' ? 'Marquer « Traitée »' : 'Refuser…';
  }

  /** Refuser exige un motif (montré au client) ; « En cours » s'applique sans confirmation. */
  requestAction(action: Action): void {
    this.actionError.set(null);
    if (action === 'REFUSEE' && !this.comment().trim()) {
      this.actionError.set('Le refus demande un commentaire : il est montré au client.');
      document.getElementById('se-comment')?.focus();
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
    return this.confirmAction() === 'TRAITEE' ? 'Marquer la demande comme traitée ?' : 'Refuser la demande ?';
  }

  confirmMessage(): string {
    return 'Le client est notifié dans l’application' + (this.comment().trim() ? ' avec votre commentaire.' : '.');
  }

  // ------------------------------------------------------------------ affichage

  statusChip(status: EnterpriseRequestStatus): string {
    return status === 'RECUE' ? 'se-chip se-chip--wa' : status === 'EN_COURS' ? 'se-chip se-chip--in' : status === 'TRAITEE' ? 'se-chip se-chip--ok' : 'se-chip se-chip--ko';
  }

  barClass(status: EnterpriseRequestStatus): string {
    return status === 'RECUE' ? 'se-bar--wa' : status === 'EN_COURS' ? 'se-bar--in' : status === 'TRAITEE' ? 'se-bar--ok' : 'se-bar--ko';
  }

  isOpen(item: EnterpriseRequest): boolean {
    return item.status === 'RECUE' || item.status === 'EN_COURS';
  }

  /** Reçue et toujours pas prise en charge au-delà du délai d'alerte. */
  isLate(item: EnterpriseRequest): boolean {
    return item.status === 'RECUE' && this.now() - new Date(item.createdAt).getTime() > LATE_HOURS * HOUR;
  }

  ago(iso?: string | null): string {
    if (!iso) return '—';
    const diff = this.now() - new Date(iso).getTime();
    const min = Math.floor(diff / 60_000);
    if (min < 1) return 'à l’instant';
    if (min < 60) return `il y a ${min} min`;
    const h = Math.floor(min / 60);
    if (h < 24) return `il y a ${h} h`;
    const d = Math.floor(h / 24);
    return d === 1 ? 'hier' : `il y a ${d} j`;
  }

  /** Pastille d'un e-mail : état, libellé, détail pour l'infobulle. null = demande antérieure au suivi. */
  mailChip(status: EmailStatus | null | undefined): { cls: string; sign: string } {
    if (!status) return { cls: 'se-mail se-mail--none', sign: '' };
    if (status === 'ENVOYE') return { cls: 'se-mail se-mail--ok', sign: '✓' };
    if (status === 'ECHEC') return { cls: 'se-mail se-mail--ko', sign: '✗' };
    if (status === 'EN_ATTENTE') return { cls: 'se-mail se-mail--wait', sign: '…' };
    return { cls: 'se-mail se-mail--none', sign: '–' };
  }

  mailLabel(status: EmailStatus | null | undefined): string {
    return status ? EMAIL_LABEL[status] : 'Non suivi';
  }

  mailTitle(status: EmailStatus | null | undefined, detail?: string | null): string {
    if (!status) return 'Demande antérieure au suivi des e-mails';
    return EMAIL_LABEL[status] + (detail ? ' : ' + detail : '');
  }

  mailChipClass(status: EmailStatus | null | undefined): string {
    if (status === 'ENVOYE') return 'se-chip se-chip--ok';
    if (status === 'ECHEC') return 'se-chip se-chip--ko';
    if (status === 'EN_ATTENTE') return 'se-chip se-chip--wa';
    return 'se-chip se-chip--ne';
  }

  domainLabel(code: string | null | undefined): string {
    if (!code) return '—';
    return this.domainLabelByCode()[code] ?? code;
  }

  formatDateTime(value?: string | null): string {
    if (!value) return '—';
    const d = new Date(value);
    const p = (n: number) => String(n).padStart(2, '0');
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} à ${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  formatAmount(value: number): string {
    return `${value.toLocaleString('fr-FR')} F`;
  }

  async copy(text: string | null | undefined): Promise<void> {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    } catch {
      // Presse-papiers indisponible : rien à faire.
    }
  }

  /** Étapes de la frise : l'API ne fournit que la date de réception et celle du traitement final. */
  steps(item: EnterpriseRequest): { label: string; sub: string; state: 'done' | 'cur' | 'todo' }[] {
    const final = item.status === 'TRAITEE' || item.status === 'REFUSEE';
    return [
      { label: 'Reçue', sub: this.shortDate(item.createdAt), state: 'done' },
      { label: 'En cours', sub: '', state: item.status === 'EN_COURS' ? 'cur' : final ? 'done' : 'todo' },
      {
        label: final ? ENTERPRISE_STATUS_LABEL[item.status] : 'Traitée ou refusée',
        sub: final ? this.shortDate(item.processedAt) : '',
        state: final ? 'done' : 'todo',
      },
    ];
  }

  private shortDate(value?: string | null): string {
    if (!value) return '';
    const d = new Date(value);
    const p = (n: number) => String(n).padStart(2, '0');
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}`;
  }

  private normalize(value: string): string {
    return value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
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
