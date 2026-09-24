import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
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

import { CommercialService } from '../../core/commercial/commercial.service';
import {
  CommercialManagementDashboardResponse,
  CommercialMonthlyObjectiveResponse,
  CommercialObjectiveResponse,
  CommercialPayrollEntry,
  CommercialPerformanceResponse,
  CommercialPerformanceStatut,
  CommercialRankingEntry,
  CommercialResponse,
  CommercialStatut,
} from '../../core/commercial/models/commercial.model';
import { CommercialCommissionService } from '../../core/commercial/commercial-commission.service';
import {
  CommercialCommissionPaymentResponse,
  CommercialCommissionSummary,
  ContractCommercialCommission,
  SycaPayStatus,
} from '../../core/commercial/models/commercial-commission.model';
import { ExerciceComptableService } from '../../core/configuration/exercice-comptable.service';
import { ExerciceComptableResponse, MoisComptableResponse } from '../../core/configuration/models/exercice-comptable.model';
import { VillePartenaireService } from '../../core/configuration/ville-partenaire.service';
import { VillePartenaireResponse } from '../../core/configuration/models/ville-partenaire.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { sycapayErrorMessage } from '../../core/sycapay/sycapay-errors';
import { ThousandSeparatorDirective } from '../../shared/directives/thousand-separator.directive';

type StatutFilter = 'all' | CommercialStatut;
type MainTab = 'commerciaux' | 'dashboard' | 'paie' | 'commissions';
type DetailView = 'objectifsAnnuels' | 'objectifsMensuels' | 'performance';
type CommissionContractStatusFilter = 'all' | 'pending' | 'paid';

const PAGE_SIZE = 20;

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { message?: string } | null;
    if (body?.message) return body.message;
  }
  return fallback;
}

@Component({
  selector: 'app-commercial',
  templateUrl: './commercial.component.html',
  styleUrl: './commercial.component.scss',
  imports: [
    ButtonDirective,
    DropdownComponent,
    DropdownItemDirective,
    DropdownMenuDirective,
    DropdownToggleDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormCheckInputDirective,
    FormSelectDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
    ThousandSeparatorDirective,
    RouterLink,
  ],
})
export class CommercialComponent {
  private readonly commercialService = inject(CommercialService);
  private readonly commercialCommissionService = inject(CommercialCommissionService);
  private readonly exerciceService = inject(ExerciceComptableService);
  private readonly villePartenaireService = inject(VillePartenaireService);
  private readonly fb = inject(FormBuilder);

  readonly pageSize = PAGE_SIZE;

  readonly activeTab = signal<MainTab>('commerciaux');

  readonly villes = signal<VillePartenaireResponse[]>([]);
  readonly exercices = signal<ExerciceComptableResponse[]>([]);

