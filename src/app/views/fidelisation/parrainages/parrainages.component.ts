import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { getStyle } from '@coreui/utils';
import { ChartjsComponent } from '@coreui/angular-chartjs';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  DropdownComponent,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';
import { forkJoin } from 'rxjs';

import { ParrainageService } from '../../../core/parrainage/parrainage.service';
import { ParrainageDashboardService } from '../../../core/parrainage/parrainage-dashboard.service';
import {
  BeneficiaireRecompense,
  ControleAntifraudeParrainageResponse,
  HistoriqueParrainageResponse,
  ParametreParrainageResponse,
  ParrainageResponse,
  ParrainageStatut,
  ProgrammeParrainageResponse,
  RecompenseParrainageResponse,
  RecompenseParrainageStatut,
  RegleRecompenseParrainageResponse,
  TypeRecompenseParrainage,
  ParrainageValidationResponse,
} from '../../../core/parrainage/models/parrainage.model';
import {
  ParrainageDashboardOverviewResponse,
  ParrainageMensuelItem,
  ParrainageParStatutItem,
  ParrainageTopParrainItem,
} from '../../../core/parrainage/models/parrainage-dashboard.model';
import { InsuredService } from '../../../core/insured/insured.service';
import { InsuredResponse } from '../../../core/insured/models/insured.model';
import { ProduitService } from '../../../core/configuration/produit.service';
import { ProduitResponse } from '../../../core/configuration/models/produit.model';
import { ContractService } from '../../../core/contracts/contract.service';
import { ContractResponse } from '../../../core/contracts/models/contract.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../../shared/modal/modal.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import {
  BENEFICIAIRE_LABEL,
  CONTROLE_ANTIFRAUDE_STATUT_VARIANT,
  PARRAINAGE_STATUTS_TERMINAUX,
  PARRAINAGE_STATUT_LABEL,
  PARRAINAGE_STATUT_ORDER,
  PARRAINAGE_STATUT_VARIANT,
  PROGRAMME_STATUT_LABEL,
  PROGRAMME_STATUT_VARIANT,
  RECOMPENSE_STATUT_LABEL,
  RECOMPENSE_STATUT_VARIANT,
  TYPE_RECOMPENSE_LABEL,
} from './parrainage-status';

type View = 'dashboard' | 'parrainages' | 'programmes' | 'regles' | 'recompenses' | 'parametres';
type PendingProgrammeAction = 'activer' | 'suspendre' | 'cloturer' | null;
type PendingRecompenseAction = 'approuver' | 'attribuer' | 'reverser' | 'annuler' | null;

const EMPTY_PARRAINAGE_FORM = {
  parrainInsuredId: 0,
  programmeId: 0,
  code: '',
  filleulNom: '',
  filleulEmail: '',
  filleulTelephone: '',
};

const EMPTY_PROGRAMME_FORM = {
  code: '',
  nom: '',
  description: '',
  dateDebut: '',
  dateFin: '',
  maxParrainagesParClient: '',
  maxRecompensesParClient: '',
  delaiCarenceJours: '',
  dureeValiditeJours: '',
  primeMinimum: '',
  produitsEligibles: [] as number[],
};

const EMPTY_REGLE_FORM = {
  programmeId: 0,
  nom: '',
  description: '',
  typeRecompense: 'CASHBACK' as const,
  beneficiaire: 'PARRAIN' as const,
  montantFixe: '',
  pourcentage: '',
  points: '',
  primeMinimum: '',
  plafondRecompense: '',
  priorite: '0',
  actif: true,
};

const EMPTY_PARAMETRE_FORM = { cle: '', valeur: '', description: '' };

