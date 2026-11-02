import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { FundOutflowService } from '../../core/fund-outflows/fund-outflow.service';
import { FundOutflowTypeService } from '../../core/fund-outflows/fund-outflow-type.service';
import { LoanService } from '../../core/fund-outflows/loan.service';
import {
  DisbursementMethod,
  FundOutflowPriority,
  FundOutflowResponse,
  FundOutflowStatus,
  LoanRepaymentFrequency,
} from '../../core/fund-outflows/models/fund-outflow.model';
import { FundOutflowTypeResponse } from '../../core/fund-outflows/models/fund-outflow-type.model';
import {
  LoanInstallmentResponse,
  LoanRepaymentResponse,
  LoanResponse,
} from '../../core/fund-outflows/models/loan.model';
import { HistoriqueModificationResponse } from '../../core/audit-trail/models/audit-trail.model';
import { AUDIT_ACTION_LABEL, AUDIT_ACTION_VARIANT } from '../audit-trail/audit-trail-status';
import { PartenaireService } from '../../core/partenaire/partenaire.service';
import { PartenaireResponse } from '../../core/partenaire/models/partenaire.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { AuthService } from '../../core/auth/auth.service';

type View = 'sorties' | 'prets' | 'types';
type RefActifFilter = 'all' | 'active' | 'inactive';
const PAGE_SIZE = 20;

const STATUS_LABEL: Record<FundOutflowStatus, string> = {
  DRAFT: 'Brouillon',
  SUBMITTED: 'Soumise',
  UNDER_REVIEW: "En cours d'examen",
  APPROVED: 'Approuvée',
  REJECTED: 'Rejetée',
  CANCELLED: 'Annulée',
  AUTHORIZED: 'Autorisée',
  PROCESSING: 'Décaissement en cours',
  DISBURSED: 'Décaissée',
  PARTIALLY_REPAID: 'Partiellement remboursée',
  REPAID: 'Remboursée',
  CLOSED: 'Clôturée',
};

const STATUS_VARIANT: Record<FundOutflowStatus, string> = {
  DRAFT: 'neutral',
  SUBMITTED: 'info',
  UNDER_REVIEW: 'warning',
  APPROVED: 'info',
  REJECTED: 'danger',
  CANCELLED: 'danger',
  AUTHORIZED: 'info',
  PROCESSING: 'warning',
  DISBURSED: 'success',
  PARTIALLY_REPAID: 'warning',
  REPAID: 'success',
  CLOSED: 'neutral',
};

const PRIORITY_LABEL: Record<FundOutflowPriority, string> = {
  LOW: 'Basse',
  NORMAL: 'Normale',
  HIGH: 'Haute',
  URGENT: 'Urgente',
};

/** Options des pastilles "Priorité" du formulaire de création — mêmes variantes sémantiques
 * .dl-chip (success/info/warning/danger) que la confidentialité GED. */
const PRIORITY_OPTIONS: { value: FundOutflowPriority; label: string; variant: string }[] = [
  { value: 'LOW', label: 'Basse', variant: 'success' },
  { value: 'NORMAL', label: 'Normale', variant: 'info' },
  { value: 'HIGH', label: 'Haute', variant: 'warning' },
  { value: 'URGENT', label: 'Urgente', variant: 'danger' },
];

const DISBURSEMENT_METHOD_LABEL: Record<DisbursementMethod, string> = {
  BANK_TRANSFER: 'Virement bancaire',
  ORANGE_MONEY: 'Orange Money',
  MTN_MONEY: 'MTN Money',
  MOOV_MONEY: 'Moov Money',
  WAVE: 'Wave',
  CASH: 'Espèces',
  CHEQUE: 'Chèque',
  OTHER: 'Autre',
};

const INSTALLMENT_STATUS_LABEL: Record<string, string> = {
  PENDING: 'En attente',
  PARTIALLY_PAID: 'Partiellement payée',
  PAID: 'Payée',
  OVERDUE: 'En retard',
  CANCELLED: 'Annulée',
};

const INSTALLMENT_STATUS_VARIANT: Record<string, string> = {
  PENDING: 'neutral',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
  OVERDUE: 'danger',
  CANCELLED: 'neutral',
};

