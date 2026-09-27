import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { map, switchMap } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { SpinnerComponent } from '@coreui/angular';

import { SycaPayService } from '../../core/sycapay/sycapay.service';
import { sycapayErrorMessage } from '../../core/sycapay/sycapay-errors';
import {
  SycaPayBalanceResult,
  SycaPayBeneficiaryType,
  SycaPayDashboard,
  SycaPayStatusCounts,
  SycaPayTransaction,
  SycaPayTransactionFilter,
  SycaPayTransactionStatus,
} from '../../core/sycapay/models/sycapay.model';
import { AuthService } from '../../core/auth/auth.service';
import { ConfigurationService } from '../../core/configurations/configuration.service';
import {
  DynamicConfigurationRequest,
  DynamicConfigurationResponse,
} from '../../core/configurations/models/configuration.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

type View = 'solde' | 'dashboard' | 'transactions';
type TransactionStatusFilter = 'all' | SycaPayTransactionStatus;
type BeneficiaryFilter = 'all' | SycaPayBeneficiaryType;

const EMPTY_FILTERS = {
  reference: '',
  beneficiaryType: 'all' as BeneficiaryFilter,
  beneficiaryId: '',
  status: 'all' as TransactionStatusFilter,
  dateStart: '',
  dateEnd: '',
};

const PAGE_SIZE = 20;

/** Clés de configuration dynamique (module SYCAPAY) des alertes de solde — backend b8c34b4. */
const THRESHOLD_KEY = 'SYCAPAY_BALANCE_ALERT_THRESHOLD';
const REPORT_ENABLED_KEY = 'SYCAPAY_BALANCE_REPORT_ENABLED';

/** PUT /configurations/{key} remplace TOUS les champs : on renvoie l'objet relu, seul configValue change. */
function toConfigRequest(config: DynamicConfigurationResponse, configValue: string, motif: string): DynamicConfigurationRequest {
  return {
    configKey: config.configKey,
    configValue,
    valueType: config.valueType,
    description: config.description ?? undefined,
    module: config.module ?? undefined,
    category: config.category ?? undefined,
    environment: config.environment ?? undefined,
    defaultValue: config.defaultValue ?? undefined,
    validationRule: config.validationRule ?? undefined,
    allowedValues: config.allowedValues ?? undefined,
    active: config.active,
    editable: config.editable,
    sensitive: config.sensitive,
    restartRequired: config.restartRequired,
    cacheEnabled: config.cacheEnabled,
    displayOrder: config.displayOrder ?? undefined,
    motif,
  };
}

const STATUS_LABEL: Record<SycaPayTransactionStatus, string> = {
  PENDING: 'En attente',
  PROCESSING: 'En cours',
  SUCCESS: 'Réussi',
  FAILED: 'Échoué',
  CANCELLED: 'Annulé',
  UNKNOWN: 'Indéterminé',
};

/** Variante visuelle d'un statut — suffixe des classes .sp-chip--* (src/scss/_sycapay.scss). */
const STATUS_VARIANT: Record<SycaPayTransactionStatus, string> = {
  PENDING: 'neutral',
  PROCESSING: 'info',
  SUCCESS: 'success',
  FAILED: 'danger',
  CANCELLED: 'neutral',
  UNKNOWN: 'warning',
};

/** Statuts acceptés par POST /reconcile — tout autre statut renvoie 409 (contrat backend du
 * 2026-09-23 : PENDING n'est PAS réconciliable, SUCCESS/FAILED/CANCELLED sont définitifs). */
const RECONCILABLE_STATUSES = new Set<SycaPayTransactionStatus>(['PROCESSING', 'UNKNOWN']);

const ALL_STATUSES: SycaPayTransactionStatus[] = ['PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED', 'UNKNOWN'];

/** Nom affiché + pastille de couleur par opérateur ("operateur" arrive tel quel de SycaPay :
 * Wave | Orange | Mtn | Moov). Wave est le compte utilisé pour les commissions. */
