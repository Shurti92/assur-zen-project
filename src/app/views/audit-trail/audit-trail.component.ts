import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { getStyle } from '@coreui/utils';
import { ChartjsComponent } from '@coreui/angular-chartjs';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  ColComponent,
  FormControlDirective,
  FormDirective,
  FormSelectDirective,
  RowComponent,
  SpinnerComponent,
} from '@coreui/angular';

import { AuditTrailService } from '../../core/audit-trail/audit-trail.service';
import {
  AuditAction,
  AuditLogFilter,
  HistoriqueModificationResponse,
  HistoriqueModificationStatistiquesResponse,
} from '../../core/audit-trail/models/audit-trail.model';
import { ModalComponent } from '../../shared/modal/modal.component';
import {
  AUDIT_ACTION_LABEL,
  AUDIT_ACTION_ORDER,
  AUDIT_ACTION_VARIANT,
  AUDIT_SENSITIVE_ACTIONS,
  AUDIT_STATUT_LABEL,
  AUDIT_STATUT_VARIANT,
} from './audit-trail-status';

type View = 'dashboard' | 'historique' | 'sensibles';
type AuditActionFilter = AuditAction | '';

const TITLES: Record<View, string> = {
  dashboard: 'Tableau de bord',
  historique: 'Historique des modifications',
  sensibles: 'Actions sensibles',
};

interface KpiCard {
  key: string;
  label: string;
  icon: string;
  color: string;
  value: string;
}