@Component({
  selector: 'app-fund-outflows',
  templateUrl: './fund-outflows.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    FormCheckInputDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class FundOutflowsComponent {
  private readonly outflowService = inject(FundOutflowService);
  private readonly typeService = inject(FundOutflowTypeService);
  private readonly loanService = inject(LoanService);
  private readonly partenaireService = inject(PartenaireService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  /** Module réservé ADMIN côté backend depuis le durcissement IDOR du 2026-09-12 (voir
   * docs/api-contracts/securisation-idor-2026-09-12.md §2) — lecture des sorties de fonds/prêts
   * en 403 pour tout le reste. Masquage d'affichage uniquement, le vrai rempart reste ce 403
   * backend (même choix que Tarifs/Livraisons dans VignettesMotoComponent). */
  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');

  readonly view = signal<View>('sorties');
  readonly statusLabel = STATUS_LABEL;
  readonly priorityLabel = PRIORITY_LABEL;
  readonly priorityOptions = PRIORITY_OPTIONS;
  readonly disbursementMethodLabel = DISBURSEMENT_METHOD_LABEL;
  readonly installmentStatusLabel = INSTALLMENT_STATUS_LABEL;
  readonly actionLabel = AUDIT_ACTION_LABEL;
  readonly actionVariant = AUDIT_ACTION_VARIANT;

  setView(view: View): void {
    this.view.set(view);
    if (view === 'types' && this.types().length === 0) this.fetchTypes();
    // Sans cet appel, l'onglet Prêts reste bloqué sur "Chargement…" indéfiniment au premier
    // affichage : loansLoading vaut true dès la déclaration du signal et rien ne le résout tant
    // que fetchLoans() n'a pas tourné une première fois (le bouton Actualiser est lui-même
    // désactivé pendant ce faux chargement, donc inutilisable pour s'en sortir).
    if (view === 'prets' && this.loans().length === 0) this.fetchLoans();
  }

  constructor() {
    if (!this.isAdmin()) return;
    this.fetchOutflows();
    this.fetchTypes();
  }

  statusVariant(status: FundOutflowStatus): string {
    return STATUS_VARIANT[status];
  }

  installmentStatusVariant(status: string): string {
    return INSTALLMENT_STATUS_VARIANT[status] ?? 'neutral';
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  // ================================================================ RÉFÉRENTIEL : TYPES DE SORTIE ================================================================

  readonly types = signal<FundOutflowTypeResponse[]>([]);
  readonly typesLoading = signal(false);
  readonly typesError = signal<string | null>(null);
  readonly typesSearch = signal('');
  readonly typesActifFilter = signal<RefActifFilter>('all');

  readonly filteredTypes = computed(() => {
    const query = this.typesSearch().trim().toLowerCase();
    const actif = this.typesActifFilter();
    return this.types()
      .filter((t) => actif === 'all' || t.actif === (actif === 'active'))
      .filter((t) => !query || `${t.code} ${t.nom}`.toLowerCase().includes(query))
      .sort((a, b) => (a.ordre ?? 0) - (b.ordre ?? 0));
  });

  fetchTypes(): void {
    this.typesLoading.set(true);
    this.typesError.set(null);
    this.typeService.list(0, 200).subscribe({
      next: (page) => {
        this.types.set(page.content);
        this.typesLoading.set(false);
      },
      error: () => {
        this.typesError.set('Impossible de charger les types de sortie.');
        this.typesLoading.set(false);
      },
    });
  }

  readonly typeFormVisible = signal(false);
  readonly typeFormSaving = signal(false);
  readonly typeFormError = signal<string | null>(null);
  readonly editingTypeId = signal<number | null>(null);
  readonly typeForm = this.fb.nonNullable.group({ code: [''], nom: [''], description: [''], ordre: [0], actif: [true] });

  openCreateType(): void {
    this.editingTypeId.set(null);
    this.typeFormError.set(null);
    this.typeForm.reset({ code: '', nom: '', description: '', ordre: 0, actif: true });
    this.typeFormVisible.set(true);
  }

  openEditType(item: FundOutflowTypeResponse): void {
    this.editingTypeId.set(item.id);
    this.typeFormError.set(null);
    this.typeForm.reset({ code: item.code, nom: item.nom, description: item.description ?? '', ordre: item.ordre ?? 0, actif: item.actif });
    this.typeFormVisible.set(true);
  }

  saveType(): void {
    const raw = this.typeForm.getRawValue();
    if (!raw.code.trim() || !raw.nom.trim()) {
      this.typeFormError.set('Le code et le nom sont requis.');
      return;
    }
    this.typeFormSaving.set(true);
    this.typeFormError.set(null);
    const payload = { code: raw.code.trim(), nom: raw.nom.trim(), description: raw.description.trim() || undefined, ordre: Number(raw.ordre) || undefined, actif: raw.actif };
    const id = this.editingTypeId();
    const request = id ? this.typeService.update(id, payload) : this.typeService.create(payload);
    request.subscribe({
      next: () => {
        this.typeFormSaving.set(false);
        this.typeFormVisible.set(false);
        this.fetchTypes();
      },
      error: () => {
        this.typeFormSaving.set(false);
        this.typeFormError.set("Impossible d'enregistrer ce type de sortie.");
      },
    });
  }

  readonly deleteTypeTarget = signal<FundOutflowTypeResponse | null>(null);
  readonly deletingType = signal(false);

  confirmDeleteType(item: FundOutflowTypeResponse): void {
    if (item.systeme) return;
    this.deleteTypeTarget.set(item);
  }

  performDeleteType(): void {
    const item = this.deleteTypeTarget();
    if (!item) return;
    this.deletingType.set(true);
    this.typeService.delete(item.id).subscribe({
      next: () => {
        this.deletingType.set(false);
        this.deleteTypeTarget.set(null);
        this.fetchTypes();
      },
      error: () => {
        this.deletingType.set(false);
        this.deleteTypeTarget.set(null);
        this.typesError.set('Impossible de supprimer ce type de sortie.');
      },
    });
  }

  /** Vrai si le type d'id donné est le type système "LOAN" — déclenche l'affichage des champs
   * prêt (taux, dates, fréquence, échéances) dans le formulaire de création. */
  isLoanType(outflowTypeId: string): boolean {
    const type = this.types().find((t) => String(t.id) === outflowTypeId);
    return type?.code === 'LOAN';
  }

  // ================================================================ SORTIES DE FONDS ================================================================

  readonly outflows = signal<FundOutflowResponse[]>([]);
  readonly outflowsLoading = signal(true);
  readonly outflowsError = signal<string | null>(null);
  readonly outflowsPage = signal(0);
  readonly outflowsTotalPages = signal(1);
  readonly outflowsTotalElements = signal(0);

  readonly referenceFilter = signal('');
  readonly typeFilter = signal('');
  readonly statusFilter = signal<'all' | FundOutflowStatus>('all');
  readonly priorityFilter = signal<'all' | FundOutflowPriority>('all');
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  fetchOutflows(): void {
    this.outflowsLoading.set(true);
    this.outflowsError.set(null);
    const status = this.statusFilter();
    const priority = this.priorityFilter();
    this.outflowService
      .search(this.outflowsPage(), PAGE_SIZE, {
        reference: this.referenceFilter() || undefined,
        outflowTypeId: this.typeFilter() ? Number(this.typeFilter()) : undefined,
        status: status === 'all' ? undefined : status,
        priority: priority === 'all' ? undefined : priority,
      })
      .subscribe({
        next: (page) => {
          this.outflows.set(page.content);
          this.outflowsTotalPages.set(Math.max(1, page.totalPages));
          this.outflowsTotalElements.set(page.totalElements);
          this.outflowsLoading.set(false);
        },
        error: () => {
          this.outflowsError.set('Impossible de charger les sorties de fonds.');
          this.outflowsLoading.set(false);
        },
      });
  }

  onReferenceFilterInput(value: string): void {
    this.referenceFilter.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.outflowsPage.set(0);
      this.fetchOutflows();
    }, 400);
  }

  setTypeFilter(value: string): void {
    this.typeFilter.set(value);
    this.outflowsPage.set(0);
    this.fetchOutflows();
  }

  setStatusFilter(value: string): void {
    this.statusFilter.set(value as 'all' | FundOutflowStatus);
    this.outflowsPage.set(0);
    this.fetchOutflows();
  }

  setPriorityFilter(value: string): void {
    this.priorityFilter.set(value as 'all' | FundOutflowPriority);
    this.outflowsPage.set(0);
    this.fetchOutflows();
  }

  resetFilters(): void {
    this.referenceFilter.set('');
    this.typeFilter.set('');
    this.statusFilter.set('all');
    this.priorityFilter.set('all');
    this.outflowsPage.set(0);
    this.fetchOutflows();
  }

  goToOutflowsPage(index: number): void {
    if (index < 0 || index >= this.outflowsTotalPages()) return;
    this.outflowsPage.set(index);
    this.fetchOutflows();
  }

  // -- création --

  readonly createFormVisible = signal(false);
  readonly createSaving = signal(false);
  readonly createError = signal<string | null>(null);
  readonly createForm = this.fb.nonNullable.group({
    outflowTypeId: [''],
    amount: [''],
    reason: [''],
    beneficiaryType: [''],
    beneficiaryId: [''],
    beneficiaryLabel: [''],
    partnerSearchQuery: [''],
    priority: ['NORMAL' as FundOutflowPriority],
    dueDate: [''],
    description: [''],
    loanInterestRate: [''],
    loanStartDate: [''],
    loanExpectedEndDate: [''],
    loanRepaymentFrequency: ['MONTHLY'],
    loanNumberOfInstallments: [''],
  });

  readonly resolvingBeneficiary = signal(false);
  readonly resolvedBeneficiaryLabel = signal<string | null>(null);
  readonly editingOutflowId = signal<number | null>(null);

  // -- recherche partenaire par autocomplétion (type de sortie = LOAN uniquement — pour les
  // autres types, le bénéficiaire reste en saisie libre, voir resolveBeneficiary ci-dessous) --
  readonly selectedPartner = signal<PartenaireResponse | null>(null);
  readonly partnerSearchOpen = signal(false);
  readonly partnerSearchLoading = signal(false);
  readonly partnerSearchError = signal<string | null>(null);
  readonly partnerSearchResults = signal<PartenaireResponse[]>([]);
  private partnerSearchDebounce: ReturnType<typeof setTimeout> | null = null;

  onPartnerSearchInput(value: string): void {
    this.selectedPartner.set(null);
    this.createForm.patchValue({ beneficiaryId: '', beneficiaryLabel: '' });
    if (this.partnerSearchDebounce) clearTimeout(this.partnerSearchDebounce);

    const query = value.trim();
    if (query.length < 2) {
      this.partnerSearchResults.set([]);
      this.partnerSearchError.set(null);
      this.partnerSearchOpen.set(query.length > 0);
      return;
    }
    this.partnerSearchOpen.set(true);
    this.partnerSearchDebounce = setTimeout(() => this.searchPartners(query), 400);
  }

  private searchPartners(query: string): void {
    this.partnerSearchLoading.set(true);
    this.partnerSearchError.set(null);
    this.partenaireService.list(0, 6, query).subscribe({
      next: (page) => {
        this.partnerSearchResults.set(page.content);
        this.partnerSearchLoading.set(false);
      },
      error: () => {
        this.partnerSearchResults.set([]);
        this.partnerSearchError.set('Recherche indisponible pour le moment.');
        this.partnerSearchLoading.set(false);
      },
    });
  }

  selectPartner(partner: PartenaireResponse): void {
    this.createForm.patchValue({
      partnerSearchQuery: `${partner.codePartenaire ?? ''} — ${partner.nom} ${partner.prenoms}`.trim(),
      beneficiaryType: 'PARTNER',
      beneficiaryId: String(partner.id),
      beneficiaryLabel: `${partner.nom} ${partner.prenoms}`,
    });
    this.selectedPartner.set(partner);
    this.partnerSearchOpen.set(false);
    this.partnerSearchResults.set([]);
  }

  clearPartnerSelection(): void {
    this.createForm.patchValue({ partnerSearchQuery: '', beneficiaryType: 'PARTNER', beneficiaryId: '', beneficiaryLabel: '' });
    this.selectedPartner.set(null);
    this.partnerSearchResults.set([]);
    this.partnerSearchOpen.set(false);
    this.partnerSearchError.set(null);
  }

  private resetPartnerSearch(): void {
    this.selectedPartner.set(null);
    this.partnerSearchResults.set([]);
    this.partnerSearchOpen.set(false);
    this.partnerSearchError.set(null);
  }

  // -- suggestions de dates rapides (échéance, début et fin prévue de prêt) — mêmes classes
  // dl-autocomplete-* que la recherche partenaire, mais liste statique plutôt qu'une recherche
  // réseau ; un signal "open" distinct par champ car les 3 peuvent être visibles en même temps --
  readonly dateShortcuts: { label: string; days: number | 'end-of-month' }[] = [
    { label: '+7 jours', days: 7 },
    { label: '+15 jours', days: 15 },
    { label: 'Fin du mois', days: 'end-of-month' },
    { label: '+30 jours', days: 30 },
  ];
  readonly dueDateShortcutsOpen = signal(false);
  readonly loanStartShortcutsOpen = signal(false);
  readonly loanEndShortcutsOpen = signal(false);

  private computeShortcutDate(days: number | 'end-of-month'): string {
    const today = new Date();
    const target = days === 'end-of-month' ? new Date(today.getFullYear(), today.getMonth() + 1, 0) : new Date(today.getFullYear(), today.getMonth(), today.getDate() + days);
    return `${target.getFullYear()}-${String(target.getMonth() + 1).padStart(2, '0')}-${String(target.getDate()).padStart(2, '0')}`;
  }

  applyDueDateShortcut(days: number | 'end-of-month'): void {
    this.createForm.controls.dueDate.setValue(this.computeShortcutDate(days));
    this.dueDateShortcutsOpen.set(false);
  }

  applyLoanStartShortcut(days: number | 'end-of-month'): void {
    this.createForm.controls.loanStartDate.setValue(this.computeShortcutDate(days));
    this.loanStartShortcutsOpen.set(false);
  }

  applyLoanEndShortcut(days: number | 'end-of-month'): void {
    this.createForm.controls.loanExpectedEndDate.setValue(this.computeShortcutDate(days));
    this.loanEndShortcutsOpen.set(false);
  }

  openCreate(): void {
    this.editingOutflowId.set(null);
    this.createError.set(null);
    this.resolvedBeneficiaryLabel.set(null);
    this.resetPartnerSearch();
    this.dueDateShortcutsOpen.set(false);
    this.loanStartShortcutsOpen.set(false);
    this.loanEndShortcutsOpen.set(false);
    this.createForm.reset({
      outflowTypeId: '',
      amount: '',
      reason: '',
      beneficiaryType: '',
      beneficiaryId: '',
      beneficiaryLabel: '',
      partnerSearchQuery: '',
      priority: 'NORMAL',
      dueDate: '',
      description: '',
      loanInterestRate: '',
      loanStartDate: '',
      loanExpectedEndDate: '',
      loanRepaymentFrequency: 'MONTHLY',
      loanNumberOfInstallments: '',
    });
    this.createFormVisible.set(true);
  }

  /** Une sortie DRAFT ne peut être modifiée que sur ses champs de base — pas les champs prêt,
   * absents d'UpdateFundOutflowRequest (le prêt n'existe de toute façon pas encore à ce stade). */
  openEditOutflow(outflow: FundOutflowResponse): void {
    this.editingOutflowId.set(outflow.id);
    this.createError.set(null);
    this.resolvedBeneficiaryLabel.set(null);
    this.resetPartnerSearch();
    this.dueDateShortcutsOpen.set(false);
    this.loanStartShortcutsOpen.set(false);
    this.loanEndShortcutsOpen.set(false);
    this.createForm.reset({
      outflowTypeId: String(outflow.outflowTypeId),
      amount: String(outflow.amount),
      reason: outflow.reason,
      beneficiaryType: outflow.beneficiaryType ?? '',
      beneficiaryId: outflow.beneficiaryId ? String(outflow.beneficiaryId) : '',
      beneficiaryLabel: outflow.beneficiaryLabel ?? '',
      partnerSearchQuery: '',
      priority: outflow.priority ?? 'NORMAL',
      dueDate: outflow.dueDate ?? '',
      description: outflow.description ?? '',
      loanInterestRate: '',
      loanStartDate: '',
      loanExpectedEndDate: '',
      loanRepaymentFrequency: 'MONTHLY',
      loanNumberOfInstallments: '',
    });
    this.createFormVisible.set(true);
    // Le bénéficiaire d'une sortie LOAN existante est déjà un partenaire réel — on va chercher
    // ses infos pour afficher la recherche déjà "remplie" plutôt qu'un champ vide malgré une
    // sélection existante.
    if (this.isLoanType(String(outflow.outflowTypeId)) && outflow.beneficiaryId) {
      this.partenaireService.get(outflow.beneficiaryId).subscribe({
        next: (p) => {
          this.selectedPartner.set(p);
          this.createForm.controls.partnerSearchQuery.setValue(`${p.codePartenaire ?? ''} — ${p.nom} ${p.prenoms}`.trim());
        },
        error: () => {},
      });
    }
  }

  onCreateTypeChange(value: string): void {
    this.createForm.controls.outflowTypeId.setValue(value);
    if (this.isLoanType(value) && !this.createForm.controls.beneficiaryType.value) {
      this.createForm.controls.beneficiaryType.setValue('PARTNER');
    }
    if (!this.isLoanType(value)) this.resetPartnerSearch();
  }

  /** Aperçu pour le bénéficiaire en saisie libre (types autres que LOAN) — n'est tenté que si
   * beneficiaryType ressemble à un partenaire, car c'est le seul référentiel avec un .get(id)
   * déjà disponible côté frontend pour ce module. Pour LOAN, voir la recherche dédiée ci-dessus. */
  resolveBeneficiary(): void {
    const type = this.createForm.controls.beneficiaryType.value.trim().toUpperCase();
    const idRaw = this.createForm.controls.beneficiaryId.value;
    const id = Number(idRaw);
    if (!idRaw || !Number.isFinite(id)) return;
    this.resolvedBeneficiaryLabel.set(null);
    if (type !== 'PARTNER' && type !== 'PARTENAIRE') return;
    this.resolvingBeneficiary.set(true);
    this.partenaireService.get(id).subscribe({
      next: (p) => {
        this.resolvingBeneficiary.set(false);
        this.resolvedBeneficiaryLabel.set(`${p.codePartenaire ?? ''} — ${p.nom} ${p.prenoms}`.trim());
      },
      error: () => {
        this.resolvingBeneficiary.set(false);
      },
    });
  }

  submitCreate(): void {
    const raw = this.createForm.getRawValue();
    if (!raw.outflowTypeId || !raw.amount || !raw.reason.trim()) {
      this.createError.set('Le type, le montant et le motif sont requis.');
      return;
    }
    const editingId = this.editingOutflowId();
    // Confirmé en direct le 2026-09-03 : les champs prêt sont optionnels à la création (créer sans
    // eux fonctionne), mais deviennent obligatoires au décaissement (400 explicite sinon, message
    // qui renvoie vers PUT) — donc plus de blocage ici, seulement une option pour les saisir tout
    // de suite ou plus tard via "Modifier" tant que le statut reste DRAFT.
    const isLoan = this.isLoanType(raw.outflowTypeId);
    if (isLoan && !raw.beneficiaryId) {
      this.createError.set('Un prêt doit avoir un partenaire bénéficiaire — recherchez-le ci-dessus.');
      return;
    }
    this.createSaving.set(true);
    this.createError.set(null);
    const basePayload = {
      outflowTypeId: Number(raw.outflowTypeId),
      amount: Number(raw.amount),
      reason: raw.reason.trim(),
      beneficiaryType: raw.beneficiaryType.trim() || undefined,
      beneficiaryId: raw.beneficiaryId ? Number(raw.beneficiaryId) : undefined,
      beneficiaryLabel: raw.beneficiaryLabel.trim() || undefined,
      priority: raw.priority,
      dueDate: raw.dueDate || undefined,
      description: raw.description.trim() || undefined,
      loanInterestRate: isLoan && raw.loanInterestRate ? Number(raw.loanInterestRate) : undefined,
      loanStartDate: isLoan ? raw.loanStartDate || undefined : undefined,
      loanExpectedEndDate: isLoan ? raw.loanExpectedEndDate || undefined : undefined,
      loanRepaymentFrequency: isLoan ? (raw.loanRepaymentFrequency as LoanRepaymentFrequency) : undefined,
      loanNumberOfInstallments: isLoan && raw.loanNumberOfInstallments ? Number(raw.loanNumberOfInstallments) : undefined,
    };
    const request$ = editingId ? this.outflowService.update(editingId, basePayload) : this.outflowService.create(basePayload);
    request$.subscribe({
      next: (updated) => {
        this.createSaving.set(false);
        this.createFormVisible.set(false);
        if (editingId) this.applyUpdate(updated);
        this.fetchOutflows();
      },
      error: () => {
        this.createSaving.set(false);
        this.createError.set(editingId ? 'Impossible de modifier cette sortie de fonds.' : "Impossible de créer cette sortie de fonds.");
      },
    });
  }

  // -- détail & workflow --

  readonly detailVisible = signal(false);
  readonly selectedOutflow = signal<FundOutflowResponse | null>(null);
  readonly detailActionPending = signal(false);
  readonly detailActionError = signal<string | null>(null);
  readonly history = signal<HistoriqueModificationResponse[]>([]);
  readonly historyLoading = signal(false);

  openDetail(outflow: FundOutflowResponse): void {
    this.selectedOutflow.set(outflow);
    this.detailActionError.set(null);
    this.detailVisible.set(true);
    this.fetchHistory(outflow.id);
  }

  closeDetail(): void {
    this.detailVisible.set(false);
    this.selectedOutflow.set(null);
  }

  private fetchHistory(id: number): void {
    this.historyLoading.set(true);
    this.outflowService.getHistory(id, 0, 50).subscribe({
      next: (page) => {
        this.history.set(page.content);
        this.historyLoading.set(false);
      },
      error: () => this.historyLoading.set(false),
    });
  }

  private applyUpdate(updated: FundOutflowResponse): void {
    this.selectedOutflow.set(updated);
    this.outflows.set(this.outflows().map((o) => (o.id === updated.id ? updated : o)));
    // L'historique n'est chargé qu'une fois à l'ouverture du détail — le rafraîchir après chaque
    // action de workflow évite qu'il reste figé sur la seule entrée CRÉATION.
    this.fetchHistory(updated.id);
  }

  canSubmit = (s: FundOutflowStatus) => s === 'DRAFT';
  canReview = (s: FundOutflowStatus) => s === 'SUBMITTED';
  canApprove = (s: FundOutflowStatus) => s === 'UNDER_REVIEW';
  canReject = (s: FundOutflowStatus) => s === 'UNDER_REVIEW';
  canAuthorize = (s: FundOutflowStatus) => s === 'APPROVED';
  canDisburse = (s: FundOutflowStatus) => s === 'AUTHORIZED';
  canCancel = (s: FundOutflowStatus) => s === 'DRAFT' || s === 'SUBMITTED' || s === 'UNDER_REVIEW';
  canClose = (s: FundOutflowStatus) => s === 'DISBURSED' || s === 'REPAID' || s === 'CANCELLED' || s === 'REJECTED';
  canDelete = (s: FundOutflowStatus) => s === 'DRAFT' || s === 'CANCELLED' || s === 'REJECTED';
  canEdit = (s: FundOutflowStatus) => s === 'DRAFT';

  private runAction(action: 'submit' | 'review' | 'approve' | 'authorize', outflow: FundOutflowResponse): void {
    this.detailActionPending.set(true);
    this.detailActionError.set(null);
    this.outflowService[action](outflow.id).subscribe({
      next: (updated) => {
        this.detailActionPending.set(false);
        this.applyUpdate(updated);
      },
      error: () => {
        this.detailActionPending.set(false);
        this.detailActionError.set("Impossible d'effectuer cette action.");
      },
    });
  }

  doSubmit(outflow: FundOutflowResponse): void {
    this.runAction('submit', outflow);
  }

  doReview(outflow: FundOutflowResponse): void {
    this.runAction('review', outflow);
  }

  doApprove(outflow: FundOutflowResponse): void {
    this.runAction('approve', outflow);
  }

  doAuthorize(outflow: FundOutflowResponse): void {
    this.runAction('authorize', outflow);
  }

  doClose(outflow: FundOutflowResponse): void {
    this.detailActionPending.set(true);
    this.detailActionError.set(null);
    this.outflowService.close(outflow.id).subscribe({
      next: (updated) => {
        this.detailActionPending.set(false);
        this.applyUpdate(updated);
      },
      error: () => {
        this.detailActionPending.set(false);
        this.detailActionError.set('Impossible de clôturer cette sortie de fonds.');
      },
    });
  }

  // -- rejet / annulation (motif obligatoire) --

  readonly reasonModalMode = signal<'reject' | 'cancel' | null>(null);
  readonly reasonForm = this.fb.nonNullable.group({ reason: [''] });
  readonly reasonSaving = signal(false);
  readonly reasonError = signal<string | null>(null);

  askReject(): void {
    this.reasonError.set(null);
    this.reasonForm.reset({ reason: '' });
    this.reasonModalMode.set('reject');
  }

  askCancel(): void {
    this.reasonError.set(null);
    this.reasonForm.reset({ reason: '' });
    this.reasonModalMode.set('cancel');
  }

  submitReason(): void {
    const outflow = this.selectedOutflow();
    const mode = this.reasonModalMode();
    const reason = this.reasonForm.getRawValue().reason.trim();
    if (!outflow || !mode) return;
    if (!reason) {
      this.reasonError.set('Le motif est obligatoire.');
      return;
    }
    this.reasonSaving.set(true);
    this.reasonError.set(null);
    const request$ = mode === 'reject' ? this.outflowService.reject(outflow.id, { reason }) : this.outflowService.cancel(outflow.id, { reason });
    request$.subscribe({
      next: (updated) => {
        this.reasonSaving.set(false);
        this.reasonModalMode.set(null);
        this.applyUpdate(updated);
      },
      error: () => {
        this.reasonSaving.set(false);
        this.reasonError.set('Impossible de finaliser cette action.');
      },
    });
  }

  // -- décaissement --

  readonly disburseFormVisible = signal(false);
  readonly disburseForm = this.fb.nonNullable.group({ method: ['BANK_TRANSFER' as DisbursementMethod], paymentReference: [''] });
  readonly disburseSaving = signal(false);
  readonly disburseError = signal<string | null>(null);

  askDisburse(): void {
    this.disburseError.set(null);
    this.disburseForm.reset({ method: 'BANK_TRANSFER', paymentReference: '' });
    this.disburseFormVisible.set(true);
  }

  submitDisburse(): void {
    const outflow = this.selectedOutflow();
    const raw = this.disburseForm.getRawValue();
    if (!outflow) return;
    this.disburseSaving.set(true);
    this.disburseError.set(null);
    const idempotencyKey = `disburse-${outflow.id}-${Date.now()}`;
    this.outflowService.disburse(outflow.id, { method: raw.method, paymentReference: raw.paymentReference.trim() || undefined }, idempotencyKey).subscribe({
      next: () => {
        this.disburseSaving.set(false);
        this.disburseFormVisible.set(false);
        this.outflowService.get(outflow.id).subscribe((updated) => this.applyUpdate(updated));
      },
      error: (err) => {
        this.disburseSaving.set(false);
        // Cas fréquent pour un prêt créé sans ses conditions : le backend renvoie un message
        // explicite ("...complétez-les via PUT /fund-outflows/{id}") plus utile que le générique.
        this.disburseError.set(err?.error?.message || 'Impossible de décaisser cette sortie de fonds.');
      },
    });
  }

  // -- suppression --

  readonly deleteOutflowTarget = signal<FundOutflowResponse | null>(null);
  readonly deletingOutflow = signal(false);

  confirmDeleteOutflow(outflow: FundOutflowResponse): void {
    this.deleteOutflowTarget.set(outflow);
  }

  performDeleteOutflow(): void {
    const outflow = this.deleteOutflowTarget();
    if (!outflow) return;
    this.deletingOutflow.set(true);
    this.outflowService.delete(outflow.id).subscribe({
      next: () => {
        this.deletingOutflow.set(false);
        this.deleteOutflowTarget.set(null);
        this.detailVisible.set(false);
        this.fetchOutflows();
      },
      error: () => {
        this.deletingOutflow.set(false);
        this.deleteOutflowTarget.set(null);
        this.outflowsError.set('Impossible de supprimer cette sortie de fonds.');
      },
    });
  }

  /** Bascule vers l'onglet Prêts et ouvre le prêt lié à la sortie de fonds affichée. */
  viewLinkedLoan(loanId: number): void {
    this.closeDetail();
    this.view.set('prets');
    this.loanService.get(loanId).subscribe({
      next: (loan) => this.openLoanDetail(loan),
      error: () => this.loansError.set('Impossible de charger le prêt lié.'),
    });
  }

  // ================================================================ PRÊTS ================================================================

  readonly loans = signal<LoanResponse[]>([]);
  readonly loansLoading = signal(true);
  readonly loansError = signal<string | null>(null);
  readonly loansPage = signal(0);
  readonly loansTotalPages = signal(1);
  readonly loansTotalElements = signal(0);
  readonly loanReferenceFilter = signal('');
  readonly overdueOnly = signal(false);
  private loanSearchDebounce: ReturnType<typeof setTimeout> | null = null;

  fetchLoans(): void {
    this.loansLoading.set(true);
    this.loansError.set(null);
    const request$ = this.overdueOnly()
      ? this.loanService.overdue(this.loansPage(), PAGE_SIZE)
      : this.loanService.search(this.loansPage(), PAGE_SIZE, { reference: this.loanReferenceFilter() || undefined });
    request$.subscribe({
      next: (page) => {
        this.loans.set(page.content);
        this.loansTotalPages.set(Math.max(1, page.totalPages));
        this.loansTotalElements.set(page.totalElements);
        this.loansLoading.set(false);
      },
      error: () => {
        this.loansError.set('Impossible de charger les prêts.');
        this.loansLoading.set(false);
      },
    });
  }

  onLoanReferenceFilterInput(value: string): void {
    this.loanReferenceFilter.set(value);
    if (this.loanSearchDebounce) clearTimeout(this.loanSearchDebounce);
    this.loanSearchDebounce = setTimeout(() => {
      this.loansPage.set(0);
      this.fetchLoans();
    }, 400);
  }

  toggleOverdueOnly(): void {
    this.overdueOnly.update((v) => !v);
    this.loansPage.set(0);
    this.fetchLoans();
  }

  goToLoansPage(index: number): void {
    if (index < 0 || index >= this.loansTotalPages()) return;
    this.loansPage.set(index);
    this.fetchLoans();
  }

  readonly loanDetailVisible = signal(false);
  readonly selectedLoan = signal<LoanResponse | null>(null);
  readonly installments = signal<LoanInstallmentResponse[]>([]);
  readonly installmentsLoading = signal(false);
  readonly repayments = signal<LoanRepaymentResponse[]>([]);
  readonly repaymentsLoading = signal(false);
  readonly loanActionError = signal<string | null>(null);

  openLoanDetail(loan: LoanResponse): void {
    this.selectedLoan.set(loan);
    this.loanActionError.set(null);
    this.loanDetailVisible.set(true);
    this.fetchInstallments(loan.id);
    this.fetchRepayments(loan.id);
  }

  closeLoanDetail(): void {
    this.loanDetailVisible.set(false);
    this.selectedLoan.set(null);
  }

  private fetchInstallments(loanId: number): void {
    this.installmentsLoading.set(true);
    this.loanService.getInstallments(loanId).subscribe({
      next: (list) => {
        this.installments.set(list);
        this.installmentsLoading.set(false);
      },
      error: () => this.installmentsLoading.set(false),
    });
  }

  private fetchRepayments(loanId: number): void {
    this.repaymentsLoading.set(true);
    this.loanService.getRepayments(loanId).subscribe({
      next: (list) => {
        this.repayments.set(list);
        this.repaymentsLoading.set(false);
      },
      error: () => this.repaymentsLoading.set(false),
    });
  }

  private refreshLoanAfterRepayment(loanId: number): void {
    this.loanService.get(loanId).subscribe({
      next: (updated) => {
        this.selectedLoan.set(updated);
        this.loans.set(this.loans().map((l) => (l.id === updated.id ? updated : l)));
      },
    });
    this.fetchInstallments(loanId);
    this.fetchRepayments(loanId);
  }

  // -- enregistrer un remboursement --

  readonly repaymentFormVisible = signal(false);
  readonly repaymentForm = this.fb.nonNullable.group({
    amount: [''],
    paymentDate: [new Date().toISOString().slice(0, 10)],
    paymentMethod: ['BANK_TRANSFER' as DisbursementMethod],
    paymentReference: [''],
    comment: [''],
  });
  readonly repaymentSaving = signal(false);
  readonly repaymentError = signal<string | null>(null);
  readonly repaymentDateShortcutsOpen = signal(false);

  applyRepaymentDateShortcut(days: number | 'end-of-month'): void {
    this.repaymentForm.controls.paymentDate.setValue(this.computeShortcutDate(days));
    this.repaymentDateShortcutsOpen.set(false);
  }

  openRepaymentForm(): void {
    this.repaymentError.set(null);
    this.repaymentDateShortcutsOpen.set(false);
    this.repaymentForm.reset({
      amount: '',
      paymentDate: new Date().toISOString().slice(0, 10),
      paymentMethod: 'BANK_TRANSFER',
      paymentReference: '',
      comment: '',
    });
    this.repaymentFormVisible.set(true);
  }

  submitRepayment(): void {
    const loan = this.selectedLoan();
    const raw = this.repaymentForm.getRawValue();
    if (!loan) return;
    if (!raw.amount || !raw.paymentDate) {
      this.repaymentError.set('Le montant et la date sont requis.');
      return;
    }
    this.repaymentSaving.set(true);
    this.repaymentError.set(null);
    this.loanService
      .createRepayment(loan.id, {
        amount: Number(raw.amount),
        paymentDate: raw.paymentDate,
        paymentMethod: raw.paymentMethod,
        paymentReference: raw.paymentReference.trim() || undefined,
        comment: raw.comment.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.repaymentSaving.set(false);
          this.repaymentFormVisible.set(false);
          this.refreshLoanAfterRepayment(loan.id);
        },
        error: (err) => {
          this.repaymentSaving.set(false);
          this.repaymentError.set(
            err?.status === 400 ? 'Montant supérieur au solde restant — vérifiez le solde du prêt.' : "Impossible d'enregistrer ce remboursement.",
          );
        },
      });
  }

  // -- remboursement anticipé --

  readonly earlyRepaymentTarget = signal<LoanResponse | null>(null);
  readonly earlyRepaymentForm = this.fb.nonNullable.group({
    paymentDate: [new Date().toISOString().slice(0, 10)],
    paymentMethod: ['BANK_TRANSFER' as DisbursementMethod],
    paymentReference: [''],
    comment: [''],
  });
  readonly earlyRepaymentSaving = signal(false);
  readonly earlyRepaymentError = signal<string | null>(null);
  readonly earlyRepaymentDateShortcutsOpen = signal(false);

  applyEarlyRepaymentDateShortcut(days: number | 'end-of-month'): void {
    this.earlyRepaymentForm.controls.paymentDate.setValue(this.computeShortcutDate(days));
    this.earlyRepaymentDateShortcutsOpen.set(false);
  }

  askEarlyRepayment(): void {
    const loan = this.selectedLoan();
    if (!loan) return;
    this.earlyRepaymentError.set(null);
    this.earlyRepaymentDateShortcutsOpen.set(false);
    this.earlyRepaymentForm.reset({
      paymentDate: new Date().toISOString().slice(0, 10),
      paymentMethod: 'BANK_TRANSFER',
      paymentReference: '',
      comment: '',
    });
    this.earlyRepaymentTarget.set(loan);
  }

  submitEarlyRepayment(): void {
    const loan = this.earlyRepaymentTarget();
    const raw = this.earlyRepaymentForm.getRawValue();
    if (!loan) return;
    this.earlyRepaymentSaving.set(true);
    this.earlyRepaymentError.set(null);
    this.loanService
      .earlyRepayment(loan.id, {
        paymentDate: raw.paymentDate,
        paymentMethod: raw.paymentMethod,
        paymentReference: raw.paymentReference.trim() || undefined,
        comment: raw.comment.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.earlyRepaymentSaving.set(false);
          this.earlyRepaymentTarget.set(null);
          this.refreshLoanAfterRepayment(loan.id);
        },
        error: () => {
          this.earlyRepaymentSaving.set(false);
          this.earlyRepaymentError.set('Impossible de solder ce prêt.');
        },
      });
  }
}
