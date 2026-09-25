import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, forkJoin, of } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ButtonDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormLabelDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { CommissionService } from '../../core/commissions/commission.service';
import {
  CommissionPaymentResponse,
  ContractCommission,
  PartialPaymentStatusResponse,
  PartnerCommissionSummary,
  TypePartenaire,
} from '../../core/commissions/models/commission.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { sycapayErrorMessage } from '../../core/sycapay/sycapay-errors';
import { ModalComponent } from '../../shared/modal/modal.component';
import { SycaPayStatus } from '../../core/commercial/models/commercial-commission.model';
import { SycaPayService } from '../../core/sycapay/sycapay.service';
import { SycaPayTransaction, SycaPayTransactionStatus } from '../../core/sycapay/models/sycapay.model';

/** Statuts qui bloquent un paiement manuel (409) tant que le virement n'est pas tranché. */
const OPEN_TRANSACTION_STATUSES = new Set<SycaPayTransactionStatus>(['PENDING', 'PROCESSING', 'UNKNOWN']);

interface BlockedTransaction {
  transactionId: number;
  policyNumber: string;
  status: SycaPayTransactionStatus;
  /** PROCESSING/UNKNOWN : réconciliable ; PENDING : il faut attendre. */
  reconcilable: boolean;
}

const SYCAPAY_STATUS_LABEL: Record<SycaPayStatus, string> = {
  SUCCESS: 'réussi',
  PROCESSING: 'en cours',
  FAILED: 'échoué',
  UNKNOWN: 'indéterminé',
  REJECTED: 'rejeté',
};

const SYCAPAY_STATUS_VARIANT: Record<SycaPayStatus, string> = {
  SUCCESS: 'success',
  PROCESSING: 'info',
  FAILED: 'danger',
  UNKNOWN: 'warning',
  REJECTED: 'danger',
};

type ContractStatusFilter = 'all' | 'pending' | 'paid';
type ContractsModalTab = 'contrats' | 'demandes';