@Component({
  selector: 'app-audit-trail',
  templateUrl: './audit-trail.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    RowComponent,
    ColComponent,
    ReactiveFormsModule,
    FormDirective,
    FormControlDirective,
    FormSelectDirective,
    ModalComponent,
    ChartjsComponent,
  ],
})
export class AuditTrailComponent {
  private readonly auditTrailService = inject(AuditTrailService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly view = signal<View>((this.route.snapshot.data['mode'] as View) ?? 'dashboard');
  readonly title = computed(() => TITLES[this.view()]);

  readonly actionOrder = AUDIT_ACTION_ORDER;
  readonly sensitiveActionOrder = AUDIT_SENSITIVE_ACTIONS;
  readonly actionLabel = AUDIT_ACTION_LABEL;
  readonly statutOrder: ('SUCCES' | 'ECHEC')[] = ['SUCCES', 'ECHEC'];
  readonly statutLabel = AUDIT_STATUT_LABEL;

  setView(view: View): void {
    this.view.set(view);
    if (view === 'dashboard' && !this.statistics() && !this.statsLoading()) this.fetchStatistics();
    if (view === 'historique' && this.items().length === 0 && !this.loading()) this.fetchLogs();
    if (view === 'sensibles') {
      if (!this.sensiblesFilterForm.getRawValue().action) {
        this.sensiblesFilterForm.patchValue({ action: this.sensitiveActionOrder[0] });
      }
      this.fetchSensibles();
    }
  }

  // ================= Historique des modifications =================

  readonly items = signal<HistoriqueModificationResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pageIndex = signal(0);
  readonly totalPages = signal(1);
  readonly totalElements = signal(0);
  readonly pageSize = 20;

  readonly filterForm = this.fb.nonNullable.group({
    dateDebut: '',
    dateFin: '',
    userId: '',
    module: '',
    entityName: '',
    entityId: '',
    action: this.fb.nonNullable.control<AuditActionFilter>(''),
    statut: this.fb.nonNullable.control<'SUCCES' | 'ECHEC' | ''>(''),
  });

  private buildFilters(raw: ReturnType<typeof this.filterForm.getRawValue>): AuditLogFilter {
    return {
      dateDebut: raw.dateDebut || undefined,
      dateFin: raw.dateFin || undefined,
      userId: raw.userId ? Number(raw.userId) : undefined,
      module: raw.module.trim() || undefined,
      entityName: raw.entityName.trim() || undefined,
      entityId: raw.entityId.trim() || undefined,
      action: raw.action || undefined,
      statut: raw.statut || undefined,
    };
  }

  fetchLogs(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auditTrailService.search(this.pageIndex(), this.pageSize, this.buildFilters(this.filterForm.getRawValue())).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.totalPages.set(Math.max(1, page.totalPages));
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("Impossible de charger le journal d'audit.");
        this.loading.set(false);
      },
    });
  }

  applyFilters(): void {
    this.pageIndex.set(0);
    this.fetchLogs();
  }

  resetFilters(): void {
    this.filterForm.reset({ dateDebut: '', dateFin: '', userId: '', module: '', entityName: '', entityId: '', action: '', statut: '' });
    this.pageIndex.set(0);
    this.fetchLogs();
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
    this.fetchLogs();
  }

  // ================= Actions sensibles =================
  // Pas de filtre "sensible" côté API — le champ action ne prend qu'une seule valeur à la fois,
  // donc cette vue restreint le sélecteur d'action aux seules actions sensibles et affiche les
  // résultats de l'action choisie (pas un mélange des 6, qui nécessiterait 6 appels serveur).

  readonly sensiblesItems = signal<HistoriqueModificationResponse[]>([]);
  readonly sensiblesLoading = signal(false);
  readonly sensiblesError = signal<string | null>(null);
  readonly sensiblesPageIndex = signal(0);
  readonly sensiblesTotalPages = signal(1);
  readonly sensiblesTotalElements = signal(0);

  readonly sensiblesFilterForm = this.fb.nonNullable.group({
    dateDebut: '',
    dateFin: '',
    action: this.fb.nonNullable.control<AuditActionFilter>(''),
  });

  fetchSensibles(): void {
    const raw = this.sensiblesFilterForm.getRawValue();
    if (!raw.action) return;
    this.sensiblesLoading.set(true);
    this.sensiblesError.set(null);
    this.auditTrailService
      .search(this.sensiblesPageIndex(), this.pageSize, { action: raw.action, dateDebut: raw.dateDebut || undefined, dateFin: raw.dateFin || undefined })
      .subscribe({
        next: (page) => {
          this.sensiblesItems.set(page.content);
          this.sensiblesTotalPages.set(Math.max(1, page.totalPages));
          this.sensiblesTotalElements.set(page.totalElements);
          this.sensiblesLoading.set(false);
        },
        error: () => {
          this.sensiblesError.set("Impossible de charger les actions sensibles.");
          this.sensiblesLoading.set(false);
        },
      });
  }

  applySensiblesFilters(): void {
    this.sensiblesPageIndex.set(0);
    this.fetchSensibles();
  }

  goToSensiblesPage(index: number): void {
    if (index < 0 || index >= this.sensiblesTotalPages()) return;
    this.sensiblesPageIndex.set(index);
    this.fetchSensibles();
  }

  actionVariant(action: AuditAction): string {
    return AUDIT_ACTION_VARIANT[action];
  }

  statutVariant(statut: 'SUCCES' | 'ECHEC'): string {
    return AUDIT_STATUT_VARIANT[statut];
  }

  // ================= Détail =================

  readonly detailVisible = signal(false);
  readonly selectedLog = signal<HistoriqueModificationResponse | null>(null);

  openDetail(log: HistoriqueModificationResponse): void {
    this.selectedLog.set(log);
    this.detailVisible.set(true);
  }

  champsModifiesList(value: string | null): string[] {
    if (!value) return [];
    try {
      const parsed = JSON.parse(value);
      if (Array.isArray(parsed)) return parsed.map((v) => String(v));
    } catch {
      // pas du JSON — traiter comme une liste séparée par des virgules
    }
    return value.split(',').map((v) => v.trim()).filter(Boolean);
  }

  formatValeur(value: string | null): string {
    if (!value) return '—';
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }

  // ================= Export =================

  readonly exportPending = signal(false);
  readonly exporting = signal(false);
  readonly exportError = signal<string | null>(null);

  askExport(): void {
    this.exportPending.set(true);
  }

  cancelExport(): void {
    this.exportPending.set(false);
  }

  confirmExport(): void {
    this.exporting.set(true);
    this.exportError.set(null);
    this.auditTrailService.exportCsv(this.buildFilters(this.filterForm.getRawValue())).subscribe({
      next: (blob) => {
        this.downloadBlob(blob, 'audit-logs.csv');
        this.exporting.set(false);
        this.exportPending.set(false);
      },
      error: () => {
        this.exportError.set("Impossible d'exporter les journaux d'audit.");
        this.exporting.set(false);
        this.exportPending.set(false);
      },
    });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  // ================= Tableau de bord =================

  readonly statistics = signal<HistoriqueModificationStatistiquesResponse | null>(null);
  readonly statsLoading = signal(false);
  readonly statsError = signal<string | null>(null);

  readonly actionsSensiblesTotal = computed(() => {
    const s = this.statistics();
    if (!s) return 0;
    return s.parAction.filter((a) => (this.sensitiveActionOrder as string[]).includes(a.libelle)).reduce((sum, a) => sum + a.nombre, 0);
  });

  readonly kpis = computed<KpiCard[]>(() => {
    const s = this.statistics();
    if (!s) return [];
    return [
      { key: 'total', label: 'Total actions', icon: 'cilNotes', color: 'primary', value: this.formatInt(s.total) },
      { key: 'echecs', label: 'Échecs', icon: 'cilNotes', color: 'danger', value: this.formatInt(s.echecs) },
      { key: 'exports', label: 'Exports', icon: 'download', color: 'info', value: this.formatInt(s.exports) },
      { key: 'connexions', label: 'Connexions', icon: 'cilUser', color: 'info', value: this.formatInt(s.connexions) },
      { key: 'sensibles', label: 'Actions sensibles', icon: 'cilNotes', color: 'warning', value: this.formatInt(this.actionsSensiblesTotal()) },
      { key: 'utilisateurs', label: 'Utilisateurs distincts', icon: 'cilStar', color: 'primary', value: this.formatInt(s.parUtilisateur.length) },
    ];
  });

  parJourData: any = null;
  parJourOptions: any = null;
  parActionData: any = null;
  parActionOptions: any = null;

  fetchStatistics(): void {
    this.statsLoading.set(true);
    this.statsError.set(null);
    this.auditTrailService.getStatistics().subscribe({
      next: (stats) => {
        this.statistics.set(stats);
        this.buildParJourChart(stats);
        this.buildParActionChart(stats);
        this.statsLoading.set(false);
      },
      error: () => {
        this.statsError.set('Impossible de charger les statistiques.');
        this.statsLoading.set(false);
      },
    });
  }

  private buildParJourChart(stats: HistoriqueModificationStatistiquesResponse): void {
    const colorBorderTranslucent = getStyle('--cui-border-color-translucent');
    const colorBody = getStyle('--cui-body-color');
    const brandPrimary = getStyle('--cui-primary') ?? '#5856d6';
    const brandPrimaryRgb = getStyle('--cui-primary-rgb') ?? '88, 86, 214';

    this.parJourData = {
      labels: stats.parJour.map((r) => this.formatShortDate(r.jour)),
      datasets: [
        {
          label: 'Actions par jour',
          data: stats.parJour.map((r) => r.nombre),
          borderColor: brandPrimary,
          backgroundColor: `rgba(${brandPrimaryRgb}, .14)`,
          fill: true,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
      ],
    };
    this.parJourOptions = {
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { color: colorBorderTranslucent, drawOnChartArea: false }, ticks: { color: colorBody, maxRotation: 0 } },
        y: { border: { color: colorBorderTranslucent }, grid: { color: colorBorderTranslucent }, min: 0, beginAtZero: true, ticks: { color: colorBody, precision: 0 } },
      },
    };
  }

  private buildParActionChart(stats: HistoriqueModificationStatistiquesResponse): void {
    const colorBody = getStyle('--cui-body-color');
    const colorBorderTranslucent = getStyle('--cui-border-color-translucent');

    this.parActionData = {
      labels: stats.parAction.map((a) => this.actionLabel[a.libelle as AuditAction] ?? a.libelle),
      datasets: [
        {
          label: 'Actions',
          data: stats.parAction.map((a) => a.nombre),
          backgroundColor: getStyle('--cui-primary') ?? '#5856d6',
          borderRadius: 4,
        },
      ],
    };
    this.parActionOptions = {
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { display: false }, ticks: { color: colorBody } },
        y: { border: { color: colorBorderTranslucent }, grid: { color: colorBorderTranslucent }, min: 0, beginAtZero: true, ticks: { color: colorBody, precision: 0 } },
      },
    };
  }

  formatInt(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(value);
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }

  formatShortDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
  }

  constructor() {
    if (this.view() === 'dashboard') this.fetchStatistics();
    else if (this.view() === 'sensibles') {
      this.sensiblesFilterForm.patchValue({ action: this.sensitiveActionOrder[0] });
      this.fetchSensibles();
    } else this.fetchLogs();
  }
}
