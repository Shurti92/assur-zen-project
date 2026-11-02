import { AfterContentInit, ChangeDetectorRef, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { getStyle } from '@coreui/utils';
import { ChartjsComponent } from '@coreui/angular-chartjs';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  ColComponent,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  RowComponent,
  SpinnerComponent,
} from '@coreui/angular';

import { FideliteService } from '../../../core/fidelite/fidelite.service';
import { VipDashboardService } from '../../../core/fidelite/vip-dashboard.service';
import {
  CompteFideliteResponse,
  StatutVip,
} from '../../../core/fidelite/models/fidelite.model';
import {
  VipMensuelItem,
  VipOverviewResponse,
  VipParDimensionItem,
  VipParNiveauItem,
  VipTopClientItem,
} from '../../../core/fidelite/models/vip-dashboard.model';
import { ProfileService } from '../../../core/profile/profile.service';
import { ProfileRecord } from '../../../core/profile/models/profile-details.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

const STATUT_VIP_LABEL: Record<StatutVip, string> = {
  NON_VIP: 'Non VIP',
  VIP_ACTIF: 'VIP actif',
  VIP_SUSPENDU: 'VIP suspendu',
  VIP_SORTI: 'VIP sorti',
};

const STATUT_VIP_VARIANT: Record<StatutVip, string> = {
  NON_VIP: 'neutral',
  VIP_ACTIF: 'success',
  VIP_SUSPENDU: 'warning',
  VIP_SORTI: 'danger',
};

const STATUT_VIP_ORDER: StatutVip[] = ['VIP_ACTIF', 'VIP_SUSPENDU', 'VIP_SORTI', 'NON_VIP'];

interface KpiCard {
  key: string;
  label: string;
  icon: string;
  color: string;
  value: string;
  sub: string;
}

type StatutVipFilter = 'all' | StatutVip;