@Component({
  selector: 'app-commissions',
  templateUrl: './commissions.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    FormsModule,
    FormControlDirective,
    FormCheckInputDirective,
    FormLabelDirective,
    AlertMessageComponent,
    ModalComponent,
    RouterLink,
  ],
})
export class CommissionsComponent {
  private readonly commissionService = inject(CommissionService);
  private readonly sycapayService = inject(SycaPayService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  // Partners summary --------------------------------------------------
  readonly partners = signal<PartnerCommissionSummary[]>([]);
  readonly partnersLoading = signal(true);
  readonly partnersError = signal<string | null>(null);
  readonly partnersSearch = signal('');

  readonly filteredPartners = computed(() => {
    const query = this.partnersSearch().trim().toLowerCase();
    if (!query) return this.partners();
    return this.partners().filter((p) =>
      `${p.nom} ${p.prenoms} ${p.codePartenaire}`.toLowerCase().includes(query)
    );
  });

  // Contracts drill-down modal -----------------------------------------
  readonly contractsModalVisible = signal(false);
  readonly contractsModalTab = signal<ContractsModalTab>('contrats');
  readonly selectedPartner = signal<PartnerCommissionSummary | null>(null);
  readonly contracts = signal<ContractCommission[]>([]);
  readonly contractsLoading = signal(true);
  readonly contractsError = signal<string | null>(null);
  readonly contractsStatusFilter = signal<ContractStatusFilter>('pending');
  readonly selectedContractIds = signal<Set<number>>(new Set());
  readonly paymentReference = signal('');
  readonly paying = signal(false);
  readonly payError = signal<string | null>(null);
  /** Après un 409 SYCAPAY_DUPLICATE_PAYMENT : virements encore ouverts des contrats sélectionnés. */
  readonly payBlockedTransactions = signal<BlockedTransaction[]>([]);

  // Demandes de versement anticipé (partial payment requests) --------
  readonly partialPaymentStatus = signal<PartialPaymentStatusResponse | null>(null);
  readonly partialPaymentLoading = signal(false);
  readonly partialPaymentError = signal<string | null>(null);
  readonly validatingRequestId = signal<number | null>(null);

  readonly filteredContracts = computed(() => {
    const filter = this.contractsStatusFilter();
    return this.contracts().filter((c) => filter === 'all' || c.commissionStatus === filter);
  });

  readonly selectedTotal = computed(() => {
    const ids = this.selectedContractIds();
    return this.contracts()
      .filter((c) => ids.has(c.contractId))
      .reduce((sum, c) => sum + c.partnerShare, 0);
  });

  readonly pendingPartialPaymentCount = computed(
    () => this.partialPaymentStatus()?.history.filter((r) => r.status === 'pending').length ?? 0
  );

  /** Scopé à ce qui est actuellement affiché (filteredContracts), pas à tous les contrats du
   * partenaire — sinon "tout sélectionner" ou le total payable pouvait référencer des contrats
   * en attente invisibles pendant qu'on regarde l'onglet "Payés". */
  readonly visiblePendingIds = computed(() =>
    this.filteredContracts()
      .filter((c) => c.commissionStatus === 'pending')
      .map((c) => c.contractId)
  );

  readonly allPendingSelected = computed(() => {
    const pendingIds = this.visiblePendingIds();
    return pendingIds.length > 0 && pendingIds.every((id) => this.selectedContractIds().has(id));
  });

  // Payment history ------------------------------------------------------
  readonly payments = signal<CommissionPaymentResponse[]>([]);
  readonly paymentsPageIndex = signal(0);
  readonly paymentsTotalElements = signal(0);
  readonly paymentsTotalPages = signal(0);
  readonly paymentsLoading = signal(true);
  readonly paymentsError = signal<string | null>(null);
  readonly paymentsPageSize = 15;

  constructor() {
    this.fetchPartners();
    this.fetchPayments();
  }

  // Partners ---------------------------------------------------------

  fetchPartners(): void {
    this.partnersLoading.set(true);
    this.partnersError.set(null);
    this.commissionService.getPartnersSummary().subscribe({
      next: (partners) => {
        this.partners.set(partners);
        this.partnersLoading.set(false);
        this.openFromDeepLink();
      },
      error: () => {
        this.partnersError.set('Impossible de charger les partenaires.');
        this.partnersLoading.set(false);
      },
    });
  }

  onPartnersSearch(value: string): void {
    this.partnersSearch.set(value);
  }

  /** Supports "Voir les commissions" links from the Partenaire d'affaire page:
   * /commissions?partenaireId=X opens that partner's contracts modal directly. */
  private openFromDeepLink(): void {
    const raw = this.route.snapshot.queryParamMap.get('partenaireId');
    if (!raw) return;
    const id = Number(raw);
    const partner = this.partners().find((p) => p.partenaireId === id);
    if (partner) {
      this.openContracts(partner);
    }
    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }

  // Contracts modal ----------------------------------------------------

  openContracts(partner: PartnerCommissionSummary): void {
    this.selectedPartner.set(partner);
    this.selectedContractIds.set(new Set());
    this.paymentReference.set('');
    this.payError.set(null);
    this.payBlockedTransactions.set([]);
    this.contractsStatusFilter.set('pending');
    this.contractsModalTab.set('contrats');
    this.contractsModalVisible.set(true);
    this.fetchContracts();
    this.fetchPartialPaymentStatus();
  }

  setContractsModalTab(tab: ContractsModalTab): void {
    this.contractsModalTab.set(tab);
  }

  fetchPartialPaymentStatus(): void {
    const partner = this.selectedPartner();
    if (!partner) return;
    this.partialPaymentLoading.set(true);
    this.partialPaymentError.set(null);
    this.commissionService.getPartialPaymentStatus(partner.partenaireId).subscribe({
      next: (status) => {
        this.partialPaymentStatus.set(status);
        this.partialPaymentLoading.set(false);
      },
      error: () => {
        this.partialPaymentError.set('Impossible de charger les demandes de versement anticipé.');
        this.partialPaymentLoading.set(false);
      },
    });
  }

  validatePartialPayment(id: number): void {
    this.validatingRequestId.set(id);
    this.commissionService.validatePartialPaymentRequest(id).subscribe({
      next: () => {
        this.validatingRequestId.set(null);
        this.fetchPartialPaymentStatus();
        this.fetchPartners();
        this.fetchPayments();
      },
      error: () => {
        this.validatingRequestId.set(null);
        this.partialPaymentError.set('Impossible de valider cette demande.');
      },
    });
  }

  closeContractsModal(): void {
    this.contractsModalVisible.set(false);
  }

  fetchContracts(): void {
    const partner = this.selectedPartner();
    if (!partner) return;
    this.contractsLoading.set(true);
    this.contractsError.set(null);
    this.commissionService.getPartnerContracts(partner.partenaireId).subscribe({
      next: (contracts) => {
        this.contracts.set(contracts);
        this.contractsLoading.set(false);
      },
      error: () => {
        this.contractsError.set('Impossible de charger les contrats de ce partenaire.');
        this.contractsLoading.set(false);
      },
    });
  }

  setContractsStatusFilter(filter: ContractStatusFilter): void {
    this.contractsStatusFilter.set(filter);
    // Évite qu'une sélection faite sous "En attente" reste active (et payable) une fois
    // basculé sur "Payés"/"Tous" — seuls les contrats en attente sont payables.
    this.selectedContractIds.set(new Set());
  }

  isSelected(contractId: number): boolean {
    return this.selectedContractIds().has(contractId);
  }

  toggleContract(contractId: number): void {
    const set = new Set(this.selectedContractIds());
    if (set.has(contractId)) {
      set.delete(contractId);
    } else {
      set.add(contractId);
    }
    this.selectedContractIds.set(set);
  }

  toggleSelectAllPending(): void {
    this.selectedContractIds.set(this.allPendingSelected() ? new Set() : new Set(this.visiblePendingIds()));
  }

  onReferenceInput(value: string): void {
    this.paymentReference.set(value);
  }

  payCommission(): void {
    const contractIds = Array.from(this.selectedContractIds());
    if (contractIds.length === 0) return;
    this.paying.set(true);
    this.payError.set(null);
    this.payBlockedTransactions.set([]);
    this.commissionService.pay({ contractIds, reference: this.paymentReference().trim() || undefined }).subscribe({
      next: () => {
        this.paying.set(false);
        this.selectedContractIds.set(new Set());
        this.paymentReference.set('');
        this.fetchContracts();
        this.fetchPartners();
        this.fetchPayments();
      },
      // 409 depuis le 2026-09-23 si une transaction SycaPay est active/réussie pour ce contrat
      // (garde anti double paiement manuel + automatique) : afficher le motif du backend.
      error: (err: unknown) => {
        this.paying.set(false);
        this.payError.set(sycapayErrorMessage(err, 'Impossible de payer cette commission. Réessayez.', 'pay'));
        if (err instanceof HttpErrorResponse && err.error?.code === 'SYCAPAY_DUPLICATE_PAYMENT') {
          this.loadBlockedTransactions(contractIds);
        }
      },
    });
  }

  /** Le 409 ne dit ni quel contrat ni quel statut : on retrouve la transaction de chaque contrat
   * sélectionné (filtre contractId, au plus une par contrat) pour proposer la bonne action. */
  private loadBlockedTransactions(contractIds: number[]): void {
    const policyById = new Map(this.contracts().map((c) => [c.contractId, c.policyNumber]));
    forkJoin(
      contractIds.map((id) => this.sycapayService.getContractTransaction(id).pipe(catchError(() => of(null)))),
    ).subscribe((transactions) => {
      this.payBlockedTransactions.set(
        transactions
          .filter((t): t is SycaPayTransaction => !!t && OPEN_TRANSACTION_STATUSES.has(t.status))
          .map((t) => ({
            transactionId: t.id,
            policyNumber: policyById.get(t.contractId) ?? `#${t.contractId}`,
            status: t.status,
            reconcilable: t.status === 'PROCESSING' || t.status === 'UNKNOWN',
          })),
      );
    });
  }

  // Payment history ----------------------------------------------------

  fetchPayments(): void {
    this.paymentsLoading.set(true);
    this.paymentsError.set(null);
    this.commissionService.getPayments(this.paymentsPageIndex(), this.paymentsPageSize).subscribe({
      next: (result) => {
        this.payments.set(result.content);
        this.paymentsTotalElements.set(result.totalElements);
        this.paymentsTotalPages.set(result.totalPages);
        this.paymentsLoading.set(false);
      },
      error: () => {
        this.paymentsError.set("Impossible de charger l'historique des paiements.");
        this.paymentsLoading.set(false);
      },
    });
  }

  goToPaymentsPage(index: number): void {
    if (index < 0 || index >= this.paymentsTotalPages()) return;
    this.paymentsPageIndex.set(index);
    this.fetchPayments();
  }

  // Formatting -----------------------------------------------------------

  typeLabel(type: TypePartenaire): string {
    if (type === 'DIRECT') return 'Direct';
    if (type === 'AGENT') return 'Agent';
    return 'Apporteur';
  }

  typeVariant(type: TypePartenaire): string {
    if (type === 'DIRECT') return 'info';
    if (type === 'AGENT') return 'neutral';
    return 'warning';
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  /** Statut du virement SycaPay d'un paiement automatique — affiché seulement hors SUCCESS,
   * pour qu'un virement échoué ou indéterminé ne passe pas pour un paiement réglé. */
  sycapayStatusLabel(status: SycaPayStatus): string {
    return SYCAPAY_STATUS_LABEL[status];
  }

  sycapayStatusVariant(status: SycaPayStatus): string {
    return SYCAPAY_STATUS_VARIANT[status];
  }

  formatDate(value: string): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }
}
