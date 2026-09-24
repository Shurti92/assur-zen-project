import { AfterContentInit, ChangeDetectorRef, Component, computed, inject, signal } from '@angular/core';
import { getStyle } from '@coreui/utils';
import { ChartjsComponent } from '@coreui/angular-chartjs';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, ColComponent, RowComponent, SpinnerComponent } from '@coreui/angular';

import { SatisfactionDashboardService } from '../../../../core/satisfaction/satisfaction-dashboard.service';
import {
  NpsBreakdownResponse,
  RepartitionNotesResponse,
  SatisfactionMensuelleResponse,
  SatisfactionOverviewResponse,
  SatisfactionParDimensionItem,
} from '../../../../core/satisfaction/models/satisfaction-dashboard.model';
import { ReclamationStatistiquesResponse } from '../../../../core/reclamations/models/reclamation.model';

interface KpiCard {
  key: string;
  label: string;
  icon: string;
  color: string;
  value: string;
  sub: string;
  subVariant: 'ok' | 'warning' | 'neutral';
}

@Component({
  selector: 'app-satisfaction-dashboard',
  templateUrl: './satisfaction-dashboard.component.html',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, RowComponent, ColComponent, ChartjsComponent],
})
export class SatisfactionDashboardComponent implements AfterContentInit {
  private readonly dashboardService = inject(SatisfactionDashboardService);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly exportingCsv = signal(false);
  readonly exportingPdf = signal(false);

  readonly overview = signal<SatisfactionOverviewResponse | null>(null);
  readonly nps = signal<NpsBreakdownResponse | null>(null);
  readonly mensuel = signal<SatisfactionMensuelleResponse[]>([]);
  readonly parCourtier = signal<SatisfactionParDimensionItem[]>([]);
  readonly parGestionnaire = signal<SatisfactionParDimensionItem[]>([]);
  readonly parProduit = signal<SatisfactionParDimensionItem[]>([]);
  readonly repartitionNotes = signal<RepartitionNotesResponse | null>(null);
  readonly reclamationsStats = signal<ReclamationStatistiquesResponse | null>(null);

  readonly kpis = computed<KpiCard[]>(() => {
    const o = this.overview();
    if (!o) return [];
    return [
      {
        key: 'satisfaction',
        label: 'Satisfaction moyenne',
        icon: 'cilSpeedometer',
        color: 'primary',
        value: `${this.formatDecimal(o.satisfactionMoyenne)} / 10`,
        sub: `${this.formatInt(o.reponses)} réponse(s)`,
        subVariant: 'neutral',
      },
      {
        key: 'nps',
        label: 'Score NPS',
        icon: 'trendUp',
        color: o.nps == null ? 'primary' : o.nps >= 0 ? 'success' : 'danger',
        value: o.nps == null ? '—' : `${o.nps}`,
        sub: o.nps == null ? 'Aucune réponse' : o.nps >= 0 ? 'Positif' : 'Négatif',
        subVariant: o.nps == null ? 'neutral' : o.nps >= 0 ? 'ok' : 'warning',
      },
      {
        key: 'taux-reponse',
        label: 'Taux de réponse',
        icon: 'cilChartPie',
        color: 'info',
        value: o.tauxReponse == null ? '—' : `${this.formatDecimal(o.tauxReponse)} %`,
        sub: `${this.formatInt(o.enquetesEnvoyees)} enquête(s) envoyée(s)`,
        subVariant: 'neutral',
      },
      {
        key: 'reclamations-ouvertes',
        label: 'Réclamations ouvertes',
        icon: 'cilWarning',
        color: o.reclamationsOuvertes > 0 ? 'warning' : 'primary',
        value: this.formatInt(o.reclamationsOuvertes),
        sub: `${this.formatInt(o.reclamationsResolues)} résolue(s)`,
        subVariant: 'neutral',
      },
      {
        key: 'delai-traitement',
        label: 'Délai moyen de traitement',
        icon: 'clock',
        color: 'primary',
        value: o.delaiMoyenTraitementHeures == null ? '—' : `${this.formatDecimal(o.delaiMoyenTraitementHeures)} h`,
        sub: 'Réclamations',
        subVariant: 'neutral',
      },
    ];
  });

  readonly npsTotal = computed(() => this.nps()?.totalReponses ?? 0);

  readonly prioriteBreakdown = computed(() => {
    const stats = this.reclamationsStats();
    if (!stats) return [];
    return Object.entries(stats.parPriorite);
  });

  npsData: any = null;
  npsOptions: any = null;
  notesData: any = null;
  notesOptions: any = null;
  mensuelData: any = null;
  mensuelOptions: any = null;

  constructor() {
    this.fetch();
  }

  ngAfterContentInit(): void {
    this.changeDetectorRef.detectChanges();
  }

  private pendingRequests = 0;

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.pendingRequests = 7;

    this.dashboardService.overview().subscribe({
      next: (data) => {
        this.overview.set(data);
        this.checkDone();
      },
      error: () => {
        this.error.set("Impossible de charger les indicateurs de satisfaction.");
        this.checkDone();
      },
    });

    this.dashboardService.nps().subscribe({
      next: (data) => {
        this.nps.set(data);
        this.buildNpsChart();
        this.checkDone();
      },
      error: () => this.checkDone(),
    });

    this.dashboardService.repartitionNotes().subscribe({
      next: (data) => {
        this.repartitionNotes.set(data);
        this.buildNotesChart();
        this.checkDone();
      },
      error: () => this.checkDone(),
    });