  setTab(tab: MainTab): void {
    this.activeTab.set(tab);
    if (tab === 'dashboard' && !this.dashboardMoisId() && this.exercices().length > 0) {
      this.autoSelectCurrentMonth('dashboard');
    }
    if (tab === 'paie' && !this.payrollMoisId() && this.exercices().length > 0) {
      this.autoSelectCurrentMonth('paie');
    }
    if (tab === 'commissions' && this.commissionSummaries().length === 0 && !this.commissionsLoading()) {
      this.fetchCommissionSummaries();
      this.fetchCommissionPayments();
    }
  }

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      // rien à nettoyer : plus de debounce de recherche (API sans paramètre texte).
    });
    this.fetch();
    this.fetchVilles();
    this.fetchExercices();
  }

  private fetchVilles(): void {
    this.villePartenaireService.list().subscribe({
      next: (villes) => this.villes.set(villes),
      error: () => {
        // Silencieux : la liste déroulante reste vide si ça échoue.
      },
    });
  }

  private fetchExercices(): void {
    this.exerciceService.listAvailable().subscribe({
      next: (exercices) => this.exercices.set(exercices),
      error: () => {
        // Silencieux : les listes déroulantes exercice/mois restent vides si ça échoue.
      },
    });
  }

  moisOptions(exerciceId: number | string | null): MoisComptableResponse[] {
    const id = Number(exerciceId);
    if (!id) return [];
    return this.exercices().find((e) => e.id === id)?.mois ?? [];
  }

  /** Présélectionne l'exercice actif et son mois courant (non clôturé) sur les onglets
   * transversaux Tableau de bord / Paie, pour éviter un écran vide au premier affichage. */
  private autoSelectCurrentMonth(target: 'dashboard' | 'paie'): void {
    const exercice = this.exercices().find((e) => !e.cloture) ?? this.exercices()[0];
    if (!exercice) return;
    const mois = exercice.mois.find((m) => !m.cloture) ?? exercice.mois[exercice.mois.length - 1];
    if (!mois) return;
    if (target === 'dashboard') {
      this.dashboardExerciceId.set(String(exercice.id));
      this.dashboardMoisId.set(String(mois.id));
      this.fetchDashboard();
    } else {
      this.payrollExerciceId.set(String(exercice.id));
      this.payrollMoisId.set(String(mois.id));
      this.fetchPayroll();
    }
  }

  // ================= Liste des commerciaux =================

  readonly items = signal<CommercialResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly pageIndex = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly statutFilter = signal<StatutFilter>('all');
  readonly villeFilter = signal('');

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    const statut = this.statutFilter();
    this.commercialService
      .list(this.pageIndex(), this.pageSize, {
        statut: statut !== 'all' ? statut : undefined,
        villePartenaireId: this.villeFilter() ? Number(this.villeFilter()) : undefined,
      })
      .subscribe({
        next: (result) => {
          this.items.set(result.content);
          this.totalElements.set(result.totalElements);
          this.totalPages.set(result.totalPages);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Impossible de charger les commerciaux.');
          this.loading.set(false);
        },
      });
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
    this.fetch();
  }

  setStatutFilter(filter: StatutFilter): void {
    this.statutFilter.set(filter);
    this.pageIndex.set(0);
    this.fetch();
  }

  setVilleFilter(villeId: string): void {
    this.villeFilter.set(villeId);
    this.pageIndex.set(0);
    this.fetch();
  }

  fullName(item: CommercialResponse): string {
    return `${item.firstname} ${item.lastname}`;
  }

  // ================= Création / modification =================

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);
  readonly accountResult = signal<{ username: string; temporaryPassword: string | null; notifie: boolean } | null>(
    null,
  );

  readonly form = this.fb.nonNullable.group({
    employeeCode: [''],
    username: [''],
    password: [''],
    firstname: [''],
    lastname: [''],
    email: [''],
    phone: [''],
    villePartenaireId: [''],
    baseSalary: [0],
    transportBenefitEnabled: [false],
    transportBenefitAmount: [0],
    phoneAllowanceEnabled: [false],
    phoneAllowanceAmount: [0],
    hireDate: [''],
    endDate: [''],
  });

  openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({
      employeeCode: '',
      username: '',
      password: '',
      firstname: '',
      lastname: '',
      email: '',
      phone: '',
      villePartenaireId: '',
      baseSalary: 0,
      transportBenefitEnabled: false,
      transportBenefitAmount: 0,
      phoneAllowanceEnabled: false,
      phoneAllowanceAmount: 0,
      hireDate: '',
      endDate: '',
    });
    this.form.controls.employeeCode.enable();
    this.form.controls.username.enable();
    this.form.controls.password.enable();
    this.formVisible.set(true);
  }

  openEdit(item: CommercialResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.form.reset({
      employeeCode: item.employeeCode ?? '',
      username: item.username ?? '',
      password: '',
      firstname: item.firstname,
      lastname: item.lastname,
      email: item.email ?? '',
      phone: item.phone ?? '',
      villePartenaireId: item.villePartenaireId ? String(item.villePartenaireId) : '',
      baseSalary: item.baseSalary,
      transportBenefitEnabled: item.transportBenefitEnabled,
      transportBenefitAmount: item.transportBenefitAmount ?? 0,
      phoneAllowanceEnabled: item.phoneAllowanceEnabled,
      phoneAllowanceAmount: item.phoneAllowanceAmount ?? 0,
      hireDate: item.hireDate ?? '',
      endDate: item.endDate ?? '',
    });
    // Immuables après création côté backend.
    this.form.controls.employeeCode.disable();
    this.form.controls.username.disable();
    this.form.controls.password.disable();
    this.formVisible.set(true);
  }

  save(): void {
    const raw = this.form.getRawValue();
    const isCreate = this.editingId() === null;
    if (
      !raw.firstname.trim() ||
      !raw.lastname.trim() ||
      !raw.email.trim() ||
      !raw.phone.trim() ||
      !raw.villePartenaireId ||
      !Number(raw.baseSalary) ||
      !raw.hireDate ||
      (isCreate && !raw.username.trim())
    ) {
      this.formError.set('Tous les champs obligatoires doivent être renseignés.');
      return;
    }
    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request = id
      ? this.commercialService.update(id, {
          firstname: raw.firstname.trim(),
          lastname: raw.lastname.trim(),
          email: raw.email.trim(),
          phone: raw.phone.trim(),
          villePartenaireId: Number(raw.villePartenaireId),
          baseSalary: Number(raw.baseSalary),
          transportBenefitEnabled: raw.transportBenefitEnabled,
          transportBenefitAmount: raw.transportBenefitEnabled ? Number(raw.transportBenefitAmount) : 0,
          phoneAllowanceEnabled: raw.phoneAllowanceEnabled,
          phoneAllowanceAmount: raw.phoneAllowanceEnabled ? Number(raw.phoneAllowanceAmount) : 0,
          hireDate: raw.hireDate,
          endDate: raw.endDate || undefined,
        })
      : this.commercialService.create({
          employeeCode: raw.employeeCode.trim() || undefined,
          username: raw.username.trim(),
          password: raw.password.trim() || undefined,
          firstname: raw.firstname.trim(),
          lastname: raw.lastname.trim(),
          email: raw.email.trim(),
          phone: raw.phone.trim(),
          villePartenaireId: Number(raw.villePartenaireId),
          baseSalary: Number(raw.baseSalary),
          transportBenefitEnabled: raw.transportBenefitEnabled,
          transportBenefitAmount: raw.transportBenefitEnabled ? Number(raw.transportBenefitAmount) : 0,
          phoneAllowanceEnabled: raw.phoneAllowanceEnabled,
          phoneAllowanceAmount: raw.phoneAllowanceEnabled ? Number(raw.phoneAllowanceAmount) : 0,
          hireDate: raw.hireDate,
        });
    request.subscribe({
      next: (response) => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
        if (response.temporaryPassword) {
          this.accountResult.set({
            username: response.username ?? '',
            temporaryPassword: response.temporaryPassword,
            notifie: response.notificationEnvoyee ?? false,
          });
        }
      },
      error: (err: unknown) => {
        this.formSaving.set(false);
        this.formError.set(extractErrorMessage(err, "Impossible d'enregistrer ce commercial."));
      },
    });
  }

  // ================= Changement de statut =================

  readonly statusChangeTarget = signal<CommercialResponse | null>(null);
  readonly statusChangeValue = signal<CommercialStatut>('ACTIVE');
  readonly statusChangeMotif = signal('');
  readonly statusChangeSaving = signal(false);
  readonly statusChangeError = signal<string | null>(null);

  openStatusChange(item: CommercialResponse): void {
    this.statusChangeTarget.set(item);
    this.statusChangeValue.set(item.statut);
    this.statusChangeMotif.set('');
    this.statusChangeError.set(null);
  }

  closeStatusChange(): void {
    this.statusChangeTarget.set(null);
  }

  confirmStatusChange(): void {
    const item = this.statusChangeTarget();
    if (!item) return;
    this.statusChangeSaving.set(true);
    this.statusChangeError.set(null);
    this.commercialService
      .updateStatut(item.id, { statut: this.statusChangeValue(), motif: this.statusChangeMotif().trim() || undefined })
      .subscribe({
        next: () => {
          this.statusChangeSaving.set(false);
          this.statusChangeTarget.set(null);
          this.fetch();
        },
        error: (err: unknown) => {
          this.statusChangeSaving.set(false);
          this.statusChangeError.set(extractErrorMessage(err, 'Impossible de changer le statut de ce commercial.'));
        },
      });
  }

  // ================= Détail d'un commercial (objectifs + performance) =================

  readonly detailVisible = signal(false);
  readonly selectedCommercial = signal<CommercialResponse | null>(null);
  readonly detailView = signal<DetailView>('objectifsAnnuels');

  readonly annualObjectives = signal<CommercialObjectiveResponse[]>([]);
  readonly annualObjectivesLoading = signal(false);
  readonly annualObjectivesError = signal<string | null>(null);
  readonly annualObjectiveEditId = signal<number | null>(null);
  readonly annualObjectiveExerciceId = signal('');
  /** Alimenté par [wzThousandSeparator] via ngModelChange, qui émet un number|null — jamais
   * une chaîne à espaces, contrairement aux autres champs texte de ce composant. */
  readonly annualObjectiveMontant = signal<string | number | null>('');
  readonly annualObjectiveSaving = signal(false);

  readonly monthlyObjectives = signal<CommercialMonthlyObjectiveResponse[]>([]);
  readonly monthlyObjectivesLoading = signal(false);
  readonly monthlyObjectivesError = signal<string | null>(null);
  readonly monthlyObjectiveEditId = signal<number | null>(null);
  readonly monthlyObjectiveExerciceId = signal('');
  readonly monthlyObjectiveMoisId = signal('');
  /** Alimenté par [wzThousandSeparator] via ngModelChange, qui émet un number|null. */
  readonly monthlyObjectiveMontant = signal<string | number | null>('');
  readonly monthlyObjectiveMotif = signal('');
  readonly monthlyObjectiveSaving = signal(false);

  readonly detailPerformance = signal<CommercialPerformanceResponse[]>([]);
  readonly detailPerformanceLoading = signal(false);
  readonly detailPerformanceError = signal<string | null>(null);
  readonly recalculatingMoisId = signal<number | null>(null);

  openDetail(item: CommercialResponse): void {
    this.selectedCommercial.set(item);
    this.detailVisible.set(true);
    this.detailView.set('objectifsAnnuels');

    this.annualObjectives.set([]);
    this.cancelAnnualObjectiveEdit();
    this.monthlyObjectives.set([]);
    this.cancelMonthlyObjectiveEdit();
    this.detailPerformance.set([]);

    this.fetchAnnualObjectives();
  }

  setDetailView(view: DetailView): void {
    this.detailView.set(view);
    if (view === 'objectifsAnnuels' && this.annualObjectives().length === 0 && !this.annualObjectivesLoading()) {
      this.fetchAnnualObjectives();
    }
    if (view === 'objectifsMensuels' && this.monthlyObjectives().length === 0 && !this.monthlyObjectivesLoading()) {
      this.fetchMonthlyObjectives();
    }
    if (view === 'performance' && this.detailPerformance().length === 0 && !this.detailPerformanceLoading()) {
      this.fetchDetailPerformance();
    }
  }

  private fetchAnnualObjectives(): void {
    const commercial = this.selectedCommercial();
    if (!commercial) return;
    this.annualObjectivesLoading.set(true);
    this.annualObjectivesError.set(null);
    this.commercialService.listObjectives(commercial.id).subscribe({
      next: (objectives) => {
        this.annualObjectives.set(objectives);
        this.annualObjectivesLoading.set(false);
      },
      error: () => {
        this.annualObjectivesError.set('Impossible de charger les objectifs annuels.');
        this.annualObjectivesLoading.set(false);
      },
    });
  }

  editAnnualObjective(objective: CommercialObjectiveResponse): void {
    this.annualObjectiveEditId.set(objective.id);
    this.annualObjectiveExerciceId.set(String(objective.exerciceComptableId));
    this.annualObjectiveMontant.set(String(objective.targetAmount));
    this.annualObjectivesError.set(null);
  }

  cancelAnnualObjectiveEdit(): void {
    this.annualObjectiveEditId.set(null);
    this.annualObjectiveExerciceId.set('');
    this.annualObjectiveMontant.set('');
  }

  saveAnnualObjective(): void {
    const commercial = this.selectedCommercial();
    const exerciceId = Number(this.annualObjectiveExerciceId());
    const montant = Number(this.annualObjectiveMontant() ?? 0);
    if (!commercial) return;
    if (!exerciceId || !montant) {
      this.annualObjectivesError.set('Choisissez un exercice et saisissez un objectif valide.');
      return;
    }
    this.annualObjectiveSaving.set(true);
    this.annualObjectivesError.set(null);
    const payload = { exerciceComptableId: exerciceId, objectiveType: 'PREMIUM_AMOUNT' as const, targetAmount: montant };
    const editId = this.annualObjectiveEditId();
    const request = editId
      ? this.commercialService.updateObjective(commercial.id, editId, payload)
      : this.commercialService.createObjective(commercial.id, payload);
    request.subscribe({
      next: () => {
        this.annualObjectiveSaving.set(false);
        this.cancelAnnualObjectiveEdit();
        this.fetchAnnualObjectives();
      },
      error: (err: unknown) => {
        this.annualObjectiveSaving.set(false);
        this.annualObjectivesError.set(extractErrorMessage(err, "Impossible d'enregistrer cet objectif annuel."));
      },
    });
  }

  private fetchMonthlyObjectives(): void {
    const commercial = this.selectedCommercial();
    if (!commercial) return;
    this.monthlyObjectivesLoading.set(true);
    this.monthlyObjectivesError.set(null);
    this.commercialService.listMonthlyObjectives(commercial.id).subscribe({
      next: (objectives) => {
        this.monthlyObjectives.set(objectives);
        this.monthlyObjectivesLoading.set(false);
      },
      error: () => {
        this.monthlyObjectivesError.set('Impossible de charger les objectifs mensuels.');
        this.monthlyObjectivesLoading.set(false);
      },
    });
  }

  editMonthlyObjective(objective: CommercialMonthlyObjectiveResponse): void {
    this.monthlyObjectiveEditId.set(objective.id);
    this.monthlyObjectiveMoisId.set(String(objective.moisComptableId));
    this.monthlyObjectiveMontant.set(String(objective.targetAmount));
    this.monthlyObjectiveMotif.set('');
    this.monthlyObjectivesError.set(null);
  }

  cancelMonthlyObjectiveEdit(): void {
    this.monthlyObjectiveEditId.set(null);
    this.monthlyObjectiveExerciceId.set('');
    this.monthlyObjectiveMoisId.set('');
    this.monthlyObjectiveMontant.set('');
    this.monthlyObjectiveMotif.set('');
  }

  saveMonthlyObjective(): void {
    const commercial = this.selectedCommercial();
    const montant = Number(this.monthlyObjectiveMontant() ?? 0);
    if (!commercial) return;
    const editId = this.monthlyObjectiveEditId();

    if (editId) {
      const motif = this.monthlyObjectiveMotif().trim();
      if (!montant || !motif) {
        this.monthlyObjectivesError.set('Saisissez un objectif valide et le motif de la révision.');
        return;
      }
      this.monthlyObjectiveSaving.set(true);
      this.monthlyObjectivesError.set(null);
      this.commercialService
        .reviseMonthlyObjective(commercial.id, editId, { targetAmount: montant, motif })
        .subscribe({
          next: () => {
            this.monthlyObjectiveSaving.set(false);
            this.cancelMonthlyObjectiveEdit();
            this.fetchMonthlyObjectives();
          },
          error: (err: unknown) => {
            this.monthlyObjectiveSaving.set(false);
            this.monthlyObjectivesError.set(extractErrorMessage(err, 'Impossible de réviser cet objectif mensuel.'));
          },
        });
      return;
    }

    const moisId = Number(this.monthlyObjectiveMoisId());
    if (!moisId || !montant) {
      this.monthlyObjectivesError.set('Choisissez un mois et saisissez un objectif valide.');
      return;
    }
    this.monthlyObjectiveSaving.set(true);
    this.monthlyObjectivesError.set(null);
    this.commercialService
      .createMonthlyObjective(commercial.id, { moisComptableId: moisId, objectiveType: 'PREMIUM_AMOUNT', targetAmount: montant })
      .subscribe({
        next: () => {
          this.monthlyObjectiveSaving.set(false);
          this.cancelMonthlyObjectiveEdit();
          this.fetchMonthlyObjectives();
        },
        error: (err: unknown) => {
          this.monthlyObjectiveSaving.set(false);
          this.monthlyObjectivesError.set(extractErrorMessage(err, "Impossible d'enregistrer cet objectif mensuel."));
        },
      });
  }

  private fetchDetailPerformance(): void {
    const commercial = this.selectedCommercial();
    if (!commercial) return;
    this.detailPerformanceLoading.set(true);
    this.detailPerformanceError.set(null);
    this.commercialService.listPerformance(commercial.id).subscribe({
      next: (performance) => {
        this.detailPerformance.set(performance);
        this.detailPerformanceLoading.set(false);
      },
      error: () => {
        this.detailPerformanceError.set("Impossible de charger l'historique de performance.");
        this.detailPerformanceLoading.set(false);
      },
    });
  }

  recalculatePerformance(item: CommercialPerformanceResponse): void {
    const commercial = this.selectedCommercial();
    if (!commercial) return;
    this.recalculatingMoisId.set(item.moisComptableId);
    this.detailPerformanceError.set(null);
    this.commercialService.recalculatePerformance(commercial.id, item.moisComptableId).subscribe({
      next: () => {
        this.recalculatingMoisId.set(null);
        this.fetchDetailPerformance();
      },
      error: (err: unknown) => {
        this.recalculatingMoisId.set(null);
        this.detailPerformanceError.set(extractErrorMessage(err, 'Impossible de recalculer cette performance.'));
      },
    });
  }

  readonly statutTarget = signal<CommercialPerformanceResponse | null>(null);
  readonly statutAction = signal<'VALIDATED' | 'PAID' | null>(null);
  readonly statutPending = signal(false);

  confirmValider(item: CommercialPerformanceResponse): void {
    this.statutTarget.set(item);
    this.statutAction.set('VALIDATED');
  }

  confirmPayer(item: CommercialPerformanceResponse): void {
    this.statutTarget.set(item);
    this.statutAction.set('PAID');
  }

  cancelStatutAction(): void {
    this.statutTarget.set(null);
    this.statutAction.set(null);
  }

  performStatutAction(): void {
    const commercial = this.selectedCommercial();
    const item = this.statutTarget();
    const statut = this.statutAction();
    if (!commercial || !item || !statut) return;
    this.statutPending.set(true);
    this.commercialService.updatePerformanceStatut(commercial.id, item.moisComptableId, { statut }).subscribe({
      next: () => {
        this.statutPending.set(false);
        this.statutTarget.set(null);
        this.statutAction.set(null);
        this.fetchDetailPerformance();
      },
      error: (err: unknown) => {
        this.statutPending.set(false);
        this.detailPerformanceError.set(
          extractErrorMessage(err, 'Impossible de mettre à jour le statut de cette performance.'),
        );
        this.statutTarget.set(null);
        this.statutAction.set(null);
      },
    });
  }

  // ================= Tableau de bord & Classement =================

  readonly dashboard = signal<CommercialManagementDashboardResponse | null>(null);
  readonly dashboardLoading = signal(false);
  readonly dashboardError = signal<string | null>(null);
  readonly dashboardExerciceId = signal('');
  readonly dashboardMoisId = signal('');

  readonly rankingItems = signal<CommercialRankingEntry[]>([]);
  readonly rankingLoading = signal(false);
  readonly rankingError = signal<string | null>(null);

  readonly exporting = signal(false);
  readonly exportError = signal<string | null>(null);

  setDashboardExercice(exerciceId: string): void {
    this.dashboardExerciceId.set(exerciceId);
    this.dashboardMoisId.set('');
  }

  setDashboardMois(moisId: string): void {
    this.dashboardMoisId.set(moisId);
    this.fetchDashboard();
  }

  /** Lancés en parallèle depuis le correctif backend du 409 CONCURRENT_MODIFICATION (confirmé
   * corrigé le 2026-09-07 — le chemin de lecture des deux endpoints ne persiste plus rien).
   * Contournement séquentiel retiré, voir missing_backend_dependencies.md. */
  fetchDashboard(): void {
    const moisId = this.dashboardMoisId() ? Number(this.dashboardMoisId()) : undefined;
    this.dashboardLoading.set(true);
    this.dashboardError.set(null);
    this.commercialService.managementDashboard(moisId).subscribe({
      next: (result) => {
        this.dashboard.set(result);
        this.dashboardLoading.set(false);
      },
      error: () => {
        this.dashboardError.set('Impossible de charger le tableau de bord.');
        this.dashboardLoading.set(false);
      },
    });
    this.fetchRanking(moisId);
  }

  private fetchRanking(moisId: number | undefined): void {
    this.rankingLoading.set(true);
    this.rankingError.set(null);
    this.commercialService.ranking(moisId).subscribe({
      next: (items) => {
        this.rankingItems.set(items);
        this.rankingLoading.set(false);
      },
      error: () => {
        this.rankingError.set('Impossible de charger le classement.');
        this.rankingLoading.set(false);
      },
    });
  }

  exportDashboard(): void {
    this.exporting.set(true);
    this.exportError.set(null);
    const moisComptableId = this.dashboardMoisId() ? Number(this.dashboardMoisId()) : undefined;
    this.commercialService.exportPerformance({ moisComptableId }).subscribe({
      next: (blob) => {
        this.downloadBlob(blob, `assurzen-performance-commerciale-${this.exportTimestamp()}.csv`);
        this.exporting.set(false);
      },
      error: () => {
        this.exportError.set("Impossible d'exporter la performance commerciale.");
        this.exporting.set(false);
      },
    });
  }

  private exportTimestamp(): string {
    return new Date().toISOString().replace(/[:.]/g, '-');
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  // ================= Paie =================

  readonly payrollItems = signal<CommercialPayrollEntry[]>([]);
  readonly payrollLoading = signal(false);
  readonly payrollError = signal<string | null>(null);
  readonly payrollExerciceId = signal('');
  readonly payrollMoisId = signal('');

  setPayrollExercice(exerciceId: string): void {
    this.payrollExerciceId.set(exerciceId);
    this.payrollMoisId.set('');
  }

  setPayrollMois(moisId: string): void {
    this.payrollMoisId.set(moisId);
    this.fetchPayroll();
  }

  fetchPayroll(): void {
    const moisId = this.payrollMoisId();
    if (!moisId) return;
    this.payrollLoading.set(true);
    this.payrollError.set(null);
    this.commercialService.payroll(Number(moisId)).subscribe({
      next: (items) => {
        this.payrollItems.set(items);
        this.payrollLoading.set(false);
      },
      error: () => {
        this.payrollError.set('Impossible de charger les éléments de paie.');
        this.payrollLoading.set(false);
      },
    });
  }

  // ================= Commissions (paiement, séparé du cycle bonus mensuel) =================

  readonly commissionSummaries = signal<CommercialCommissionSummary[]>([]);
  readonly commissionsLoading = signal(false);
  readonly commissionsError = signal<string | null>(null);
  readonly commissionsSearch = signal('');

  readonly filteredCommissionSummaries = computed(() => {
    const query = this.commissionsSearch().trim().toLowerCase();
    if (!query) return this.commissionSummaries();
    return this.commissionSummaries().filter((s) =>
      `${s.nomComplet} ${s.employeeCode}`.toLowerCase().includes(query)
    );
  });

  fetchCommissionSummaries(): void {
    this.commissionsLoading.set(true);
    this.commissionsError.set(null);
    this.commercialCommissionService.getCommercialsSummary().subscribe({
      next: (items) => {
        this.commissionSummaries.set(items);
        this.commissionsLoading.set(false);
      },
      error: () => {
        this.commissionsError.set('Impossible de charger les commissions commerciales.');
        this.commissionsLoading.set(false);
      },
    });
  }

  onCommissionsSearch(value: string): void {
    this.commissionsSearch.set(value);
  }

  // Contracts drill-down modal ------------------------------------------

  readonly commissionContractsModalVisible = signal(false);
  readonly selectedCommissionCommercial = signal<CommercialCommissionSummary | null>(null);
  readonly commissionContracts = signal<ContractCommercialCommission[]>([]);
  readonly commissionContractsLoading = signal(true);
  readonly commissionContractsError = signal<string | null>(null);
  readonly commissionContractsStatusFilter = signal<CommissionContractStatusFilter>('pending');
  readonly selectedCommissionContractIds = signal<Set<number>>(new Set());
  readonly commissionPaymentReference = signal('');
  readonly commissionPaying = signal(false);
  readonly commissionPayError = signal<string | null>(null);
  /** Résultat détaillé du dernier lot payé — chaque entrée peut avoir échoué individuellement
   * côté SycaPay, donc on ne suppose jamais "demandé = payé" (intégration du 2026-09-22). */
  readonly commissionPayResult = signal<CommercialCommissionPaymentResponse[] | null>(null);

  readonly commissionPayHasFailures = computed(() => {
    const results = this.commissionPayResult();
    return !!results && results.some((r) => r.sycapayStatus === 'FAILED');
  });

  /** Bandeau de synthèse du résultat : ton global + décompte par issue. */
  readonly commissionPaySummary = computed(() => {
    const results = this.commissionPayResult() ?? [];
    const paid = results.filter((r) => r.sycapayStatus === 'SUCCESS').length;
    const pending = results.filter((r) => r.sycapayStatus === 'PROCESSING' || r.sycapayStatus === 'UNKNOWN').length;
    const failed = results.length - paid - pending;
    const plural = (n: number, word: string) => `${n} ${word}${n > 1 ? 's' : ''}`;
    const parts = [
      paid ? `${plural(paid, 'payé')}` : '',
      pending ? `${pending} à vérifier` : '',
      failed ? `${plural(failed, 'échoué')}` : '',
    ].filter(Boolean);
    const total = this.formatAmount(results.filter((r) => r.sycapayStatus === 'SUCCESS').reduce((sum, r) => sum + r.amount, 0));
    if (paid === results.length) {
      return { variant: 'success', title: paid > 1 ? 'Paiements effectués' : 'Paiement effectué', detail: `${plural(paid, 'contrat')} payé${paid > 1 ? 's' : ''} · ${total} versés via SycaPay` };
    }
    if (paid === 0 && pending === 0) {
      return { variant: 'danger', title: 'Paiement non effectué', detail: parts.join(' · ') };
    }
    return { variant: 'warning', title: 'Paiement partiellement effectué', detail: parts.join(' · ') };
  });

  /** Les messages SycaPay arrivent en majuscules (« DEPOT EFFECTUE AVEC SUCCESS ») : casse de phrase. */
  sycapayMessageLabel(message: string | null): string {
    if (!message) return '';
    const lower = message.toLowerCase();
    return lower.charAt(0).toUpperCase() + lower.slice(1);
  }

  readonly commissionPayHasPending = computed(() => {
    const results = this.commissionPayResult();
    return !!results && results.some((r) => this.needsReconcile(r));
  });

  /** UNKNOWN/PROCESSING : issue incertaine, ne jamais relancer le paiement — réconcilier. */
  needsReconcile(result: CommercialCommissionPaymentResponse): boolean {
    return (result.sycapayStatus === 'UNKNOWN' || result.sycapayStatus === 'PROCESSING') && !!result.sycapayTransactionReference;
  }

  /** Query params de /sycapay : l'id direct si le backend le fournit, sinon la référence. */
  reconcileQueryParams(result: CommercialCommissionPaymentResponse): Record<string, string | number> {
    return result.sycapayTransactionPk != null
      ? { transaction: result.sycapayTransactionPk }
      : { reference: result.sycapayTransactionReference ?? '' };
  }

  readonly filteredCommissionContracts = computed(() => {
    const filter = this.commissionContractsStatusFilter();
    return this.commissionContracts().filter((c) => filter === 'all' || c.commercialCommissionStatus === filter);
  });

  readonly selectedCommissionTotal = computed(() => {
    const ids = this.selectedCommissionContractIds();
    return this.commissionContracts()
      .filter((c) => ids.has(c.contractId))
      .reduce((sum, c) => sum + c.commercialCommissionAmount, 0);
  });

  /** Scopé à ce qui est actuellement affiché, comme pour les commissions partenaire. */
  readonly visiblePendingCommissionContractIds = computed(() =>
    this.filteredCommissionContracts()
      .filter((c) => c.commercialCommissionStatus === 'pending')
      .map((c) => c.contractId)
  );

  readonly allPendingCommissionContractsSelected = computed(() => {
    const pendingIds = this.visiblePendingCommissionContractIds();
    return pendingIds.length > 0 && pendingIds.every((id) => this.selectedCommissionContractIds().has(id));
  });

  openCommissionContracts(item: CommercialCommissionSummary): void {
    this.selectedCommissionCommercial.set(item);
    this.selectedCommissionContractIds.set(new Set());
    this.commissionPaymentReference.set('');
    this.commissionPayError.set(null);
    this.commissionPayResult.set(null);
    this.commissionContractsStatusFilter.set('pending');
    this.commissionContractsModalVisible.set(true);
    this.fetchCommissionContracts();
  }

  closeCommissionContractsModal(): void {
    this.commissionContractsModalVisible.set(false);
  }

  closeCommissionPayResult(): void {
    this.commissionPayResult.set(null);
  }

  fetchCommissionContracts(): void {
    const commercial = this.selectedCommissionCommercial();
    if (!commercial) return;
    this.commissionContractsLoading.set(true);
    this.commissionContractsError.set(null);
    this.commercialCommissionService.getCommercialContracts(commercial.commercialId).subscribe({
      next: (contracts) => {
        this.commissionContracts.set(contracts);
        this.commissionContractsLoading.set(false);
      },
      error: () => {
        this.commissionContractsError.set('Impossible de charger les contrats de ce commercial.');
        this.commissionContractsLoading.set(false);
      },
    });
  }

  setCommissionContractsStatusFilter(filter: CommissionContractStatusFilter): void {
    this.commissionContractsStatusFilter.set(filter);
    this.selectedCommissionContractIds.set(new Set());
  }

  isCommissionContractSelected(contractId: number): boolean {
    return this.selectedCommissionContractIds().has(contractId);
  }

  toggleCommissionContract(contractId: number): void {
    const set = new Set(this.selectedCommissionContractIds());
    if (set.has(contractId)) {
      set.delete(contractId);
    } else {
      set.add(contractId);
    }
    this.selectedCommissionContractIds.set(set);
  }

  toggleSelectAllPendingCommissionContracts(): void {
    this.selectedCommissionContractIds.set(
      this.allPendingCommissionContractsSelected() ? new Set() : new Set(this.visiblePendingCommissionContractIds()),
    );
  }

  onCommissionReferenceInput(value: string): void {
    this.commissionPaymentReference.set(value);
  }

  payCommercialCommission(): void {
    const contractIds = Array.from(this.selectedCommissionContractIds());
    if (contractIds.length === 0) return;
    this.commissionPaying.set(true);
    this.commissionPayError.set(null);
    this.commissionPayResult.set(null);
    this.commercialCommissionService
      .pay({ contractIds, reference: this.commissionPaymentReference().trim() || undefined })
      .subscribe({
        next: (results) => {
          this.commissionPaying.set(false);
          this.commissionPayResult.set(results);
          // Seuls les FAILED restent sélectionnés (relance possible). UNKNOWN/PROCESSING ne doivent
          // PAS être relancés (issue incertaine → réconcilier), REJECTED = déjà en cours/payé.
          const retryable = new Set(results.filter((r) => r.sycapayStatus === 'FAILED').map((r) => r.contractId));
          this.selectedCommissionContractIds.set(retryable);
          if (retryable.size === 0) this.commissionPaymentReference.set('');
          this.fetchCommissionContracts();
          this.fetchCommissionSummaries();
          this.fetchCommissionPayments();
        },
        error: (err: unknown) => {
          this.commissionPaying.set(false);
          this.commissionPayError.set(sycapayErrorMessage(err, 'Impossible de payer cette commission. Réessayez.', 'pay'));
        },
      });
  }

  sycapayStatusLabel(status: SycaPayStatus | null): string {
    switch (status) {
      case 'SUCCESS':
        return 'Payé';
      case 'PROCESSING':
        return 'En cours';
      case 'FAILED':
        return 'Échoué';
      case 'UNKNOWN':
        return 'Indéterminé';
      case 'REJECTED':
        return 'Rejeté';
      default:
        return '—';
    }
  }

  sycapayStatusVariant(status: SycaPayStatus | null): string {
    switch (status) {
      case 'SUCCESS':
        return 'success';
      case 'PROCESSING':
        return 'info';
      case 'FAILED':
      case 'REJECTED':
        return 'danger';
      case 'UNKNOWN':
        return 'warning';
      default:
        return 'neutral';
    }
  }

  // Payment history --------------------------------------------------------

  readonly commissionPayments = signal<CommercialCommissionPaymentResponse[]>([]);
  readonly commissionPaymentsPageIndex = signal(0);
  readonly commissionPaymentsTotalElements = signal(0);
  readonly commissionPaymentsTotalPages = signal(0);
  readonly commissionPaymentsLoading = signal(true);
  readonly commissionPaymentsError = signal<string | null>(null);
  readonly commissionPaymentsPageSize = 15;

  fetchCommissionPayments(): void {
    this.commissionPaymentsLoading.set(true);
    this.commissionPaymentsError.set(null);
    this.commercialCommissionService
      .getPayments(this.commissionPaymentsPageIndex(), this.commissionPaymentsPageSize)
      .subscribe({
        next: (result) => {
          this.commissionPayments.set(result.content);
          this.commissionPaymentsTotalElements.set(result.totalElements);
          this.commissionPaymentsTotalPages.set(result.totalPages);
          this.commissionPaymentsLoading.set(false);
        },
        error: () => {
          this.commissionPaymentsError.set("Impossible de charger l'historique des paiements.");
          this.commissionPaymentsLoading.set(false);
        },
      });
  }

  goToCommissionPaymentsPage(index: number): void {
    if (index < 0 || index >= this.commissionPaymentsTotalPages()) return;
    this.commissionPaymentsPageIndex.set(index);
    this.fetchCommissionPayments();
  }

  // ================= Présentation =================

  commercialStatutLabel(statut: CommercialStatut): string {
    switch (statut) {
      case 'ACTIVE':
        return 'Actif';
      case 'INACTIVE':
        return 'Inactif';
      case 'SUSPENDED':
        return 'Suspendu';
      case 'TERMINATED':
        return 'Sorti';
    }
  }

  commercialStatutVariant(statut: CommercialStatut): string {
    switch (statut) {
      case 'ACTIVE':
        return 'success';
      case 'INACTIVE':
        return 'neutral';
      case 'SUSPENDED':
        return 'warning';
      case 'TERMINATED':
        return 'danger';
    }
  }

  statutLabel(statut: CommercialPerformanceStatut): string {
    switch (statut) {
      case 'OPEN':
        return 'Ouvert';
      case 'CALCULATED':
        return 'Calculé';
      case 'ELIGIBLE':
        return 'Éligible (estimé)';
      case 'NOT_ELIGIBLE':
        return 'Non éligible';
      case 'VALIDATED':
        return 'Validé';
      case 'PAID':
        return 'Payé';
      case 'CLOSED':
        return 'Clôturé';
    }
  }

  statutVariant(statut: CommercialPerformanceStatut): string {
    switch (statut) {
      case 'OPEN':
      case 'CALCULATED':
        return 'neutral';
      case 'ELIGIBLE':
        return 'warning';
      case 'NOT_ELIGIBLE':
        return 'danger';
      case 'VALIDATED':
        return 'info';
      case 'PAID':
        return 'success';
      case 'CLOSED':
        return 'neutral';
    }
  }

  /** Montant définitif uniquement à partir de VALIDATED — avant, toujours "estimé"
   * (règle explicite du contrat, ne jamais présenter le bonus comme acquis). */
  isBonusEstime(statut: CommercialPerformanceStatut): boolean {
    return statut === 'OPEN' || statut === 'CALCULATED' || statut === 'ELIGIBLE' || statut === 'NOT_ELIGIBLE';
  }

  canRecalculate(statut: CommercialPerformanceStatut): boolean {
    return statut !== 'VALIDATED' && statut !== 'PAID' && statut !== 'CLOSED';
  }

  tauxVariant(taux: number | null): string {
    if (taux === null) return 'neutral';
    if (taux >= 100) return 'success';
    if (taux >= 50) return 'warning';
    return 'danger';
  }

  tauxProgressColor(taux: number | null): string {
    if (taux === null) return 'secondary';
    if (taux >= 100) return 'success';
    if (taux >= 50) return 'warning';
    return 'danger';
  }

  initials(nomComplet: string): string {
    return nomComplet
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');
  }

  formatAmount(value: number | null): string {
    if (value === null) return '—';
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }
}
