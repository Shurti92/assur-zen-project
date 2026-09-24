import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, ColComponent, ProgressComponent, RowComponent, SpinnerComponent } from '@coreui/angular';

import { DashboardService } from '../../../core/dashboard/dashboard.service';
import { DashboardBackOfficeKpis, DashboardPeriod, PartenaireObjectif } from '../../../core/dashboard/models/dashboard-kpis.model';
import { TypePartenaire } from '../../../core/partenaire/models/partenaire.model';

interface KpiCard {
  key: string;
  label: string;
  icon: string;
  color: string;
  value: string;
  link?: string;
  queryParams?: Record<string, string>;
}

const TYPE_PARTENAIRE_LABEL: Record<TypePartenaire, string> = {
  DIRECT: 'Direct',
  AGENT: 'Agent',
  APPORTEUR: 'Apporteur',
};

const TYPE_PARTENAIRE_VARIANT: Record<TypePartenaire, string> = {
  DIRECT: 'info',
  AGENT: 'neutral',
  APPORTEUR: 'warning',
};

@Component({
  selector: 'app-insurance-widgets',
  templateUrl: './insurance-widgets.component.html',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, RowComponent, ColComponent, ProgressComponent, RouterLink],
})
export class InsuranceWidgetsComponent {
  private readonly dashboardService = inject(DashboardService);

  readonly period = signal<DashboardPeriod>('month');
  readonly kpis = signal<DashboardBackOfficeKpis | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly volumeKpis = computed<KpiCard[]>(() => {
    const v = this.kpis()?.volumes;
    if (!v) return [];
    return [
      { key: 'total', label: 'Contrats totaux', icon: 'cilDescription', color: 'primary', value: this.formatInt(v.totalContracts) },
      {
        key: 'signed',
        label: 'Contrats signés',
        icon: 'cilCheckCircle',
        color: 'success',
        value: this.formatInt(v.signedContracts),
        link: '/contrats/liste-des-contrats',
      },
      {
        key: 'pending',
        label: 'Contrats en attente',
        icon: 'cilWarning',
        color: 'warning',
        value: this.formatInt(v.pendingContracts),
        link: '/contrats/cycle-de-vie-contrat',
        queryParams: { status: 'pending' },
      },
      { key: 'conversion', label: 'Taux de conversion', icon: 'cilSpeedometer', color: 'info', value: this.formatPercentFraction(v.conversionRate) },
    ];
  });

  readonly financeKpis = computed<KpiCard[]>(() => {
    const f = this.kpis()?.finance;
    if (!f) return [];
    return [
      { key: 'production', label: 'Production', icon: 'cilChartPie', color: 'primary', value: this.formatAmount(f.production) },
      { key: 'total-commission', label: 'Commission totale', icon: 'cilCalculator', color: 'info', value: this.formatAmount(f.totalCommission) },
      { key: 'paid-commission', label: 'Commission payée', icon: 'cilCreditCard', color: 'success', value: this.formatAmount(f.paidCommission) },
      { key: 'pending-commission', label: 'Commission en attente', icon: 'cilWarning', color: 'warning', value: this.formatAmount(f.pendingCommission), link: '/commissions' },
      { key: 'partner-share', label: 'Part partenaire', icon: 'cilPeople', color: 'secondary', value: this.formatAmount(f.partnerShare) },
      { key: 'prime-non-reversee-count', label: 'Contrats non reversés', icon: 'cilWarning', color: 'warning', value: this.formatInt(f.primeNonReverseeCount), link: '/contrats/prime-non-reversee' },
      { key: 'prime-non-reversee-montant', label: 'Montant non reversé', icon: 'cilCreditCard', color: 'danger', value: this.formatAmount(f.primeNonReverseeMontant), link: '/contrats/prime-non-reversee' },
      { key: 'avenants-count', label: 'Avenants', icon: 'cilPencil', color: 'info', value: this.formatInt(f.avenantsCount) },
    ];
  });