const OPERATORS: Record<string, { label: string; color: string }> = {
  wave: { label: 'Wave', color: '#1aa7d8' },
  orange: { label: 'Orange Money', color: '#ff7900' },
  mtn: { label: 'MTN MoMo', color: '#f2c200' },
  moov: { label: 'Moov Money', color: '#0057a8' },
};

interface TimelineStep {
  label: string;
  date: string | null;
  state: 'done' | 'pending' | 'failed';
}

@Component({
  selector: 'app-sycapay',
  templateUrl: './sycapay.component.html',
  imports: [SpinnerComponent, ReactiveFormsModule, AlertMessageComponent],
  host: { '(document:keydown.escape)': 'detailVisible() && closeDetail()' },
})
export class SycaPayComponent {
  private readonly sycapayService = inject(SycaPayService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  /** Module ADMIN-only côté backend (intégration SycaPay du 2026-09-22, tous les endpoints
   * /sycapay/** exigent le rôle ADMIN) — adminGuard protège déjà la route, ce test reste un filet
   * d'affichage ; le vrai rempart est le 403 backend. */
  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');

  readonly view = signal<View>('dashboard');
  readonly statusLabel = STATUS_LABEL;
  readonly allStatuses = ALL_STATUSES;

  /** Horloge (rafraîchie toutes les 5 s) pour l'âge de la dernière lecture du solde. */
  private readonly now = signal(Date.now());

  setView(view: View): void {
    this.view.set(view);
    if (view === 'solde' && this.balanceResults().length === 0 && !this.balanceLoading()) this.fetchBalance();
    if (view === 'dashboard' && !this.dashboard() && !this.dashboardLoading()) this.fetchDashboard();
    if (view === 'transactions' && this.transactions().length === 0 && !this.transactionsLoading()) this.fetchTransactions();
  }

  /** Bouton « Actualiser » de l'en-tête : recharge la vue courante. */
  refreshCurrentView(): void {
    const view = this.view();
    if (view === 'dashboard') {
      this.fetchDashboard();
      this.fetchBalance();
    } else if (view === 'solde') {
      this.fetchBalance();
    } else {
      this.fetchTransactions();
    }
  }

  readonly refreshing = computed(() => {
    const view = this.view();
    if (view === 'dashboard') return this.dashboardLoading();
    if (view === 'solde') return this.balanceLoading();
    return this.transactionsLoading();
  });

  constructor() {
    if (!this.isAdmin()) return;
    this.fetchDashboard();
    this.fetchAlertSettings();
    // Carte « Solde Wave » du tableau de bord — cache serveur ~20 s, appel peu coûteux.
    this.fetchBalance();

    const timer = setInterval(() => this.now.set(Date.now()), 5000);
    this.destroyRef.onDestroy(() => clearInterval(timer));

    // Lien « Réconcilier » depuis le résultat de paiement des commissions (CommercialComponent) :
    // ?transaction=<id> si le backend fournit l'id, sinon ?reference=<transactionReference>.
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = Number(params.get('transaction'));
      const reference = params.get('reference');
      if (Number.isInteger(id) && id > 0) this.openTransactionById(id);
      else if (reference) this.openTransactionByReference(reference);
    });
  }

  private openTransactionById(id: number): void {
    this.view.set('transactions');
    this.fetchTransactions();
    this.sycapayService.getTransaction(id).subscribe({
      next: (transaction) => this.openDetail(transaction),
      // « introuvable » seulement sur 404 : un 503/erreur réseau n'en dit rien sur la transaction.
      error: (err: unknown) =>
        this.transactionsError.set(
          sycapayErrorMessage(
            err,
            err instanceof HttpErrorResponse && err.status === 404
              ? 'Transaction SycaPay introuvable.'
              : 'Impossible de charger la transaction SycaPay.',
          ),
        ),
    });
  }

  /** Le filtre reference est une égalité exacte : 0 ou 1 résultat. */
  private openTransactionByReference(reference: string): void {
    this.view.set('transactions');
    this.filterForm.reset({ ...EMPTY_FILTERS, reference });
    this.transactionsPage.set(0);
    this.fetchTransactions((page) => {
      if (page.length === 1) this.openDetail(page[0]);
    });
  }

  statusVariant(status: SycaPayTransactionStatus): string {
    return STATUS_VARIANT[status];
  }

  isReconcilable(status: SycaPayTransactionStatus): boolean {
    return RECONCILABLE_STATUSES.has(status);
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }

  formatTime(value: string): string {
    return new Date(value).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  /** Nombre formaté seul — la devise (FCFA) est posée à part dans le template. */
  formatNumber(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(value);
  }

  /** « 0757873487 » → « 07 57 87 34 87 ». */
  formatPhone(phone: string): string {
    return phone.replace(/(\d{2})(?=\d)/g, '$1 ');
  }

  initials(name: string | null): string {
    if (!name) return '?';
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join('');
  }

  // ================================================================ SOLDE MARCHAND ================================================================

  readonly balanceResults = signal<SycaPayBalanceResult[]>([]);
  readonly balanceLoading = signal(false);
  readonly balanceError = signal<string | null>(null);
  readonly balanceLastFetched = signal<Date | null>(null);

  fetchBalance(refresh = false): void {
    this.balanceLoading.set(true);
    this.balanceError.set(null);
    this.sycapayService.getBalance(refresh).subscribe({
      next: (response) => {
        this.balanceResults.set(response.Resultats);
        this.balanceLastFetched.set(new Date());
        this.now.set(Date.now());
        this.balanceLoading.set(false);
      },
      error: (err: unknown) => {
        this.balanceError.set(sycapayErrorMessage(err, 'Impossible de charger le solde marchand.'));
        this.balanceLoading.set(false);
      },
    });
  }

  /** "solde" est une String côté SycaPay (contrat externe tel quel) — parsée ici uniquement
   * pour l'affichage, jamais renvoyée dans un payload. */
  soldeValue(result: SycaPayBalanceResult): number {
    const value = Number(result.solde);
    return Number.isFinite(value) ? value : 0;
  }

  operatorLabel(result: SycaPayBalanceResult): string {
    return OPERATORS[result.operateur.toLowerCase()]?.label ?? result.operateur;
  }

  operatorColor(result: SycaPayBalanceResult): string {
    return OPERATORS[result.operateur.toLowerCase()]?.color ?? '#8b93a7';
  }

  isCommissionAccount(result: SycaPayBalanceResult): boolean {
    return result.operateur.toLowerCase() === 'wave';
  }

  readonly totalBalance = computed(() => this.balanceResults().reduce((sum, r) => sum + this.soldeValue(r), 0));

  readonly waveBalance = computed(() => {
    const wave = this.balanceResults().find((r) => this.isCommissionAccount(r));
    return wave ? this.soldeValue(wave) : null;
  });

  /** Opérateur des commissions en premier, puis les autres par solde décroissant. */
  // Alertes de solde (Telegram) ----------------------------------------------

  private readonly configurationService = inject(ConfigurationService);
  readonly thresholdConfig = signal<DynamicConfigurationResponse | null>(null);
  readonly reportConfig = signal<DynamicConfigurationResponse | null>(null);
  readonly alertSettingsError = signal<string | null>(null);
  readonly thresholdInput = signal('');
  readonly thresholdSaving = signal(false);
  readonly thresholdMessage = signal<{ variant: 'success' | 'danger'; text: string } | null>(null);
  readonly reportSaving = signal(false);
  readonly telegramSending = signal(false);
  readonly telegramMessage = signal<{ variant: 'success' | 'danger'; text: string } | null>(null);

  readonly threshold = computed(() => {
    const config = this.thresholdConfig();
    const value = Number(config?.configValue ?? config?.defaultValue);
    return config && Number.isFinite(value) ? value : null;
  });

  readonly reportEnabled = computed(() => {
    const config = this.reportConfig();
    return (config?.configValue ?? config?.defaultValue) === 'true';
  });

  /** Solde Wave (compte commissions) sous le seuil d'alerte configuré. */
  readonly belowThreshold = computed(() => {
    const wave = this.waveBalance();
    const threshold = this.threshold();
    return wave !== null && threshold !== null && wave < threshold;
  });

  readonly thresholdDirty = computed(() => {
    const input = this.thresholdInput().trim();
    return input !== '' && Number(input) !== this.threshold();
  });

  fetchAlertSettings(): void {
    this.alertSettingsError.set(null);
    const onError = () => this.alertSettingsError.set("Impossible de charger les réglages d'alerte du solde.");
    this.configurationService.getByKey(THRESHOLD_KEY).subscribe({
      next: (config) => {
        this.thresholdConfig.set(config);
        this.thresholdInput.set(String(this.threshold() ?? ''));
      },
      error: onError,
    });
    this.configurationService.getByKey(REPORT_ENABLED_KEY).subscribe({
      next: (config) => this.reportConfig.set(config),
      error: onError,
    });
  }

  onThresholdInput(value: string): void {
    this.thresholdInput.set(value.replace(/\D/g, ''));
    this.thresholdMessage.set(null);
  }

  saveThreshold(): void {
    const value = this.thresholdInput().trim();
    if (!this.thresholdDirty()) return;
    this.thresholdSaving.set(true);
    this.thresholdMessage.set(null);
    // Relecture juste avant l'écriture : PUT remplace l'objet entier, ne pas écraser une
    // modification faite entre-temps depuis « Configuration dynamique ».
    this.writeConfig(THRESHOLD_KEY, value, "Seuil d'alerte modifié depuis l'écran SycaPay").subscribe({
      next: (updated) => {
        this.thresholdConfig.set(updated);
        this.thresholdInput.set(String(this.threshold() ?? value));
        this.thresholdSaving.set(false);
        this.thresholdMessage.set({ variant: 'success', text: 'Seuil enregistré, appliqué immédiatement.' });
      },
      error: (err: unknown) => {
        this.thresholdSaving.set(false);
        this.thresholdMessage.set({
          variant: 'danger',
          text:
            err instanceof HttpErrorResponse && err.status === 400
              ? 'Seuil refusé : il doit être compris entre 0 et 100 000 000 FCFA.'
              : "Impossible d'enregistrer le seuil. Réessayez.",
        });
      },
    });
  }

  toggleReport(): void {
    const next = !this.reportEnabled();
    this.reportSaving.set(true);
    this.alertSettingsError.set(null);
    this.writeConfig(REPORT_ENABLED_KEY, String(next), `Rapport de solde Telegram ${next ? 'activé' : 'désactivé'} depuis l'écran SycaPay`).subscribe({
      next: (updated) => {
        this.reportConfig.set(updated);
        this.reportSaving.set(false);
      },
      error: () => {
        this.reportSaving.set(false);
        this.alertSettingsError.set("Impossible de modifier l'envoi du rapport. Réessayez.");
      },
    });
  }

  private writeConfig(key: string, value: string, motif: string) {
    return this.configurationService
      .getByKey(key)
      .pipe(switchMap((config) => this.configurationService.update(key, toConfigRequest(config, value, motif))));
  }

  sendBalanceToTelegram(): void {
    this.telegramSending.set(true);
    this.telegramMessage.set(null);
    this.sycapayService.sendBalanceToTelegram().subscribe({
      next: (response) => {
        this.telegramSending.set(false);
        // Le backend renvoie le texte Telegram tel quel (balises HTML <b>…</b>) : on les retire.
        const text = (response.message ?? '').replace(/<[^>]+>/g, '').trim();
        this.telegramMessage.set({ variant: 'success', text: text || 'Solde envoyé sur Telegram.' });
      },
      error: (err: unknown) => {
        this.telegramSending.set(false);
        this.telegramMessage.set({ variant: 'danger', text: sycapayErrorMessage(err, "Impossible d'envoyer le solde sur Telegram.") });
      },
    });
  }

  readonly sortedBalances = computed(() =>
    [...this.balanceResults()].sort((a, b) => {
      if (this.isCommissionAccount(a) !== this.isCommissionAccount(b)) return this.isCommissionAccount(a) ? -1 : 1;
      return this.soldeValue(b) - this.soldeValue(a);
    }),
  );

  readonly balanceAgeLabel = computed(() => {
    const fetched = this.balanceLastFetched();
    if (!fetched) return null;
    const seconds = Math.max(0, Math.round((this.now() - fetched.getTime()) / 1000));
    if (seconds < 60) return `Lu il y a ${seconds} s`;
    const minutes = Math.round(seconds / 60);
    return `Lu il y a ${minutes} min`;
  });

  // ================================================================ TABLEAU DE BORD DU JOUR ================================================================

  readonly dashboard = signal<SycaPayDashboard | null>(null);
  readonly dashboardLoading = signal(true);
  readonly dashboardError = signal<string | null>(null);
  readonly dashboardFetchedAt = signal<Date | null>(null);

  fetchDashboard(): void {
    this.dashboardLoading.set(true);
    this.dashboardError.set(null);
    this.sycapayService.getDashboard().subscribe({
      next: (result) => {
        this.dashboard.set(result);
        this.dashboardFetchedAt.set(new Date());
        this.dashboardLoading.set(false);
      },
      error: (err: unknown) => {
        this.dashboardError.set(sycapayErrorMessage(err, 'Impossible de charger le tableau de bord du jour.'));
        this.dashboardLoading.set(false);
      },
    });
  }

  /** Segments de la barre de répartition (seuls les statuts non nuls sont dessinés). */
  readonly dashboardSegments = computed(() => {
    const d = this.dashboard();
    if (!d) return [];
    return [
      { variant: 'success', count: d.paiementsSuccess },
      { variant: 'info', count: d.paiementsPending },
      { variant: 'danger', count: d.paiementsFailed },
      { variant: 'warning', count: d.paiementsUnknown },
    ].filter((segment) => segment.count > 0);
  });

  // ================================================================ TRANSACTIONS ================================================================

  readonly transactions = signal<SycaPayTransaction[]>([]);
  readonly transactionsLoading = signal(false);
  readonly transactionsError = signal<string | null>(null);
  readonly transactionsPage = signal(0);
  readonly transactionsTotalPages = signal(1);
  readonly transactionsTotalElements = signal<number | null>(null);

  readonly filterForm = this.fb.nonNullable.group({
    reference: [''],
    beneficiaryType: ['all' as BeneficiaryFilter],
    /** Id du commercial ou du partenaire selon beneficiaryType (ignoré sur « Tous »). */
    beneficiaryId: [''],
    status: ['all' as TransactionStatusFilter],
    dateStart: [''],
    dateEnd: [''],
  });

  /** Type de bénéficiaire filtré — affiche le champ id adapté (commercial ou partenaire). */
  readonly beneficiaryTypeFilter = toSignal(this.filterForm.controls.beneficiaryType.valueChanges, {
    initialValue: 'all' as BeneficiaryFilter,
  });

  /** « Commercial #12 » / « Partenaire #34 » — un seul des deux ids est renseigné. */
  /** Objet du virement : commission d'un contrat, ou demande de paiement anticipé (contractId null). */
  transactionSubject(t: SycaPayTransaction): string {
    if (t.contractId !== null) return `Contrat #${t.contractId}`;
    return t.partialPaymentRequestId ? `Paiement anticipé, demande n° ${t.partialPaymentRequestId}` : 'Paiement anticipé';
  }

  beneficiaryLabel(t: SycaPayTransaction): string {
    return t.beneficiaryType === 'PARTENAIRE' ? `Partenaire #${t.partenaireId ?? '—'}` : `Commercial #${t.commercialId ?? '—'}`;
  }

  /** Miroir signal du statut filtré, pour l'état actif des pastilles. */
  readonly statusFilter = signal<TransactionStatusFilter>('all');

  /** Compteurs des pastilles de statut + total de l'onglet — null tant que non chargés (ou en
   * erreur : les pastilles s'affichent alors sans nombre, la liste reste utilisable). */
  readonly statusCounts = signal<SycaPayStatusCounts | null>(null);
  /** Filtres (hors status) des compteurs affichés — évite de les recharger à chaque page/statut. */
  private statusCountsKey: string | null = null;

  private fetchStatusCounts(filter: Omit<SycaPayTransactionFilter, 'status'>, force = false): void {
    const key = JSON.stringify(filter);
    if (key === this.statusCountsKey && !force) return;
    this.statusCountsKey = key;
    this.sycapayService.getStatusCounts(filter).subscribe({
      next: (counts) => this.statusCounts.set(counts),
      error: () => {
        this.statusCounts.set(null);
        this.statusCountsKey = null;
      },
    });
  }

  fetchTransactions(onLoaded?: (content: SycaPayTransaction[]) => void): void {
    this.transactionsLoading.set(true);
    this.transactionsError.set(null);
    const raw = this.filterForm.getRawValue();
    this.statusFilter.set(raw.status);
    const filter = {
      reference: raw.reference.trim() || undefined,
      beneficiaryType: raw.beneficiaryType === 'all' ? undefined : raw.beneficiaryType,
      commercialId: raw.beneficiaryType === 'COMMERCIAL' && raw.beneficiaryId !== '' ? Number(raw.beneficiaryId) : undefined,
      partenaireId: raw.beneficiaryType === 'PARTENAIRE' && raw.beneficiaryId !== '' ? Number(raw.beneficiaryId) : undefined,
      dateStart: raw.dateStart ? new Date(raw.dateStart).toISOString() : undefined,
      dateEnd: raw.dateEnd ? new Date(raw.dateEnd).toISOString() : undefined,
    };
    this.fetchStatusCounts(filter);
    this.sycapayService
      .getTransactions(this.transactionsPage(), PAGE_SIZE, { ...filter, status: raw.status === 'all' ? undefined : raw.status })
      .subscribe({
        next: (page) => {
          this.transactions.set(page.content);
          this.transactionsTotalPages.set(Math.max(1, page.totalPages));
          this.transactionsTotalElements.set(page.totalElements);
          this.transactionsLoading.set(false);
          onLoaded?.(page.content);
        },
        error: (err: unknown) => {
          this.transactionsError.set(sycapayErrorMessage(err, 'Impossible de charger les transactions SycaPay.'));
          this.transactionsLoading.set(false);
        },
      });
  }

  applyFilters(): void {
    this.transactionsPage.set(0);
    this.fetchTransactions();
  }

  setStatusFilter(status: TransactionStatusFilter): void {
    this.filterForm.controls.status.setValue(status);
    this.applyFilters();
  }

  /** Tuiles du tableau de bord / bandeau d'alerte → liste filtrée sur ce statut. */
  showTransactionsWithStatus(status: SycaPayTransactionStatus): void {
    this.filterForm.reset({ ...EMPTY_FILTERS, status });
    this.view.set('transactions');
    this.applyFilters();
  }

  resetFilters(): void {
    this.filterForm.reset(EMPTY_FILTERS);
    this.applyFilters();
  }

  private readonly hasFieldFilters = toSignal(
    this.filterForm.valueChanges.pipe(map((v) => !!(v.reference || (v.beneficiaryType && v.beneficiaryType !== 'all') || v.dateStart || v.dateEnd))),
    { initialValue: false },
  );

  /** Affiche « Réinitialiser » seulement quand un filtre est posé. */
  readonly hasActiveFilters = computed(() => this.statusFilter() !== 'all' || this.hasFieldFilters());

  goToTransactionsPage(index: number): void {
    if (index < 0 || index >= this.transactionsTotalPages()) return;
    this.transactionsPage.set(index);
    this.fetchTransactions();
  }

  // -- détail (panneau latéral) & réconciliation --

  readonly detailVisible = signal(false);
  readonly selectedTransaction = signal<SycaPayTransaction | null>(null);
  readonly reconciling = signal(false);
  readonly reconcileError = signal<string | null>(null);
  readonly reconcileNoChange = signal(false);
  readonly referenceCopied = signal(false);

  openDetail(transaction: SycaPayTransaction): void {
    this.selectedTransaction.set(transaction);
    this.reconcileError.set(null);
    this.reconcileNoChange.set(false);
    this.referenceCopied.set(false);
    this.detailVisible.set(true);
  }

  closeDetail(): void {
    this.detailVisible.set(false);
    this.selectedTransaction.set(null);
  }

  /** Étapes du suivi : demandée → envoyée à SycaPay → issue (ou confirmation en attente). */
  readonly timeline = computed<TimelineStep[]>(() => {
    const t = this.selectedTransaction();
    if (!t) return [];
    const attempts = t.attemptCount > 1 ? `${t.attemptCount} tentatives` : `${t.attemptCount} tentative`;
    const steps: TimelineStep[] = [
      { label: 'Demandée', date: t.requestDate, state: 'done' },
      { label: `Envoyée à SycaPay · ${attempts}`, date: t.processingDate, state: t.processingDate ? 'done' : 'pending' },
    ];
    if (t.status === 'SUCCESS') steps.push({ label: 'Virement confirmé', date: t.completedDate, state: 'done' });
    else if (t.status === 'FAILED') steps.push({ label: 'Virement échoué', date: t.completedDate, state: 'failed' });
    else if (t.status === 'CANCELLED') steps.push({ label: 'Annulée', date: t.completedDate, state: 'failed' });
    else steps.push({ label: 'Confirmation en attente', date: null, state: 'pending' });
    return steps;
  });

  /** Bandeau contextuel du panneau de détail selon le statut. */
  readonly detailNotice = computed<{ variant: string; title: string; text: string } | null>(() => {
    const t = this.selectedTransaction();
    if (!t) return null;
    const cause = t.errorMessage || t.errorCode;
    switch (t.status) {
      case 'UNKNOWN':
        return {
          variant: 'warning',
          title: cause ? `SycaPay n'a pas confirmé le virement (${t.errorCode ?? 'sans réponse'})` : "SycaPay n'a pas confirmé le virement",
          text: "Le virement est peut-être parti. Ne relancez pas le paiement : vérifiez d'abord son statut auprès de SycaPay.",
        };
      case 'PROCESSING':
        return {
          variant: 'info',
          title: 'Virement en cours de traitement',
          text: "SycaPay a accepté la demande mais ne l'a pas encore confirmée. Vérifiez son statut avant toute nouvelle action.",
        };
      case 'FAILED':
        return {
          variant: 'danger',
          title: 'Le virement a échoué',
          text: cause ? `Motif : ${cause}. Le contrat reste payable depuis l'écran Commissions.` : "Le contrat reste payable depuis l'écran Commissions.",
        };
      default:
        return null;
    }
  });

  copyReference(): void {
    const reference = this.selectedTransaction()?.transactionReference;
    if (!reference || !navigator.clipboard) return;
    void navigator.clipboard.writeText(reference).then(() => {
      this.referenceCopied.set(true);
      setTimeout(() => this.referenceCopied.set(false), 2000);
    });
  }

  private applyTransactionUpdate(updated: SycaPayTransaction): void {
    this.selectedTransaction.set(updated);
    this.transactions.set(this.transactions().map((t) => (t.id === updated.id ? updated : t)));
  }

  reconcileSelected(): void {
    const transaction = this.selectedTransaction();
    if (!transaction) return;
    this.reconciling.set(true);
    this.reconcileError.set(null);
    this.reconcileNoChange.set(false);
    this.sycapayService.reconcile(transaction.id).subscribe({
      next: (updated) => {
        this.reconciling.set(false);
        // SycaPay peut ne rien renvoyer d'exploitable : statut inchangé, pas d'erreur HTTP —
        // à traiter comme "toujours en attente, réessayer plus tard" (confirmé par le backend).
        this.reconcileNoChange.set(updated.status === transaction.status);
        this.applyTransactionUpdate(updated);
        if (updated.status !== transaction.status && this.statusCountsKey) {
          this.fetchStatusCounts(JSON.parse(this.statusCountsKey), true);
        }
      },
      error: (err: unknown) => {
        this.reconciling.set(false);
        this.reconcileError.set(sycapayErrorMessage(err, 'Impossible de relancer la vérification pour cette transaction.', 'reconcile'));
      },
    });
  }
}
