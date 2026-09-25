import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, ColComponent, FormSelectDirective, RowComponent, SpinnerComponent } from '@coreui/angular';

import { ContractService } from '../../core/contracts/contract.service';
import { ContractPage, ContractResponse, ContractStatus, ModePaiement } from '../../core/contracts/models/contract.model';
import { apiRateToPercent } from '../../core/contracts/commission-rate.util';
import { InsuredService } from '../../core/insured/insured.service';
import { InsuredResponse } from '../../core/insured/models/insured.model';
import { ModalComponent } from '../../shared/modal/modal.component';
import { AuditHistoryComponent } from '../../shared/audit-history/audit-history.component';
import { EntityDocumentsComponent } from '../../shared/entity-documents/entity-documents.component';
import { AvenantService } from '../../core/avenants/avenant.service';
import { AvenantRequest, AvenantResponse, AvenantType } from '../../core/avenants/models/avenant.model';
import { AVENANT_CATEGORIE_LABEL, AVENANT_CATEGORIE_VARIANT, AVENANT_TYPE_GROUPS, AVENANT_TYPE_LABEL } from '../../core/avenants/avenant-labels';
import { PartenaireService } from '../../core/partenaire/partenaire.service';
import { PartenaireResponse } from '../../core/partenaire/models/partenaire.model';
import { CompagnieService } from '../../core/configuration/compagnie.service';
import { CompagnieResponse } from '../../core/configuration/models/compagnie.model';

type ContratsMode = 'valides' | 'echus' | 'cycle' | 'non-renouveles' | 'echeance-proche';
type StatusFilter = 'all' | ContractStatus;
type ModePaiementFilter = 'all' | ModePaiement;
/** Preset day thresholds for the "échéance proche" quick filter — 'custom' unlocks a free
 * number-of-days input alongside it (echeanceProcheJoursCustom). */
type EcheanceProcheFilter = '' | '1' | '2' | '5' | '7' | '10' | 'custom';

const EMPTY_ADVANCED_FILTERS = {
  productName: 'all',
  compagnieName: 'all',
  partenaireName: 'all',
  modePaiement: 'all' as ModePaiementFilter,
  dateEffetFrom: '',
  dateEffetTo: '',
  dateEcheanceFrom: '',
  dateEcheanceTo: '',
  primeMin: '',
  primeMax: '',
  echeanceProcheJours: '' as EcheanceProcheFilter,
  echeanceProcheJoursCustom: '',
};

const TITLES: Record<ContratsMode, string> = {
  valides: 'Contrats valides',
  echus: 'Contrats échus',
  cycle: 'Cycle de vie du contrat',
  'non-renouveles': 'Contrats non renouvelés',
  'echeance-proche': 'Contrats à échéance proche',
};

const STATUS_LABEL: Record<ContractStatus, string> = {
  draft: 'Brouillon',
  pending: 'En attente',
  signed: 'Signé',
  cancelled: 'Annulé',
};

const STATUS_VARIANT: Record<ContractStatus, string> = {
  draft: 'neutral',
  pending: 'warning',
  signed: 'success',
  cancelled: 'danger',
};

/** Order of stages in the normal lifecycle — 'cancelled' is a terminal branch
 * shown separately rather than as a 4th step. */
const LIFECYCLE_STEPS: ContractStatus[] = ['draft', 'pending', 'signed'];

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
const THIRTY_DAYS_MS = 30 * ONE_DAY_MS;

const EMPTY_AVENANT_FORM = {
  type: 'CORRECTION_INFORMATION' as AvenantType,
  dateEffet: '',
  motif: '',
  description: '',
  premium: '',
  commissionRate: '',
  commissionAmount: '',
  partenaireId: '',
  compagnieId: '',
};

interface AvenantChange {
  label: string;
  from: string;
  to: string;
}