    this.dashboardService.mensuel().subscribe({
      next: (data) => {
        this.mensuel.set(data);
        this.buildMensuelChart();
        this.checkDone();
      },
      error: () => this.checkDone(),
    });

    this.dashboardService.parCourtier().subscribe({
      next: (data) => {
        this.parCourtier.set(data);
        this.checkDone();
      },
      error: () => this.checkDone(),
    });

    this.dashboardService.parGestionnaire().subscribe({
      next: (data) => {
        this.parGestionnaire.set(data);
        this.checkDone();
      },
      error: () => this.checkDone(),
    });

    this.dashboardService.parProduit().subscribe({
      next: (data) => {
        this.parProduit.set(data);
        this.checkDone();
      },
      error: () => this.checkDone(),
    });

    this.dashboardService.reclamationsStats().subscribe({
      next: (data) => this.reclamationsStats.set(data),
      error: () => undefined,
    });
  }

  private checkDone(): void {
    this.pendingRequests -= 1;
    if (this.pendingRequests <= 0) {
      this.loading.set(false);
    }
  }

  private buildNpsChart(): void {
    const nps = this.nps();
    if (!nps) return;
    const colorBody = getStyle('--cui-body-color');
    this.npsData = {
      labels: ['Promoteurs', 'Passifs', 'Détracteurs'],
      datasets: [
        {
          data: [nps.promoteurs, nps.passifs, nps.detracteurs],
          backgroundColor: [getStyle('--cui-success'), getStyle('--cui-warning'), getStyle('--cui-danger')],
          borderWidth: 0,
        },
      ],
    };
    this.npsOptions = {
      maintainAspectRatio: false,
      cutout: '68%',
      plugins: { legend: { display: false }, tooltip: { titleColor: colorBody } },
    };
  }

  private buildNotesChart(): void {
    const repartition = this.repartitionNotes();
    if (!repartition) return;
    const colorBorderTranslucent = getStyle('--cui-border-color-translucent');
    const colorBody = getStyle('--cui-body-color');
    const brandPrimary = getStyle('--cui-primary') ?? '#5856d6';
    const notes = Object.keys(repartition.parNote).sort((a, b) => Number(a) - Number(b));
    this.notesData = {
      labels: notes,
      datasets: [
        {
          label: 'Réponses',
          data: notes.map((n) => repartition.parNote[n]),
          backgroundColor: brandPrimary,
          borderRadius: 4,
        },
      ],
    };
    this.notesOptions = {
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { display: false }, ticks: { color: colorBody } },
        y: { border: { color: colorBorderTranslucent }, grid: { color: colorBorderTranslucent }, beginAtZero: true, ticks: { color: colorBody, precision: 0 } },
      },
    };
  }

  private buildMensuelChart(): void {
    const rows = this.mensuel();
    const colorBorderTranslucent = getStyle('--cui-border-color-translucent');
    const colorBody = getStyle('--cui-body-color');
    const brandPrimary = getStyle('--cui-primary') ?? '#5856d6';
    const brandPrimaryRgb = getStyle('--cui-primary-rgb') ?? '88, 86, 214';
    const brandSuccess = getStyle('--cui-success') ?? '#2eb85c';

    this.mensuelData = {
      labels: rows.map((r) => this.formatMonth(r.mois)),
      datasets: [
        {
          label: 'Satisfaction moyenne',
          data: rows.map((r) => r.satisfactionMoyenne),
          borderColor: brandPrimary,
          backgroundColor: `rgba(${brandPrimaryRgb}, .14)`,
          fill: true,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 4,
          yAxisID: 'y',
        },
        {
          label: 'NPS',
          data: rows.map((r) => r.nps),
          borderColor: brandSuccess,
          backgroundColor: 'transparent',
          fill: false,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 4,
          yAxisID: 'y1',
        },
      ],
    };
    this.mensuelOptions = {
      maintainAspectRatio: false,
      plugins: { legend: { display: true, position: 'bottom', labels: { color: colorBody, boxWidth: 10, usePointStyle: true } } },
      scales: {
        x: { grid: { color: colorBorderTranslucent, drawOnChartArea: false }, ticks: { color: colorBody, maxRotation: 0 } },
        y: { position: 'left', border: { color: colorBorderTranslucent }, grid: { color: colorBorderTranslucent }, min: 0, max: 10, ticks: { color: colorBody } },
        y1: { position: 'right', min: -100, max: 100, grid: { display: false }, ticks: { color: colorBody } },
      },
    };
  }

  exportCsv(): void {
    this.exportingCsv.set(true);
    this.dashboardService.exportCsv().subscribe({
      next: (blob) => {
        this.exportingCsv.set(false);
        this.downloadBlob(blob, 'satisfaction-dashboard.csv');
      },
      error: () => this.exportingCsv.set(false),
    });
  }

  exportPdf(): void {
    this.exportingPdf.set(true);
    this.dashboardService.exportPdf().subscribe({
      next: (blob) => {
        this.exportingPdf.set(false);
        this.downloadBlob(blob, 'satisfaction-dashboard.pdf');
      },
      error: () => this.exportingPdf.set(false),
    });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }

  formatInt(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(value);
  }

  formatDecimal(value: number | null): string {
    if (value == null) return '—';
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(value);
  }

  formatMonth(value: string): string {
    const [year, month] = value.split('-');
    if (!month) return value;
    const date = new Date(Number(year), Number(month) - 1, 1);
    return date.toLocaleDateString('fr-FR', { month: 'short', year: '2-digit' });
  }
}