  /** Derived business ratios, not returned as-is by the backend — computed
   * client-side from the finance/volumes/cycleDeVie payloads already fetched
   * above, so they need no extra endpoint. */
  readonly performanceKpis = computed<KpiCard[]>(() => {
    const k = this.kpis();
    if (!k) return [];
    const { finance, volumes, cycleDeVie } = k;
    const primeMoyenne = volumes.signedContracts > 0 ? finance.production / volumes.signedContracts : 0;
    const tauxCommission = finance.production > 0 ? (finance.totalCommission / finance.production) * 100 : 0;
    const margeNette = finance.totalCommission - finance.partnerShare;
    const renouvelables = cycleDeVie.realises + cycleDeVie.nonRenouveles;
    const tauxRenouvellement = renouvelables > 0 ? (cycleDeVie.realises / renouvelables) * 100 : 0;
    return [
      { key: 'prime-moyenne', label: 'Prime moyenne / contrat signé', icon: 'cilChartPie', color: 'primary', value: this.formatAmount(primeMoyenne) },
      { key: 'taux-commission', label: 'Taux de commission moyen', icon: 'cilSpeedometer', color: 'info', value: `${tauxCommission.toFixed(1)} %` },
      { key: 'marge-nette', label: 'Marge nette (après part partenaire)', icon: 'cilCalculator', color: 'success', value: this.formatAmount(margeNette) },
      { key: 'taux-renouvellement', label: 'Taux de renouvellement', icon: 'cilCheckCircle', color: renouvelables > 0 && tauxRenouvellement >= 50 ? 'success' : 'warning', value: `${tauxRenouvellement.toFixed(1)} %` },
    ];
  });

  readonly lifecycleKpis = computed<KpiCard[]>(() => {
    const c = this.kpis()?.cycleDeVie;
    if (!c) return [];
    return [
      {
        key: 'realises',
        label: 'Contrats réalisés',
        icon: 'cilCheckCircle',
        color: 'success',
        value: this.formatInt(c.realises),
        link: '/contrats/cycle-de-vie-contrat',
      },
      {
        key: 'echeance-proche',
        label: 'Échéance dans 30 j',
        icon: 'cilWarning',
        color: 'warning',
        value: this.formatInt(c.echeanceProche),
        link: '/contrats/a-echeance-proche',
      },
      {
        key: 'non-renouveles',
        label: 'Non renouvelés',
        icon: 'cilXCircle',
        color: 'danger',
        value: this.formatInt(c.nonRenouveles),
        link: '/contrats/non-renouveles',
      },
    ];
  });

  readonly produitBreakdown = computed(() => this.kpis()?.repartitionParProduit ?? []);

  readonly hasObjective = computed(() => (this.kpis()?.objectifGlobal.montant ?? 0) > 0);
  readonly objectivePercent = computed(() => Math.round((this.kpis()?.objectifGlobal.progression ?? 0) * 1000) / 10);
  readonly objectiveColor = computed(() => {
    const pct = this.objectivePercent();
    if (pct >= 100) return 'success';
    if (pct >= 50) return 'warning';
    return 'danger';
  });

  readonly objectifApporteurRows = computed<PartenaireObjectif[]>(() => this.kpis()?.objectifParApporteur.partenaires ?? []);
  readonly totalObjectifApporteurs = computed(() => this.kpis()?.objectifParApporteur.totalObjectif ?? 0);
  readonly totalRealiseApporteurs = computed(() => this.kpis()?.objectifParApporteur.totalRealise ?? 0);
  readonly tauxGlobalApporteurs = computed<number | null>(() => this.kpis()?.objectifParApporteur.tauxGlobal ?? null);

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.dashboardService.getKpis(this.period()).subscribe({
      next: (kpis) => {
        this.kpis.set(kpis);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les indicateurs du tableau de bord.');
        this.loading.set(false);
      },
    });
  }

  setPeriod(period: DashboardPeriod): void {
    if (this.period() === period) return;
    this.period.set(period);
    this.fetch();
  }

  tauxVariant(taux: number | null): string {
    if (taux === null) return 'neutral';
    if (taux >= 100) return 'success';
    if (taux >= 50) return 'warning';
    return 'danger';
  }

  typeLabel(type: TypePartenaire): string {
    return TYPE_PARTENAIRE_LABEL[type];
  }

  typeVariant(type: TypePartenaire): string {
    return TYPE_PARTENAIRE_VARIANT[type];
  }

  formatInt(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(value);
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  private formatPercentFraction(fraction: number): string {
    return `${(fraction * 100).toFixed(1)} %`;
  }
}