@Component({
  selector: 'app-contrats',
  templateUrl: './contrats.component.html',
  styleUrl: './contrats.component.scss',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    ModalComponent,
    FormSelectDirective,
    RowComponent,
    ColComponent,
    AuditHistoryComponent,
    EntityDocumentsComponent,
  ],
})
export class ContratsComponent {
  private readonly contractService = inject(ContractService);
  private readonly insuredService = inject(InsuredService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly avenantService = inject(AvenantService);
  private readonly partenaireService = inject(PartenaireService);
  private readonly compagnieService = inject(CompagnieService);

  readonly mode = (this.route.snapshot.data['mode'] as ContratsMode) ?? 'cycle';
  readonly title = TITLES[this.mode];
  readonly isCycleMode = this.mode === 'cycle';
  readonly lifecycleSteps = LIFECYCLE_STEPS;

  readonly pageSize = 20;

  readonly allContracts = signal<ContractResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  /** Permet au widget "Contrats en attente" du tableau de bord de renvoyer directement
   * sur cette liste déjà filtrée (?status=pending), plutôt que sur la liste complète —
   * sans effet en dehors du mode cycle, seul mode où statusFilter influence l'affichage. */
  readonly statusFilter = signal<StatusFilter>(this.initialStatusFilter());
  readonly pageIndex = signal(0);
  readonly exporting = signal(false);

  readonly advancedFiltersOpen = signal(false);
  readonly advancedFilters = signal({ ...EMPTY_ADVANCED_FILTERS });
  /** Toggled by clicking the flag KPI tile — restricts the table to whatever that
   * tile is counting (échéance proche / échues récemment, depending on mode). Not
   * offered in cycle mode: there the tile counts "en attente", already filterable
   * via the status pills above the table. */
  readonly flagFilterActive = signal(false);

  readonly detailVisible = signal(false);
  readonly selectedContract = signal<ContractResponse | null>(null);
  /** Le téléphone de l'assuré n'est pas sur ContractResponse — récupéré via insuredId
   * à l'ouverture du détail plutôt que sur chaque ligne de la liste, pour éviter N appels. */
  readonly selectedInsuredDetail = signal<InsuredResponse | null>(null);
  readonly selectedInsuredLoading = signal(false);

  readonly avenants = signal<AvenantResponse[]>([]);
  readonly avenantsLoading = signal(false);
  readonly avenantsError = signal<string | null>(null);
  readonly avenantFormOpen = signal(false);
  readonly avenantForm = signal({ ...EMPTY_AVENANT_FORM });
  readonly avenantSaving = signal(false);
  readonly avenantError = signal<string | null>(null);
  readonly avenantPartenaires = signal<PartenaireResponse[]>([]);
  readonly avenantCompagnies = signal<CompagnieResponse[]>([]);
  private avenantRefsLoaded = false;

  readonly avenantTypeGroups = AVENANT_TYPE_GROUPS;
  readonly avenantTypeLabel = AVENANT_TYPE_LABEL;
  readonly avenantCategorieLabel = AVENANT_CATEGORIE_LABEL;
  readonly avenantCategorieVariant = AVENANT_CATEGORIE_VARIANT;

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  readonly productOptions = computed(() => this.distinctValues((c) => c.productName));
  readonly compagnieOptions = computed(() => this.distinctValues((c) => c.compagnieName));
  readonly partenaireOptions = computed(() => this.distinctValues((c) => c.partenaireName));

  readonly activeFilterChips = computed(() => {
    const chips: { key: keyof typeof EMPTY_ADVANCED_FILTERS | 'search' | 'flag'; label: string }[] = [];
    const query = this.searchQuery().trim();
    if (query) chips.push({ key: 'search', label: `Recherche : « ${query} »` });
    if (this.flagFilterActive() && !this.isCycleMode) chips.push({ key: 'flag', label: this.statFlagLabel() });

    const f = this.advancedFilters();
    if (f.productName !== 'all') chips.push({ key: 'productName', label: `Produit : ${f.productName}` });
    if (f.compagnieName !== 'all') chips.push({ key: 'compagnieName', label: `Compagnie : ${f.compagnieName}` });
    if (f.partenaireName !== 'all') chips.push({ key: 'partenaireName', label: `Partenaire : ${f.partenaireName}` });
    if (f.modePaiement !== 'all') {
      chips.push({ key: 'modePaiement', label: `Paiement : ${f.modePaiement === 'FRACTIONNE' ? 'Fractionné' : 'Comptant'}` });
    }
    if (f.primeMin) chips.push({ key: 'primeMin', label: `Prime ≥ ${f.primeMin} FCFA` });
    if (f.primeMax) chips.push({ key: 'primeMax', label: `Prime ≤ ${f.primeMax} FCFA` });
    if (f.dateEffetFrom) chips.push({ key: 'dateEffetFrom', label: `Effet ≥ ${this.formatDate(f.dateEffetFrom)}` });
    if (f.dateEffetTo) chips.push({ key: 'dateEffetTo', label: `Effet ≤ ${this.formatDate(f.dateEffetTo)}` });
    if (f.dateEcheanceFrom) chips.push({ key: 'dateEcheanceFrom', label: `Échéance ≥ ${this.formatDate(f.dateEcheanceFrom)}` });
    if (f.dateEcheanceTo) chips.push({ key: 'dateEcheanceTo', label: `Échéance ≤ ${this.formatDate(f.dateEcheanceTo)}` });
    const echeanceProcheLabel = this.echeanceProcheChipLabel(f);
    if (echeanceProcheLabel) chips.push({ key: 'echeanceProcheJours', label: echeanceProcheLabel });
    return chips;
  });

  readonly activeAdvancedFilterCount = computed(() => {
    const f = this.advancedFilters();
    let count = 0;
    for (const [key, value] of Object.entries(f)) {
      // Counted together with echeanceProcheJours below — it's a sub-field, not a filter of its own.
      if (key === 'echeanceProcheJoursCustom') continue;
      if (key === 'echeanceProcheJours') {
        if (this.echeanceProcheChipLabel(f)) count++;
        continue;
      }
      if (key === 'modePaiement' || key === 'productName' || key === 'compagnieName' || key === 'partenaireName') {
        if (value !== 'all') count++;
        continue;
      }
      if (value !== '') count++;
    }
    return count;
  });

  readonly scopedContracts = computed(() => {
    const all = this.allContracts();
    if (this.mode === 'valides') return all.filter((c) => c.status === 'signed' && !this.isExpired(c));
    if (this.mode === 'echus') return all.filter((c) => c.status === 'signed' && this.isExpired(c));
    // non-renouveles/echeance-proche come pre-filtered from their own dedicated endpoints.
    if (this.mode === 'non-renouveles' || this.mode === 'echeance-proche') return all;
    const filter = this.statusFilter();
    return filter === 'all' ? all : all.filter((c) => c.status === filter);
  });

  readonly filteredContracts = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const scoped = this.scopedContracts();
    const searched = !query
      ? scoped
      : scoped.filter((c) =>
          `${c.policyNumber} ${c.insuredName} ${c.productName} ${c.partenaireName}`.toLowerCase().includes(query)
        );

    const f = this.advancedFilters();
    const primeMin = f.primeMin ? Number(f.primeMin) : null;
    const primeMax = f.primeMax ? Number(f.primeMax) : null;
    const dateEffetFrom = f.dateEffetFrom ? new Date(f.dateEffetFrom).getTime() : null;
    const dateEffetTo = f.dateEffetTo ? new Date(f.dateEffetTo).getTime() : null;
    const dateEcheanceFrom = f.dateEcheanceFrom ? new Date(f.dateEcheanceFrom).getTime() : null;
    const dateEcheanceTo = f.dateEcheanceTo ? new Date(f.dateEcheanceTo).getTime() : null;
    const echeanceProcheDays = this.echeanceProcheDaysValue(f);

    return searched.filter((c) => {
      if (f.productName !== 'all' && c.productName !== f.productName) return false;
      if (f.compagnieName !== 'all' && c.compagnieName !== f.compagnieName) return false;
      if (f.partenaireName !== 'all' && c.partenaireName !== f.partenaireName) return false;
      if (f.modePaiement !== 'all' && c.modePaiement !== f.modePaiement) return false;
      if (primeMin !== null && c.premium < primeMin) return false;
      if (primeMax !== null && c.premium > primeMax) return false;
      if (dateEffetFrom !== null && (!c.dateEffet || new Date(c.dateEffet).getTime() < dateEffetFrom)) return false;
      if (dateEffetTo !== null && (!c.dateEffet || new Date(c.dateEffet).getTime() > dateEffetTo)) return false;
      if (dateEcheanceFrom !== null && (!c.dateEcheance || new Date(c.dateEcheance).getTime() < dateEcheanceFrom)) return false;
      if (dateEcheanceTo !== null && (!c.dateEcheance || new Date(c.dateEcheance).getTime() > dateEcheanceTo)) return false;
      if (echeanceProcheDays !== null && !this.isDueSoon(c, echeanceProcheDays)) return false;
      if (this.flagFilterActive() && !this.isCycleMode && !this.isRowFlagged(c)) return false;
      return true;
    });
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filteredContracts().length / this.pageSize)));

  readonly pagedContracts = computed(() => {
    const start = this.pageIndex() * this.pageSize;
    return this.filteredContracts().slice(start, start + this.pageSize);
  });

  readonly statTotal = computed(() => this.filteredContracts().length);

  readonly statVolume = computed(() => this.filteredContracts().reduce((sum, c) => sum + c.premium, 0));

  readonly statMoyenne = computed(() => {
    const total = this.statTotal();
    return total === 0 ? 0 : Math.round(this.statVolume() / total);
  });

  /** The 4th tile adapts to what's actually informative per mode: contracts here
   * are never both "already expired" and "due soon" at once, so échéance-based
   * flags would always read 0 in cycle/echus modes. echeance-proche is already
   * pre-filtered to <30j by its own endpoint, so its flag tightens to <7j instead
   * of just repeating the total. */
  readonly statFlagLabel = computed(() => {
    if (this.mode === 'echus' || this.mode === 'non-renouveles') return 'Échues < 30 j';
    if (this.mode === 'cycle') return 'En attente de signature';
    if (this.mode === 'echeance-proche') return 'Échéance < 7 j';
    return 'Échéance < 30 j';
  });

  readonly statFlagValue = computed(() => {
    if (this.mode === 'cycle') return this.filteredContracts().filter((c) => c.status === 'pending').length;
    if (this.mode === 'echus' || this.mode === 'non-renouveles') {
      return this.filteredContracts().filter((c) => this.isRecentlyExpired(c)).length;
    }
    if (this.mode === 'echeance-proche') return this.filteredContracts().filter((c) => this.isDueSoon(c, 7)).length;
    return this.filteredContracts().filter((c) => this.isDueSoon(c)).length;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.searchDebounce) clearTimeout(this.searchDebounce);
    });
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.pageIndex.set(0);

    let request$: Observable<ContractPage>;
    if (this.mode === 'non-renouveles') {
      request$ = this.contractService.listNonRenouveles();
    } else if (this.mode === 'echeance-proche') {
      request$ = this.contractService.listEcheanceProche(30);
    } else {
      request$ = this.contractService.listAll(this.mode === 'cycle' ? undefined : 'signed');
    }

    request$.subscribe({
      next: (page) => {
        this.allContracts.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les contrats.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.pageIndex.set(0), 400);
  }

  setStatusFilter(filter: StatusFilter): void {
    this.statusFilter.set(filter);
    this.pageIndex.set(0);
  }

  private initialStatusFilter(): StatusFilter {
    const value = this.route.snapshot.queryParamMap.get('status');
    const valid: StatusFilter[] = ['all', 'draft', 'pending', 'signed', 'cancelled'];
    return valid.includes(value as StatusFilter) ? (value as StatusFilter) : 'all';
  }

  toggleAdvancedFilters(): void {
    this.advancedFiltersOpen.set(!this.advancedFiltersOpen());
  }

  closeAdvancedFilters(): void {
    this.advancedFiltersOpen.set(false);
  }

  updateAdvancedFilter<K extends keyof typeof EMPTY_ADVANCED_FILTERS>(
    key: K,
    value: (typeof EMPTY_ADVANCED_FILTERS)[K],
  ): void {
    this.advancedFilters.update((current) => ({ ...current, [key]: value }));
    this.pageIndex.set(0);
  }

  resetAdvancedFilters(): void {
    this.advancedFilters.set({ ...EMPTY_ADVANCED_FILTERS });
    this.pageIndex.set(0);
  }

  removeFilterChip(key: keyof typeof EMPTY_ADVANCED_FILTERS | 'search' | 'flag'): void {
    if (key === 'search') {
      this.searchQuery.set('');
      this.pageIndex.set(0);
      return;
    }
    if (key === 'flag') {
      this.flagFilterActive.set(false);
      this.pageIndex.set(0);
      return;
    }
    if (key === 'echeanceProcheJours') {
      this.setEcheanceProcheJours('');
      return;
    }
    this.updateAdvancedFilter(key, EMPTY_ADVANCED_FILTERS[key]);
  }

  toggleFlagFilter(): void {
    if (this.isCycleMode) return;
    this.flagFilterActive.update((v) => !v);
    this.pageIndex.set(0);
  }

  /** Selecting a preset clears any leftover custom value; picking 'custom' keeps
   * whatever the user had already typed there. */
  setEcheanceProcheJours(value: EcheanceProcheFilter): void {
    this.advancedFilters.update((current) => ({
      ...current,
      echeanceProcheJours: value,
      echeanceProcheJoursCustom: value === 'custom' ? current.echeanceProcheJoursCustom : '',
    }));
    this.pageIndex.set(0);
  }

  private echeanceProcheDaysValue(f: typeof EMPTY_ADVANCED_FILTERS): number | null {
    const raw = f.echeanceProcheJours === 'custom' ? f.echeanceProcheJoursCustom : f.echeanceProcheJours;
    if (!raw) return null;
    const days = Number(raw);
    return Number.isFinite(days) && days > 0 ? days : null;
  }

  private echeanceProcheChipLabel(f: typeof EMPTY_ADVANCED_FILTERS): string | null {
    const days = this.echeanceProcheDaysValue(f);
    if (days === null) return null;
    return f.echeanceProcheJours === 'custom' ? `Échéance ≤ ${days} j` : `Échéance < ${days} j`;
  }

  private distinctValues(pick: (c: ContractResponse) => string | null): string[] {
    const values = new Set(this.allContracts().map(pick).filter((v): v is string => !!v));
    return Array.from(values).sort((a, b) => a.localeCompare(b, 'fr'));
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
  }

  /** Envoie vers le module Souscription avec l'id du contrat en query param — c'est ce
   * dernier qui charge le contrat et pré-remplit assuré/produit/couverture (voir
   * applyRenewalPrefill dans souscription.component.ts). */
  renewContract(contract: ContractResponse): void {
    this.router.navigate(['/souscription/souscription-produit-assurance'], {
      queryParams: { renewContractId: contract.id },
    });
  }

  openDetail(contract: ContractResponse): void {
    this.selectedContract.set(contract);
    this.selectedInsuredDetail.set(null);
    this.detailVisible.set(true);
    this.selectedInsuredLoading.set(true);
    this.insuredService.get(contract.insuredId).subscribe({
      next: (insured) => {
        this.selectedInsuredLoading.set(false);
        this.selectedInsuredDetail.set(insured);
      },
      error: () => {
        this.selectedInsuredLoading.set(false);
      },
    });

    this.avenantFormOpen.set(false);
    this.avenantForm.set({ ...EMPTY_AVENANT_FORM });
    this.avenantError.set(null);
    this.fetchAvenants(contract.id);
  }

  private fetchAvenants(contractId: number): void {
    this.avenantsLoading.set(true);
    this.avenantsError.set(null);
    this.avenantService.listByContract(contractId).subscribe({
      next: (avenants) => {
        this.avenants.set(avenants);
        this.avenantsLoading.set(false);
      },
      error: () => {
        this.avenantsError.set('Impossible de charger les avenants de ce contrat.');
        this.avenantsLoading.set(false);
      },
    });
  }

  toggleAvenantForm(): void {
    const next = !this.avenantFormOpen();
    this.avenantFormOpen.set(next);
    if (next) {
      this.avenantError.set(null);
      this.loadAvenantReferenceData();
    }
  }

  /** Partenaires/compagnies pour les selects du formulaire — chargés une seule fois,
   * à la première ouverture du formulaire (pas au chargement de la page). */
  private loadAvenantReferenceData(): void {
    if (this.avenantRefsLoaded) return;
    this.avenantRefsLoaded = true;
    this.partenaireService.listAll().subscribe({
      next: (page) => this.avenantPartenaires.set(page.content),
      error: () => this.avenantPartenaires.set([]),
    });
    this.compagnieService.list(0, 200).subscribe({
      next: (page) => this.avenantCompagnies.set(page.content),
      error: () => this.avenantCompagnies.set([]),
    });
  }

  updateAvenantForm<K extends keyof typeof EMPTY_AVENANT_FORM>(key: K, value: (typeof EMPTY_AVENANT_FORM)[K]): void {
    this.avenantForm.update((current) => ({ ...current, [key]: value }));
  }

  submitAvenant(): void {
    const contract = this.selectedContract();
    if (!contract) return;

    const f = this.avenantForm();
    if (!f.dateEffet || !f.motif.trim()) {
      this.avenantError.set("La date d'effet et le motif sont requis.");
      return;
    }

    const payload: AvenantRequest = {
      type: f.type,
      dateEffet: f.dateEffet,
      motif: f.motif.trim(),
      description: f.description.trim() || undefined,
      premium: f.premium !== '' ? Number(f.premium) : undefined,
      commissionRate: f.commissionRate !== '' ? Number(f.commissionRate) : undefined,
      commissionAmount: f.commissionAmount !== '' ? Number(f.commissionAmount) : undefined,
      partenaireId: f.partenaireId !== '' ? Number(f.partenaireId) : undefined,
      compagnieId: f.compagnieId !== '' ? Number(f.compagnieId) : undefined,
    };

    this.avenantSaving.set(true);
    this.avenantError.set(null);
    this.avenantService.create(contract.id, payload).subscribe({
      next: () => {
        this.avenantSaving.set(false);
        this.avenantFormOpen.set(false);
        this.avenantForm.set({ ...EMPTY_AVENANT_FORM });
        this.fetchAvenants(contract.id);
      },
      error: () => {
        this.avenantSaving.set(false);
        this.avenantError.set("Impossible de créer l'avenant.");
      },
    });
  }

  /** N'affiche que les paires ancien→nouveau effectivement renseignées par cet avenant
   * — la plupart des champs "ancien*"/"nouveau*" sont null sauf ceux réellement modifiés. */
  avenantChanges(a: AvenantResponse): AvenantChange[] {
    const changes: AvenantChange[] = [];
    if (a.ancienPremium !== null || a.nouveauPremium !== null) {
      changes.push({
        label: 'Prime',
        from: a.ancienPremium !== null ? this.formatAmount(a.ancienPremium) : '—',
        to: a.nouveauPremium !== null ? this.formatAmount(a.nouveauPremium) : '—',
      });
    }
    if (a.ancienneCommissionRate !== null || a.nouvelleCommissionRate !== null) {
      changes.push({
        label: 'Taux de commission',
        from: a.ancienneCommissionRate !== null ? `${a.ancienneCommissionRate} %` : '—',
        to: a.nouvelleCommissionRate !== null ? `${a.nouvelleCommissionRate} %` : '—',
      });
    }
    if (a.ancienCommissionAmount !== null || a.nouveauCommissionAmount !== null) {
      changes.push({
        label: 'Montant de commission',
        from: a.ancienCommissionAmount !== null ? this.formatAmount(a.ancienCommissionAmount) : '—',
        to: a.nouveauCommissionAmount !== null ? this.formatAmount(a.nouveauCommissionAmount) : '—',
      });
    }
    if (a.ancienPartenaireId !== null || a.nouveauPartenaireId !== null) {
      changes.push({
        label: 'Partenaire',
        from: a.ancienPartenaireNom ?? '—',
        to: a.nouveauPartenaireNom ?? '—',
      });
    }
    if (a.ancienneCompagnieId !== null || a.nouvelleCompagnieId !== null) {
      changes.push({
        label: 'Compagnie',
        from: a.ancienneCompagnieNom ?? '—',
        to: a.nouvelleCompagnieNom ?? '—',
      });
    }
    return changes;
  }

  /** Maps the current search/status/product filters onto the export endpoint's
   * supported params — it doesn't accept compagnie/partenaire/mode paiement/date/prime,
   * so those stay client-side-only refinements on the table itself. */
  export(): void {
    if (this.filteredContracts().length === 0) return;

    const f = this.advancedFilters();
    const productId =
      f.productName === 'all'
        ? undefined
        : this.allContracts().find((c) => c.productName === f.productName)?.productId;

    this.exporting.set(true);
    this.contractService
      .exportCsv({
        q: this.searchQuery().trim() || undefined,
        status: this.mode === 'cycle' ? (this.statusFilter() === 'all' ? undefined : this.statusFilter()) : 'signed',
        productId,
      })
      .subscribe({
        next: (blob) => {
          this.downloadBlob(blob);
          this.exporting.set(false);
        },
        error: () => {
          this.error.set("Impossible d'exporter les contrats.");
          this.exporting.set(false);
        },
      });
  }

  private downloadBlob(blob: Blob): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `assurzen-contrats-${this.mode}-${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  private isExpired(contract: ContractResponse): boolean {
    if (!contract.dateEcheance) return false;
    return new Date(contract.dateEcheance).getTime() < Date.now();
  }

  isDueSoon(contract: ContractResponse, days = 30): boolean {
    if (!contract.dateEcheance) return false;
    const t = new Date(contract.dateEcheance).getTime();
    const now = Date.now();
    return t >= now && t <= now + days * ONE_DAY_MS;
  }

  isRecentlyExpired(contract: ContractResponse): boolean {
    if (!contract.dateEcheance) return false;
    const t = new Date(contract.dateEcheance).getTime();
    const now = Date.now();
    return t < now && t >= now - THIRTY_DAYS_MS;
  }

  /** Whether a row should get the orange "needs attention" highlight — the
   * definition of "flagged" mirrors whatever the mode's own KPI flag tile counts. */
  isRowFlagged(contract: ContractResponse): boolean {
    if (this.isCycleMode) return false;
    if (this.mode === 'echus' || this.mode === 'non-renouveles') return this.isRecentlyExpired(contract);
    if (this.mode === 'echeance-proche') return this.isDueSoon(contract, 7);
    return this.isDueSoon(contract);
  }

  flagNote(contract: ContractResponse): string | null {
    if (!this.isRowFlagged(contract)) return null;
    return this.mode === 'echus' || this.mode === 'non-renouveles' ? 'Échue récemment' : 'Échéance proche';
  }

  stepState(contract: ContractResponse, step: ContractStatus): 'done' | 'current' | 'upcoming' {
    if (contract.status === 'cancelled') return 'upcoming';
    const stepIndex = LIFECYCLE_STEPS.indexOf(step);
    const currentIndex = LIFECYCLE_STEPS.indexOf(contract.status);
    if (stepIndex < currentIndex) return 'done';
    if (stepIndex === currentIndex) return 'current';
    return 'upcoming';
  }

  statusLabel(status: ContractStatus): string {
    return STATUS_LABEL[status];
  }

  statusVariant(status: ContractStatus): string {
    return STATUS_VARIANT[status];
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  insuredInitials(name: string | null | undefined): string {
    if (!name) return '?';
    const parts = name.trim().split(/\s+/);
    const first = parts[0]?.[0] ?? '';
    const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
    return (first + last).toUpperCase();
  }

  /** commissionRate est une fraction côté API (0.10 = 10 %), sauf anciens contrats — voir apiRateToPercent(). */
  ratePercent(rate: number | null): number {
    return apiRateToPercent(rate);
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string): string {
    return new Date(value).toLocaleString('fr-FR');
  }
}