@Component({
  selector: 'app-parrainages',
  templateUrl: './parrainages.component.html',
  imports: [
    DecimalPipe,
    ButtonDirective,
    DropdownComponent,
    DropdownItemDirective,
    DropdownMenuDirective,
    DropdownToggleDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    FormCheckInputDirective,
    ChartjsComponent,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class ParrainagesComponent {
  private readonly parrainageService = inject(ParrainageService);
  private readonly dashboardService = inject(ParrainageDashboardService);
  private readonly insuredService = inject(InsuredService);
  private readonly produitService = inject(ProduitService);
  private readonly contractService = inject(ContractService);
  private readonly fb = inject(FormBuilder);

  readonly statutOrder = PARRAINAGE_STATUT_ORDER;
  readonly statutLabel = PARRAINAGE_STATUT_LABEL;
  readonly typeRecompenseLabel = TYPE_RECOMPENSE_LABEL;
  readonly beneficiaireLabel = BENEFICIAIRE_LABEL;

  readonly view = signal<View>('dashboard');

  setView(view: View): void {
    this.view.set(view);
  }

  // Référentiels partagés ----------------------------------------------------
  readonly insureds = signal<InsuredResponse[]>([]);
  readonly produits = signal<ProduitResponse[]>([]);

  insuredLabel(insured: InsuredResponse): string {
    return `${insured.nom} ${insured.prenoms}${insured.clientPhone ? ' — ' + insured.clientPhone : ''}`;
  }

  // Dashboard ------------------------------------------------------------
  readonly dashboardLoading = signal(true);
  private pendingDashboard = 4;
  readonly overview = signal<ParrainageDashboardOverviewResponse | null>(null);
  readonly topParrains = signal<ParrainageTopParrainItem[]>([]);
  readonly parStatutRows = signal<ParrainageParStatutItem[]>([]);
  readonly mensuel = signal<ParrainageMensuelItem[]>([]);
  mensuelData: any = null;
  mensuelOptions: any = null;

  private checkDashboardDone(): void {
    this.pendingDashboard -= 1;
    if (this.pendingDashboard <= 0) this.dashboardLoading.set(false);
  }

  private fetchDashboard(): void {
    this.dashboardLoading.set(true);
    this.pendingDashboard = 4;
    this.dashboardService.overview().subscribe({
      next: (v) => { this.overview.set(v); this.checkDashboardDone(); },
      error: () => this.checkDashboardDone(),
    });
    this.dashboardService.topParrains().subscribe({
      next: (v) => { this.topParrains.set(v); this.checkDashboardDone(); },
      error: () => this.checkDashboardDone(),
    });
    this.dashboardService.parStatut().subscribe({
      next: (v) => { this.parStatutRows.set(v); this.checkDashboardDone(); },
      error: () => this.checkDashboardDone(),
    });
    this.dashboardService.evolution().subscribe({
      next: (v) => {
        this.mensuel.set(v);
        this.buildMensuelChart();
        this.checkDashboardDone();
      },
      error: () => this.checkDashboardDone(),
    });
  }

  private buildMensuelChart(): void {
    const rows = this.mensuel();
    const colorBorderTranslucent = getStyle('--cui-border-color-translucent');
    const colorBody = getStyle('--cui-body-color');
    const brandPrimary = getStyle('--cui-primary') ?? '#5856d6';
    const brandPrimaryRgb = getStyle('--cui-primary-rgb') ?? '88, 86, 214';
    const brandSuccess = getStyle('--cui-success') ?? '#2eb85c';

    this.mensuelData = {
      labels: rows.map((r) => r.mois),
      datasets: [
        {
          label: 'Parrainages créés',
          data: rows.map((r) => r.nombre),
          borderColor: brandPrimary,
          backgroundColor: `rgba(${brandPrimaryRgb}, .14)`,
          fill: true,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
        {
          label: 'Convertis',
          data: rows.map((r) => r.convertis),
          borderColor: brandSuccess,
          backgroundColor: 'transparent',
          fill: false,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
      ],
    };
    this.mensuelOptions = {
      maintainAspectRatio: false,
      plugins: { legend: { display: true, position: 'bottom', labels: { color: colorBody, boxWidth: 10, usePointStyle: true } } },
      scales: {
        x: { grid: { color: colorBorderTranslucent, drawOnChartArea: false }, ticks: { color: colorBody, maxRotation: 0 } },
        y: { border: { color: colorBorderTranslucent }, grid: { color: colorBorderTranslucent }, min: 0, beginAtZero: true, ticks: { color: colorBody, precision: 0 } },
      },
    };
  }

  parStatutVariant(statut: ParrainageStatut): string {
    return PARRAINAGE_STATUT_VARIANT[statut];
  }

  parStatutMax = computed(() => Math.max(1, ...this.parStatutRows().map((r) => r.nombre)));

  // Parrainages ------------------------------------------------------------
  readonly parrainages = signal<ParrainageResponse[]>([]);
  readonly parrainagesLoading = signal(true);
  readonly parrainagesError = signal<string | null>(null);
  readonly statutFilter = signal<'' | ParrainageStatut>('');
  readonly programmeFilter = signal<number | ''>('');

  readonly filteredParrainages = computed(() => this.parrainages());
  readonly statOuvertes = computed(
    () => this.parrainages().filter((p) => !PARRAINAGE_STATUTS_TERMINAUX.includes(p.statut)).length,
  );
  readonly statConvertis = computed(
    () => this.parrainages().filter((p) => p.statut === 'RECOMPENSE_ATTRIBUEE').length,
  );

  setStatutFilter(value: string): void {
    this.statutFilter.set((value as ParrainageStatut) || '');
    this.fetchParrainages();
  }

  setProgrammeFilter(value: string): void {
    this.programmeFilter.set(value ? Number(value) : '');
    this.fetchParrainages();
  }

  fetchParrainages(): void {
    this.parrainagesLoading.set(true);
    this.parrainagesError.set(null);
    this.parrainageService
      .listParrainages(0, 200, {
        statut: this.statutFilter() || undefined,
        programmeId: this.programmeFilter() || undefined,
      })
      .subscribe({
        next: (page) => {
          this.parrainages.set(page.content);
          this.parrainagesLoading.set(false);
        },
        error: () => {
          this.parrainagesError.set('Impossible de charger les parrainages.');
          this.parrainagesLoading.set(false);
        },
      });
  }

  isTerminal(statut: ParrainageStatut): boolean {
    return PARRAINAGE_STATUTS_TERMINAUX.includes(statut);
  }

  parrainageStatutVariant(statut: ParrainageStatut): string {
    return PARRAINAGE_STATUT_VARIANT[statut];
  }

  // Création d'un parrainage
  readonly createVisible = signal(false);
  readonly createSaving = signal(false);
  readonly createError = signal<string | null>(null);
  readonly parrainageForm = this.fb.nonNullable.group({ ...EMPTY_PARRAINAGE_FORM });

  openCreateParrainage(): void {
    this.parrainageForm.reset({ ...EMPTY_PARRAINAGE_FORM });
    this.createError.set(null);
    this.createVisible.set(true);
  }

  closeCreateParrainage(): void {
    this.createVisible.set(false);
  }

  submitCreateParrainage(): void {
    const raw = this.parrainageForm.getRawValue();
    if (!raw.filleulNom.trim()) {
      this.createError.set('Le nom du filleul est requis.');
      return;
    }
    if (!raw.code.trim() && (!raw.parrainInsuredId || !raw.programmeId)) {
      this.createError.set('Renseignez un code de parrainage, ou un parrain et un programme.');
      return;
    }
    this.createSaving.set(true);
    this.createError.set(null);
    this.parrainageService
      .createParrainage({
        code: raw.code.trim() || undefined,
        parrainInsuredId: raw.parrainInsuredId || undefined,
        programmeId: raw.programmeId || undefined,
        filleulNom: raw.filleulNom.trim(),
        filleulEmail: raw.filleulEmail.trim() || undefined,
        filleulTelephone: raw.filleulTelephone.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.createSaving.set(false);
          this.createVisible.set(false);
          this.fetchParrainages();
        },
        error: () => {
          this.createSaving.set(false);
          this.createError.set("Impossible de créer ce parrainage. Vérifiez le code ou le parrain/programme sélectionné.");
        },
      });
  }

  // Détail d'un parrainage
  readonly detailVisible = signal(false);
  readonly selectedParrainage = signal<ParrainageResponse | null>(null);
  readonly historique = signal<HistoriqueParrainageResponse[]>([]);
  readonly controles = signal<ControleAntifraudeParrainageResponse[]>([]);
  readonly filleulContracts = signal<ContractResponse[]>([]);
  readonly detailActionSaving = signal(false);
  readonly detailActionError = signal<string | null>(null);

  readonly inscriptionForm = this.fb.nonNullable.group({ filleulInsuredId: 0 });
  readonly contratForm = this.fb.nonNullable.group({ contratId: 0 });
  readonly annulerForm = this.fb.nonNullable.group({ motif: '' });

  openDetail(parrainage: ParrainageResponse): void {
    this.selectedParrainage.set(parrainage);
    this.historique.set([]);
    this.controles.set([]);
    this.filleulContracts.set([]);
    this.detailActionError.set(null);
    this.inscriptionForm.reset({ filleulInsuredId: 0 });
    this.contratForm.reset({ contratId: 0 });
    this.annulerForm.reset({ motif: '' });
    this.lastValidation.set(null);
    this.detailVisible.set(true);
    this.loadDetailData(parrainage.id);
    if (parrainage.filleulId) this.loadFilleulContracts(parrainage.filleulId);
  }

  private loadDetailData(id: number): void {
    this.parrainageService.getHistorique(id).subscribe({ next: (h) => this.historique.set(h), error: () => undefined });
    this.parrainageService.getControlesAntifraude(id).subscribe({ next: (c) => this.controles.set(c), error: () => undefined });
  }

  private loadFilleulContracts(insuredId: number): void {
    this.contractService.list(0, 100, { insuredId }).subscribe({
      next: (page) => this.filleulContracts.set(page.content),
      error: () => undefined,
    });
  }

  private refreshDetail(updated: ParrainageResponse): void {
    this.selectedParrainage.set(updated);
    this.loadDetailData(updated.id);
    if (updated.filleulId) this.loadFilleulContracts(updated.filleulId);
    this.fetchParrainages();
  }

  doInviter(): void {
    const p = this.selectedParrainage();
    if (!p) return;
    this.detailActionSaving.set(true);
    this.detailActionError.set(null);
    this.parrainageService.inviter(p.id).subscribe({
      next: (r) => { this.detailActionSaving.set(false); this.refreshDetail(r); },
      error: () => { this.detailActionSaving.set(false); this.detailActionError.set("Impossible d'envoyer l'invitation."); },
    });
  }

  submitInscription(): void {
    const p = this.selectedParrainage();
    const filleulInsuredId = this.inscriptionForm.getRawValue().filleulInsuredId;
    if (!p || !filleulInsuredId) {
      this.detailActionError.set('Sélectionnez le filleul (assuré déjà enregistré).');
      return;
    }
    this.detailActionSaving.set(true);
    this.detailActionError.set(null);
    this.parrainageService.inscrire(p.id, { filleulInsuredId }).subscribe({
      next: (r) => { this.detailActionSaving.set(false); this.refreshDetail(r); },
      error: () => { this.detailActionSaving.set(false); this.detailActionError.set("Impossible d'inscrire ce filleul."); },
    });
  }

  submitContrat(): void {
    const p = this.selectedParrainage();
    const contratId = this.contratForm.getRawValue().contratId;
    if (!p || !contratId) {
      this.detailActionError.set('Sélectionnez le contrat souscrit par le filleul.');
      return;
    }
    this.detailActionSaving.set(true);
    this.detailActionError.set(null);
    this.parrainageService.rattacherContrat(p.id, { contratId }).subscribe({
      next: (r) => { this.detailActionSaving.set(false); this.refreshDetail(r); },
      error: () => { this.detailActionSaving.set(false); this.detailActionError.set('Impossible de rattacher ce contrat.'); },
    });
  }

  readonly lastValidation = signal<ParrainageValidationResponse | null>(null);

  doValider(): void {
    const p = this.selectedParrainage();
    if (!p) return;
    this.detailActionSaving.set(true);
    this.detailActionError.set(null);
    this.lastValidation.set(null);
    this.parrainageService.valider(p.id).subscribe({
      next: (r) => {
        this.detailActionSaving.set(false);
        this.lastValidation.set(r);
        this.refreshDetail(r.parrainage);
      },
      error: () => { this.detailActionSaving.set(false); this.detailActionError.set("Impossible de valider l'éligibilité."); },
    });
  }

  doExpirer(): void {
    const p = this.selectedParrainage();
    if (!p) return;
    this.detailActionSaving.set(true);
    this.detailActionError.set(null);
    this.parrainageService.expirer(p.id).subscribe({
      next: (r) => { this.detailActionSaving.set(false); this.refreshDetail(r); },
      error: () => { this.detailActionSaving.set(false); this.detailActionError.set('Impossible de marquer ce parrainage comme expiré.'); },
    });
  }

  submitAnnuler(): void {
    const p = this.selectedParrainage();
    if (!p) return;
    this.detailActionSaving.set(true);
    this.detailActionError.set(null);
    this.parrainageService.annulerParrainage(p.id, { motif: this.annulerForm.getRawValue().motif.trim() || undefined }).subscribe({
      next: (r) => { this.detailActionSaving.set(false); this.refreshDetail(r); },
      error: () => { this.detailActionSaving.set(false); this.detailActionError.set("Impossible d'annuler ce parrainage."); },
    });
  }

  runAntifraude(): void {
    const p = this.selectedParrainage();
    if (!p) return;
    this.detailActionSaving.set(true);
    this.detailActionError.set(null);
    this.parrainageService.runControleAntifraude(p.id).subscribe({
      next: () => { this.detailActionSaving.set(false); this.loadDetailData(p.id); },
      error: () => { this.detailActionSaving.set(false); this.detailActionError.set('Impossible de lancer le contrôle anti-fraude.'); },
    });
  }

  antifraudeVariant(statut: string): string {
    return CONTROLE_ANTIFRAUDE_STATUT_VARIANT[statut as keyof typeof CONTROLE_ANTIFRAUDE_STATUT_VARIANT] ?? 'neutral';
  }

  // Programmes ------------------------------------------------------------
  readonly programmes = signal<ProgrammeParrainageResponse[]>([]);
  readonly programmesLoading = signal(true);
  readonly programmesError = signal<string | null>(null);
  readonly programmeStatutLabel = PROGRAMME_STATUT_LABEL;
  readonly programmeStatutVariant = PROGRAMME_STATUT_VARIANT;

  fetchProgrammes(): void {
    this.programmesLoading.set(true);
    this.programmesError.set(null);
    this.parrainageService.listProgrammes().subscribe({
      next: (list) => { this.programmes.set(list); this.programmesLoading.set(false); },
      error: () => { this.programmesError.set('Impossible de charger les programmes.'); this.programmesLoading.set(false); },
    });
  }

  readonly programmeFormVisible = signal(false);
  readonly programmeFormMode = signal<'create' | 'edit'>('create');
  readonly programmeFormSaving = signal(false);
  readonly programmeFormError = signal<string | null>(null);
  readonly editingProgrammeId = signal<number | null>(null);
  readonly programmeForm = this.fb.nonNullable.group({
    code: EMPTY_PROGRAMME_FORM.code,
    nom: EMPTY_PROGRAMME_FORM.nom,
    description: EMPTY_PROGRAMME_FORM.description,
    dateDebut: EMPTY_PROGRAMME_FORM.dateDebut,
    dateFin: EMPTY_PROGRAMME_FORM.dateFin,
    maxParrainagesParClient: EMPTY_PROGRAMME_FORM.maxParrainagesParClient,
    maxRecompensesParClient: EMPTY_PROGRAMME_FORM.maxRecompensesParClient,
    delaiCarenceJours: EMPTY_PROGRAMME_FORM.delaiCarenceJours,
    dureeValiditeJours: EMPTY_PROGRAMME_FORM.dureeValiditeJours,
    primeMinimum: EMPTY_PROGRAMME_FORM.primeMinimum,
    produitsEligibles: this.fb.nonNullable.control<number[]>(EMPTY_PROGRAMME_FORM.produitsEligibles),
  });

  openCreateProgramme(): void {
    this.programmeFormMode.set('create');
    this.editingProgrammeId.set(null);
    this.programmeForm.reset({ ...EMPTY_PROGRAMME_FORM });
    this.programmeFormError.set(null);
    this.programmeFormVisible.set(true);
  }

  openEditProgramme(programme: ProgrammeParrainageResponse): void {
    this.programmeFormMode.set('edit');
    this.editingProgrammeId.set(programme.id);
    this.programmeForm.reset({
      code: programme.code,
      nom: programme.nom,
      description: programme.description ?? '',
      dateDebut: programme.dateDebut ?? '',
      dateFin: programme.dateFin ?? '',
      maxParrainagesParClient: programme.maxParrainagesParClient != null ? String(programme.maxParrainagesParClient) : '',
      maxRecompensesParClient: programme.maxRecompensesParClient != null ? String(programme.maxRecompensesParClient) : '',
      delaiCarenceJours: programme.delaiCarenceJours != null ? String(programme.delaiCarenceJours) : '',
      dureeValiditeJours: programme.dureeValiditeJours != null ? String(programme.dureeValiditeJours) : '',
      primeMinimum: programme.primeMinimum != null ? String(programme.primeMinimum) : '',
      produitsEligibles: programme.produitsEligibles ?? [],
    });
    this.programmeFormError.set(null);
    this.programmeFormVisible.set(true);
  }

  closeProgrammeForm(): void {
    this.programmeFormVisible.set(false);
  }

  toggleProduitEligible(id: number, checked: boolean): void {
    const current = this.programmeForm.controls.produitsEligibles.value;
    this.programmeForm.controls.produitsEligibles.setValue(checked ? [...current, id] : current.filter((p) => p !== id));
  }

  submitProgrammeForm(): void {
    const raw = this.programmeForm.getRawValue();
    if (!raw.code.trim() || !raw.nom.trim()) {
      this.programmeFormError.set('Le code et le nom sont requis.');
      return;
    }
    const payload = {
      code: raw.code.trim(),
      nom: raw.nom.trim(),
      description: raw.description.trim() || undefined,
      dateDebut: raw.dateDebut || undefined,
      dateFin: raw.dateFin || undefined,
      maxParrainagesParClient: raw.maxParrainagesParClient ? Number(raw.maxParrainagesParClient) : undefined,
      maxRecompensesParClient: raw.maxRecompensesParClient ? Number(raw.maxRecompensesParClient) : undefined,
      delaiCarenceJours: raw.delaiCarenceJours ? Number(raw.delaiCarenceJours) : undefined,
      dureeValiditeJours: raw.dureeValiditeJours ? Number(raw.dureeValiditeJours) : undefined,
      primeMinimum: raw.primeMinimum ? Number(raw.primeMinimum) : undefined,
      produitsEligibles: raw.produitsEligibles.length ? raw.produitsEligibles : undefined,
    };
    this.programmeFormSaving.set(true);
    this.programmeFormError.set(null);
    const id = this.editingProgrammeId();
    const request$ = id ? this.parrainageService.updateProgramme(id, payload) : this.parrainageService.createProgramme(payload);
    request$.subscribe({
      next: () => {
        this.programmeFormSaving.set(false);
        this.programmeFormVisible.set(false);
        this.fetchProgrammes();
      },
      error: () => {
        this.programmeFormSaving.set(false);
        this.programmeFormError.set('Impossible d’enregistrer ce programme.');
      },
    });
  }

  readonly pendingProgrammeAction = signal<PendingProgrammeAction>(null);
  readonly pendingProgramme = signal<ProgrammeParrainageResponse | null>(null);
  readonly programmeActionSaving = signal(false);

  askProgrammeAction(programme: ProgrammeParrainageResponse, action: Exclude<PendingProgrammeAction, null>): void {
    this.pendingProgramme.set(programme);
    this.pendingProgrammeAction.set(action);
  }

  cancelProgrammeAction(): void {
    this.pendingProgrammeAction.set(null);
    this.pendingProgramme.set(null);
  }

  readonly pendingProgrammeActionTitle = computed(() => {
    const action = this.pendingProgrammeAction();
    if (action === 'activer') return 'Activer le programme';
    if (action === 'suspendre') return 'Suspendre le programme';
    return 'Clôturer le programme';
  });

  readonly pendingProgrammeActionMessage = computed(() => {
    const programme = this.pendingProgramme();
    const action = this.pendingProgrammeAction();
    if (!programme || !action) return '';
    if (action === 'activer') return `Activer le programme « ${programme.nom} » ?`;
    if (action === 'suspendre') return `Suspendre le programme « ${programme.nom} » ? Aucun nouveau parrainage ne pourra y adhérer tant qu'il n'est pas réactivé.`;
    return `Clôturer définitivement le programme « ${programme.nom} » ?`;
  });

  confirmProgrammeAction(): void {
    const programme = this.pendingProgramme();
    const action = this.pendingProgrammeAction();
    if (!programme || !action) return;
    this.programmeActionSaving.set(true);
    const request$ =
      action === 'activer'
        ? this.parrainageService.activerProgramme(programme.id)
        : action === 'suspendre'
          ? this.parrainageService.suspendreProgramme(programme.id)
          : this.parrainageService.cloturerProgramme(programme.id);
    request$.subscribe({
      next: () => {
        this.programmeActionSaving.set(false);
        this.pendingProgrammeAction.set(null);
        this.pendingProgramme.set(null);
        this.fetchProgrammes();
      },
      error: () => {
        this.programmeActionSaving.set(false);
        this.pendingProgrammeAction.set(null);
        this.programmesError.set("Impossible d'effectuer cette action sur le programme.");
      },
    });
  }

  produitNom(id: number): string {
    return this.produits().find((p) => p.id === id)?.nomProduit ?? `#${id}`;
  }

  // Règles de récompense ------------------------------------------------------
  readonly regles = signal<RegleRecompenseParrainageResponse[]>([]);
  readonly reglesLoading = signal(true);
  readonly reglesError = signal<string | null>(null);

  fetchRegles(): void {
    this.reglesLoading.set(true);
    this.reglesError.set(null);
    this.parrainageService.listRegles().subscribe({
      next: (list) => { this.regles.set(list); this.reglesLoading.set(false); },
      error: () => { this.reglesError.set('Impossible de charger les règles de récompense.'); this.reglesLoading.set(false); },
    });
  }

  readonly regleFormVisible = signal(false);
  readonly regleFormMode = signal<'create' | 'edit'>('create');
  readonly regleFormSaving = signal(false);
  readonly regleFormError = signal<string | null>(null);
  readonly editingRegleId = signal<number | null>(null);
  readonly regleForm = this.fb.nonNullable.group({
    programmeId: EMPTY_REGLE_FORM.programmeId,
    nom: EMPTY_REGLE_FORM.nom,
    description: EMPTY_REGLE_FORM.description,
    typeRecompense: this.fb.nonNullable.control<TypeRecompenseParrainage>(EMPTY_REGLE_FORM.typeRecompense),
    beneficiaire: this.fb.nonNullable.control<BeneficiaireRecompense>(EMPTY_REGLE_FORM.beneficiaire),
    montantFixe: EMPTY_REGLE_FORM.montantFixe,
    pourcentage: EMPTY_REGLE_FORM.pourcentage,
    points: EMPTY_REGLE_FORM.points,
    primeMinimum: EMPTY_REGLE_FORM.primeMinimum,
    plafondRecompense: EMPTY_REGLE_FORM.plafondRecompense,
    priorite: EMPTY_REGLE_FORM.priorite,
    actif: EMPTY_REGLE_FORM.actif,
  });

  openCreateRegle(): void {
    this.regleFormMode.set('create');
    this.editingRegleId.set(null);
    this.regleForm.reset({ ...EMPTY_REGLE_FORM });
    this.regleFormError.set(null);
    this.regleFormVisible.set(true);
  }

  openEditRegle(regle: RegleRecompenseParrainageResponse): void {
    this.regleFormMode.set('edit');
    this.editingRegleId.set(regle.id);
    this.regleForm.reset({
      programmeId: regle.programmeId,
      nom: regle.nom,
      description: regle.description ?? '',
      typeRecompense: regle.typeRecompense,
      beneficiaire: regle.beneficiaire,
      montantFixe: regle.montantFixe != null ? String(regle.montantFixe) : '',
      pourcentage: regle.pourcentage != null ? String(regle.pourcentage) : '',
      points: regle.points != null ? String(regle.points) : '',
      primeMinimum: regle.primeMinimum != null ? String(regle.primeMinimum) : '',
      plafondRecompense: regle.plafondRecompense != null ? String(regle.plafondRecompense) : '',
      priorite: regle.priorite != null ? String(regle.priorite) : '0',
      actif: regle.actif ?? true,
    });
    this.regleFormError.set(null);
    this.regleFormVisible.set(true);
  }

  closeRegleForm(): void {
    this.regleFormVisible.set(false);
  }

  submitRegleForm(): void {
    const raw = this.regleForm.getRawValue();
    if (!raw.programmeId || !raw.nom.trim()) {
      this.regleFormError.set('Le programme et le nom sont requis.');
      return;
    }
    const payload = {
      programmeId: raw.programmeId,
      nom: raw.nom.trim(),
      description: raw.description.trim() || undefined,
      typeRecompense: raw.typeRecompense,
      beneficiaire: raw.beneficiaire,
      montantFixe: raw.montantFixe ? Number(raw.montantFixe) : undefined,
      pourcentage: raw.pourcentage ? Number(raw.pourcentage) : undefined,
      points: raw.points ? Number(raw.points) : undefined,
      primeMinimum: raw.primeMinimum ? Number(raw.primeMinimum) : undefined,
      plafondRecompense: raw.plafondRecompense ? Number(raw.plafondRecompense) : undefined,
      priorite: raw.priorite ? Number(raw.priorite) : undefined,
      actif: raw.actif,
    };
    this.regleFormSaving.set(true);
    this.regleFormError.set(null);
    const id = this.editingRegleId();
    const request$ = id ? this.parrainageService.updateRegle(id, payload) : this.parrainageService.createRegle(payload);
    request$.subscribe({
      next: () => {
        this.regleFormSaving.set(false);
        this.regleFormVisible.set(false);
        this.fetchRegles();
      },
      error: () => {
        this.regleFormSaving.set(false);
        this.regleFormError.set('Impossible d’enregistrer cette règle.');
      },
    });
  }

  readonly pendingDeleteRegle = signal<RegleRecompenseParrainageResponse | null>(null);
  readonly regleDeleteSaving = signal(false);

  askDeleteRegle(regle: RegleRecompenseParrainageResponse): void {
    this.pendingDeleteRegle.set(regle);
  }

  cancelDeleteRegle(): void {
    this.pendingDeleteRegle.set(null);
  }

  confirmDeleteRegle(): void {
    const regle = this.pendingDeleteRegle();
    if (!regle) return;
    this.regleDeleteSaving.set(true);
    this.parrainageService.deleteRegle(regle.id).subscribe({
      next: () => {
        this.regleDeleteSaving.set(false);
        this.pendingDeleteRegle.set(null);
        this.fetchRegles();
      },
      error: () => {
        this.regleDeleteSaving.set(false);
        this.pendingDeleteRegle.set(null);
        this.reglesError.set('Impossible de supprimer cette règle.');
      },
    });
  }

  // Récompenses ------------------------------------------------------------
  readonly recompenses = signal<RecompenseParrainageResponse[]>([]);
  readonly recompensesLoading = signal(true);
  readonly recompensesError = signal<string | null>(null);
  readonly recompenseStatutFilter = signal<'' | RecompenseParrainageStatut>('');
  readonly recompenseStatutLabel = RECOMPENSE_STATUT_LABEL;
  readonly recompenseStatutVariant = RECOMPENSE_STATUT_VARIANT;

  setRecompenseStatutFilter(value: string): void {
    this.recompenseStatutFilter.set((value as RecompenseParrainageStatut) || '');
    this.fetchRecompenses();
  }

  fetchRecompenses(): void {
    this.recompensesLoading.set(true);
    this.recompensesError.set(null);
    this.parrainageService.listRecompenses({ statut: this.recompenseStatutFilter() || undefined }).subscribe({
      next: (list) => { this.recompenses.set(list); this.recompensesLoading.set(false); },
      error: () => { this.recompensesError.set('Impossible de charger les récompenses.'); this.recompensesLoading.set(false); },
    });
  }

  readonly pendingRecompenseAction = signal<PendingRecompenseAction>(null);
  readonly pendingRecompense = signal<RecompenseParrainageResponse | null>(null);
  readonly recompenseActionSaving = signal(false);
  readonly recompenseMotifForm = this.fb.nonNullable.group({ motif: '' });

  askRecompenseAction(recompense: RecompenseParrainageResponse, action: Exclude<PendingRecompenseAction, null>): void {
    this.pendingRecompense.set(recompense);
    this.pendingRecompenseAction.set(action);
    this.recompenseMotifForm.reset({ motif: '' });
  }

  cancelRecompenseAction(): void {
    this.pendingRecompenseAction.set(null);
    this.pendingRecompense.set(null);
  }

  readonly recompenseActionNeedsMotif = computed(
    () => this.pendingRecompenseAction() === 'reverser' || this.pendingRecompenseAction() === 'annuler',
  );

  readonly pendingRecompenseActionTitle = computed(() => {
    switch (this.pendingRecompenseAction()) {
      case 'approuver': return 'Approuver la récompense';
      case 'attribuer': return 'Attribuer la récompense';
      case 'reverser': return 'Reverser la récompense';
      case 'annuler': return 'Annuler la récompense';
      default: return '';
    }
  });

  confirmRecompenseAction(): void {
    const recompense = this.pendingRecompense();
    const action = this.pendingRecompenseAction();
    if (!recompense || !action) return;
    const motif = this.recompenseMotifForm.getRawValue().motif.trim() || undefined;
    this.recompenseActionSaving.set(true);
    const request$ =
      action === 'approuver'
        ? this.parrainageService.approuverRecompense(recompense.id)
        : action === 'attribuer'
          ? this.parrainageService.attribuerRecompense(recompense.id)
          : action === 'reverser'
            ? this.parrainageService.reverserRecompense(recompense.id, { motif })
            : this.parrainageService.annulerRecompense(recompense.id, { motif });
    request$.subscribe({
      next: () => {
        this.recompenseActionSaving.set(false);
        this.pendingRecompenseAction.set(null);
        this.pendingRecompense.set(null);
        this.fetchRecompenses();
      },
      error: () => {
        this.recompenseActionSaving.set(false);
        this.pendingRecompenseAction.set(null);
        this.recompensesError.set("Impossible d'effectuer cette action sur la récompense.");
      },
    });
  }

  // Paramètres ------------------------------------------------------------
  readonly parametres = signal<ParametreParrainageResponse[]>([]);
  readonly parametresLoading = signal(true);
  readonly parametresError = signal<string | null>(null);

  fetchParametres(): void {
    this.parametresLoading.set(true);
    this.parametresError.set(null);
    this.parrainageService.listParametres().subscribe({
      next: (list) => { this.parametres.set(list); this.parametresLoading.set(false); },
      error: () => { this.parametresError.set('Impossible de charger les paramètres.'); this.parametresLoading.set(false); },
    });
  }

  readonly parametreFormVisible = signal(false);
  readonly parametreFormSaving = signal(false);
  readonly parametreFormError = signal<string | null>(null);
  readonly parametreForm = this.fb.nonNullable.group({ ...EMPTY_PARAMETRE_FORM });
  readonly editingParametreCle = signal<string | null>(null);

  openEditParametre(parametre: ParametreParrainageResponse | null): void {
    this.editingParametreCle.set(parametre?.cle ?? null);
    this.parametreForm.reset(parametre ? { cle: parametre.cle, valeur: parametre.valeur, description: parametre.description ?? '' } : { ...EMPTY_PARAMETRE_FORM });
    this.parametreFormError.set(null);
    this.parametreFormVisible.set(true);
  }

  closeParametreForm(): void {
    this.parametreFormVisible.set(false);
  }

  submitParametreForm(): void {
    const raw = this.parametreForm.getRawValue();
    if (!raw.cle.trim() || !raw.valeur.trim()) {
      this.parametreFormError.set('La clé et la valeur sont requises.');
      return;
    }
    this.parametreFormSaving.set(true);
    this.parametreFormError.set(null);
    this.parrainageService
      .upsertParametre({ cle: raw.cle.trim(), valeur: raw.valeur.trim(), description: raw.description.trim() || undefined })
      .subscribe({
        next: () => {
          this.parametreFormSaving.set(false);
          this.parametreFormVisible.set(false);
          this.fetchParametres();
        },
        error: () => {
          this.parametreFormSaving.set(false);
          this.parametreFormError.set('Impossible d’enregistrer ce paramètre.');
        },
      });
  }

  formatAmount(value: number | null): string {
    if (value == null) return '—';
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string | null | undefined): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null | undefined): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }

  constructor() {
    this.fetchDashboard();
    this.fetchParrainages();
    this.fetchProgrammes();
    this.fetchRegles();
    this.fetchRecompenses();
    this.fetchParametres();

    forkJoin({
      insureds: this.insuredService.list(0, 500),
      produits: this.produitService.list(0, 200),
    }).subscribe({
      next: ({ insureds, produits }) => {
        this.insureds.set(insureds.content);
        this.produits.set(produits.content);
      },
      error: () => undefined,
    });
  }
}
