import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  ColComponent,
  FormControlDirective,
  FormDirective,
  RowComponent,
  SpinnerComponent,
} from '@coreui/angular';

import { LoginHistoryService } from '../../core/login-history/login-history.service';
import {
  LoginDeviceType,
  LoginHistoryFilter,
  LoginHistoryResponse,
  LoginHistoryStatsResponse,
} from '../../core/login-history/models/login-history.model';

interface KpiCard {
  key: string;
  label: string;
  icon: string;
  color: string;
  value: string;
}

const DEVICE_LABEL: Record<LoginDeviceType, string> = {
  DESKTOP: 'Ordinateur',
  MOBILE: 'Mobile',
  TABLET: 'Tablette',
  BOT: 'Robot',
  INCONNU: 'Inconnu',
};

const DEVICE_VARIANT: Record<LoginDeviceType, string> = {
  DESKTOP: 'info',
  MOBILE: 'success',
  TABLET: 'success',
  BOT: 'warning',
  INCONNU: 'neutral',
};

@Component({
  selector: 'app-historique-connexions',
  templateUrl: './historique-connexions.component.html',
  styleUrl: './historique-connexions.component.scss',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    RowComponent,
    ColComponent,
    ReactiveFormsModule,
    FormDirective,
    FormControlDirective,
  ],
})
export class HistoriqueConnexionsComponent {
  private readonly loginHistoryService = inject(LoginHistoryService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly items = signal<LoginHistoryResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pageIndex = signal(0);
  readonly totalPages = signal(1);
  readonly totalElements = signal(0);
  readonly pageSize = 20;

  readonly statistics = signal<LoginHistoryStatsResponse | null>(null);
  readonly statsLoading = signal(false);
  readonly statsError = signal<string | null>(null);

  readonly filterForm = this.fb.nonNullable.group({
    dateDebut: '',
    dateFin: '',
    userId: '',
    partenaireId: '',
  });

  readonly kpis = computed<KpiCard[]>(() => {
    const s = this.statistics();
    if (!s) return [];
    return [
      { key: 'jour', label: "Connexions aujourd'hui", icon: 'clock', color: 'primary', value: this.formatInt(s.connexionsAujourdHui) },
      { key: 'semaine', label: 'Connexions cette semaine', icon: 'cilCalendar', color: 'info', value: this.formatInt(s.connexionsCetteSemaine) },
      { key: 'mois', label: 'Connexions ce mois', icon: 'cilChartPie', color: 'success', value: this.formatInt(s.connexionsCeMois) },
    ];
  });

  private buildFilters(raw: ReturnType<typeof this.filterForm.getRawValue>): LoginHistoryFilter {
    return {
      dateDebut: raw.dateDebut || undefined,
      dateFin: raw.dateFin || undefined,
      userId: raw.userId ? Number(raw.userId) : undefined,
      partenaireId: raw.partenaireId ? Number(raw.partenaireId) : undefined,
    };
  }

  fetchLogs(): void {
    this.loading.set(true);
    this.error.set(null);
    this.loginHistoryService.search(this.pageIndex(), this.pageSize, this.buildFilters(this.filterForm.getRawValue())).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.totalPages.set(Math.max(1, page.totalPages));
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("Impossible de charger l'historique des connexions.");
        this.loading.set(false);
      },
    });
  }

  fetchStatistics(): void {
    this.statsLoading.set(true);
    this.statsError.set(null);
    this.loginHistoryService.getStatistics(this.buildFilters(this.filterForm.getRawValue())).subscribe({
      next: (stats) => {
        this.statistics.set(stats);
        this.statsLoading.set(false);
      },
      error: () => {
        this.statsError.set('Impossible de charger les statistiques.');
        this.statsLoading.set(false);
      },
    });
  }

  refresh(): void {
    this.fetchLogs();
    this.fetchStatistics();
  }

  applyFilters(): void {
    this.pageIndex.set(0);
    this.fetchLogs();
    this.fetchStatistics();
  }

  resetFilters(): void {
    this.filterForm.reset({ dateDebut: '', dateFin: '', userId: '', partenaireId: '' });
    this.pageIndex.set(0);
    this.fetchLogs();
    this.fetchStatistics();
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
    this.fetchLogs();
  }

  deviceLabel(deviceType: LoginDeviceType | null): string {
    return deviceType ? DEVICE_LABEL[deviceType] : '—';
  }

  deviceVariant(deviceType: LoginDeviceType | null): string {
    return deviceType ? DEVICE_VARIANT[deviceType] : 'neutral';
  }

  localisation(item: LoginHistoryResponse): string {
    const parts = [item.city, item.country].filter((v): v is string => !!v);
    return parts.length > 0 ? parts.join(', ') : '—';
  }

  formatInt(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(value);
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }

  constructor() {
    this.applyDeepLink();
    this.fetchLogs();
    this.fetchStatistics();
  }

  /** Supports "Historique des connexions" links from the Partenaire d'affaire page:
   * /administration/historique-connexions?partenaireId=X pré-filtre sur ce partenaire. */
  private applyDeepLink(): void {
    const raw = this.route.snapshot.queryParamMap.get('partenaireId');
    if (!raw) return;
    this.filterForm.patchValue({ partenaireId: raw });
    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }
}