@Component({
  selector: 'app-clients-vip',
  templateUrl: './clients-vip.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    RowComponent,
    ColComponent,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    AlertMessageComponent,
    ModalComponent,
    ChartjsComponent,
  ],
})
export class ClientsVipComponent implements AfterContentInit {
  private readonly vipDashboardService = inject(VipDashboardService);
  private readonly fideliteService = inject(FideliteService);
  private readonly profileService = inject(ProfileService);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);
  private readonly fb = inject(FormBuilder);

  readonly statutVipLabel = STATUT_VIP_LABEL;
  readonly statutVipOrder = STATUT_VIP_ORDER;

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly overview = signal<VipOverviewResponse | null>(null);
  readonly mensuel = signal<VipMensuelItem[]>([]);
  readonly parCourtier = signal<VipParDimensionItem[]>([]);
  readonly parGestionnaire = signal<VipParDimensionItem[]>([]);
  readonly parProduit = signal<VipParDimensionItem[]>([]);
  readonly parNiveau = signal<VipParNiveauItem[]>([]);
  readonly topClients = signal<VipTopClientItem[]>([]);

  readonly kpis = computed<KpiCard[]>(() => {
    const o = this.overview();
    if (!o) return [];
    return [
      {
        key: 'total-vip',
        label: 'Clients VIP',
        icon: 'cilStar',
        color: 'primary',
        value: this.formatInt(o.totalVip),
        sub: `sur ${this.formatInt(o.totalComptes)} comptes fidélité`,
      },
      {
        key: 'score-moyen',
        label: 'Score moyen VIP',
        icon: 'cilSpeedometer',
        color: 'info',
        value: this.formatDecimal(o.scoreMoyenVip),
        sub: 'Score de fidélité',
      },
      {
        key: 'prime',
        label: 'Prime générée VIP',
        icon: 'dollar',
        color: 'success',
        value: this.formatAmount(o.primeGenereeVip),
        sub: 'Cumul',
      },
      {
        key: 'recompenses',
        label: 'Récompenses distribuées',
        icon: 'cilBasket',
        color: 'warning',
        value: this.formatInt(o.recompensesDistribuees),
        sub: `${this.formatInt(o.pointsRecompensesDistribues)} points utilisés`,
      },
      {
        key: 'mouvement',
        label: 'Mouvement du mois',
        icon: 'trendUp',
        color: o.entreesVipMoisCourant >= o.sortiesVipMoisCourant ? 'primary' : 'danger',
        value: `+${o.entreesVipMoisCourant} / -${o.sortiesVipMoisCourant}`,
        sub: 'Entrées / sorties VIP',
      },
    ];
  });

  mensuelData: any = null;
  mensuelOptions: any = null;
  niveauData: any = null;
  niveauOptions: any = null;
  readonly niveauLegend = computed(() => this.buildNiveauLegend());

  // Comptes management -------------------------------------------------------------------

  readonly comptes = signal<CompteFideliteResponse[]>([]);
  readonly comptesLoading = signal(true);
  readonly comptesError = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly statutVipFilter = signal<StatutVipFilter>('all');
  readonly gestionnaires = signal<ProfileRecord[]>([]);

  readonly filteredComptes = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const statut = this.statutVipFilter();
    let items = this.comptes();
    if (statut !== 'all') items = items.filter((c) => c.statutVip === statut);
    if (!query) return items;
    return items.filter((c) => (c.insuredName ?? '').toLowerCase().includes(query));
  });

  readonly detailVisible = signal(false);
  readonly selectedCompte = signal<CompteFideliteResponse | null>(null);

  readonly statutVipForm = this.fb.nonNullable.group({ statut: 'VIP_ACTIF' as StatutVip, motif: '' });
  readonly statutVipSaving = signal(false);
  readonly statutVipError = signal<string | null>(null);

  readonly gestionnaireForm = this.fb.nonNullable.group({ gestionnaireId: 0 });
  readonly gestionnaireSaving = signal(false);
  readonly gestionnaireError = signal<string | null>(null);

  readonly recalculating = signal(false);
  readonly recalculateError = signal<string | null>(null);

  constructor() {
    this.fetch();
    this.profileService.list().subscribe({
      next: (profiles) => this.gestionnaires.set(profiles),
      error: () => undefined,
    });
  }

  ngAfterContentInit(): void {
    this.changeDetectorRef.detectChanges();
  }

  private pendingRequests = 0;

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.pendingRequests = 5;

    this.vipDashboardService.overview().subscribe({
      next: (data) => {
        this.overview.set(data);
        this.checkDone();
      },
      error: () => {
        this.error.set('Impossible de charger les indicateurs VIP.');
        this.checkDone();
      },
    });
    this.vipDashboardService.mensuel().subscribe({
      next: (data) => {
        this.mensuel.set(data);
        this.buildMensuelChart();
        this.checkDone();
      },
      error: () => this.checkDone(),
    });
    this.vipDashboardService.parNiveau().subscribe({
      next: (data) => {
        this.parNiveau.set(data);
        this.buildNiveauChart();
        this.checkDone();
      },
      error: () => this.checkDone(),
    });
    this.vipDashboardService.parCourtier().subscribe({
      next: (data) => {
        this.parCourtier.set(data);
        this.checkDone();
      },
      error: () => this.checkDone(),
    });
    this.vipDashboardService.parGestionnaire().subscribe({
      next: (data) => {
        this.parGestionnaire.set(data);
        this.checkDone();
      },
      error: () => this.checkDone(),
    });
    this.vipDashboardService.parProduit().subscribe({
      next: (data) => this.parProduit.set(data),
      error: () => undefined,
    });
    this.vipDashboardService.topClients(10).subscribe({
      next: (data) => this.topClients.set(data),
      error: () => undefined,
    });

    this.fetchComptes();
  }

  private checkDone(): void {
    this.pendingRequests -= 1;
    if (this.pendingRequests <= 0) this.loading.set(false);
  }

  fetchComptes(): void {
    this.comptesLoading.set(true);
    this.comptesError.set(null);
    this.fideliteService.listComptes(0, 500).subscribe({
      next: (page) => {
        this.comptes.set(page.content);
        this.comptesLoading.set(false);
      },
      error: () => {
        this.comptesError.set('Impossible de charger les comptes fidélité.');
        this.comptesLoading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  setStatutVipFilter(value: string): void {
    this.statutVipFilter.set(value as StatutVipFilter);
  }

  private buildMensuelChart(): void {
    const rows = this.mensuel();
    const colorBorderTranslucent = getStyle('--cui-border-color-translucent');
    const colorBody = getStyle('--cui-body-color');
    const brandPrimary = getStyle('--cui-primary') ?? '#5856d6';
    const brandPrimaryRgb = getStyle('--cui-primary-rgb') ?? '88, 86, 214';
    const brandDanger = getStyle('--cui-danger') ?? '#e55353';

    this.mensuelData = {
      labels: rows.map((r) => this.formatMonth(r.mois)),
      datasets: [
        {
          label: 'Entrées VIP',
          data: rows.map((r) => r.entrees),
          borderColor: brandPrimary,
          backgroundColor: `rgba(${brandPrimaryRgb}, .14)`,
          fill: true,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
        {
          label: 'Sorties VIP',
          data: rows.map((r) => r.sorties),
          borderColor: brandDanger,
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

  private buildNiveauChart(): void {
    const rows = this.parNiveau();
    const colorBody = getStyle('--cui-body-color');
    this.niveauData = {
      labels: rows.map((r) => r.palierLibelle),
      datasets: [
        {
          data: rows.map((r) => r.nombreComptes),
          backgroundColor: rows.map((r) => r.couleur ?? getStyle('--cui-primary')),
          borderWidth: 0,
        },
      ],
    };
    this.niveauOptions = {
      maintainAspectRatio: false,
      cutout: '68%',
      plugins: { legend: { display: false }, tooltip: { titleColor: colorBody } },
    };
  }

  private buildNiveauLegend(): { label: string; value: number; color: string; estVip: boolean }[] {
    return this.parNiveau().map((r) => ({
      label: r.palierLibelle,
      value: r.nombreComptes,
      color: r.couleur ?? '#5856d6',
      estVip: r.estVip,
    }));
  }

  openDetail(compte: CompteFideliteResponse): void {
    this.selectedCompte.set(compte);
    this.statutVipForm.reset({ statut: compte.statutVip, motif: '' });
    this.statutVipError.set(null);
    this.gestionnaireForm.reset({ gestionnaireId: compte.gestionnaireDedieId ?? 0 });
    this.gestionnaireError.set(null);
    this.recalculateError.set(null);
    this.detailVisible.set(true);
  }

  submitStatutVip(): void {
    const compte = this.selectedCompte();
    if (!compte) return;
    const raw = this.statutVipForm.getRawValue();
    this.statutVipSaving.set(true);
    this.statutVipError.set(null);
    this.fideliteService.updateStatutVip(compte.insuredId, { statut: raw.statut, motif: raw.motif.trim() || undefined }).subscribe({
      next: (updated) => {
        this.statutVipSaving.set(false);
        this.selectedCompte.set(updated);
        this.applyUpdate(updated);
        this.fetch();
      },
      error: () => {
        this.statutVipSaving.set(false);
        this.statutVipError.set('Impossible de mettre à jour le statut VIP.');
      },
    });
  }

  submitGestionnaire(): void {
    const compte = this.selectedCompte();
    const raw = this.gestionnaireForm.getRawValue();
    if (!compte) return;
    if (!raw.gestionnaireId) {
      this.gestionnaireError.set('Sélectionnez un gestionnaire.');
      return;
    }
    this.gestionnaireSaving.set(true);
    this.gestionnaireError.set(null);
    this.fideliteService.assignGestionnaire(compte.insuredId, { gestionnaireId: Number(raw.gestionnaireId) }).subscribe({
      next: (updated) => {
        this.gestionnaireSaving.set(false);
        this.selectedCompte.set(updated);
        this.applyUpdate(updated);
      },
      error: () => {
        this.gestionnaireSaving.set(false);
        this.gestionnaireError.set("Impossible d'assigner ce gestionnaire.");
      },
    });
  }

  recalculerScore(): void {
    const compte = this.selectedCompte();
    if (!compte) return;
    this.recalculating.set(true);
    this.recalculateError.set(null);
    this.fideliteService.recalculerScore(compte.insuredId).subscribe({
      next: (updated) => {
        this.recalculating.set(false);
        this.selectedCompte.set(updated);
        this.applyUpdate(updated);
      },
      error: () => {
        this.recalculating.set(false);
        this.recalculateError.set('Impossible de recalculer le score.');
      },
    });
  }

  private applyUpdate(updated: CompteFideliteResponse): void {
    this.comptes.set(this.comptes().map((c) => (c.insuredId === updated.insuredId ? updated : c)));
  }

  gestionnaireName(profile: ProfileRecord): string {
    return `${profile.firstname} ${profile.lastname}`.trim();
  }

  statutVipVariant(statut: StatutVip): string {
    return STATUT_VIP_VARIANT[statut];
  }

  formatInt(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(value);
  }

  formatDecimal(value: number): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(value);
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatShortDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }

  formatMonth(value: string): string {
    const [year, month] = value.split('-');
    if (!month) return value;
    const date = new Date(Number(year), Number(month) - 1, 1);
    return date.toLocaleDateString('fr-FR', { month: 'short', year: '2-digit' });
  }
}
