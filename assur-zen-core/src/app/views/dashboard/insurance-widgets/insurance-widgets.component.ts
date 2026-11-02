import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

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
  styleUrl: './insurance-widgets.component.scss',
  imports: [ButtonDirective, SpinnerComponent, RouterLink, DecimalPipe],
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

  // ================= Refonte de l'onglet Contrats (maquette validée le 2026-10-02) =================

  readonly lastUpdated = signal<Date | null>(null);

  /** Objectif de production de l'année + repère de rythme (part de l'année écoulée). */
  readonly goal = computed(() => {
    const o = this.kpis()?.objectifGlobal;
    if (!o) return null;
    const now = new Date();
    const debut = new Date(now.getFullYear(), 0, 1).getTime();
    const fin = new Date(now.getFullYear() + 1, 0, 1).getTime();
    const rythme = ((now.getTime() - debut) / (fin - debut)) * 100;
    const pct = Math.round(o.progression * 1000) / 10;
    const realise = Math.round(o.montant * o.progression);
    return {
      annee: now.getFullYear(),
      montant: o.montant,
      realise,
      reste: Math.max(0, o.montant - realise),
      pct,
      rythme: Math.round(rythme),
      enAvance: pct >= rythme,
      atteint: pct >= 100,
      ringDash: `${Math.min(100, pct) * 1.005} 100.5`,
    };
  });

  /** Indicateurs dérivés réutilisés par les cartes (mêmes formules que performanceKpis). */
  readonly derived = computed(() => {
    const k = this.kpis();
    if (!k) return null;
    const { finance, volumes, cycleDeVie } = k;
    const renouvelables = cycleDeVie.realises + cycleDeVie.nonRenouveles;
    const commission = finance.totalCommission;
    return {
      primeMoyenne: volumes.signedContracts > 0 ? Math.round(finance.production / volumes.signedContracts) : 0,
      tauxCommission: finance.production > 0 ? (finance.totalCommission / finance.production) * 100 : 0,
      margeNette: finance.totalCommission - finance.partnerShare,
      tauxRenouvellement: renouvelables > 0 ? (cycleDeVie.realises / renouvelables) * 100 : null,
      conversionPct: Math.round(volumes.conversionRate * 100),
      payeesPct: commission > 0 ? (finance.paidCommission / commission) * 100 : 0,
      attentePct: commission > 0 ? (finance.pendingCommission / commission) * 100 : 0,
      reverses: Math.max(0, volumes.signedContracts - finance.primeNonReverseeCount),
      reversesPct: volumes.signedContracts > 0 ? (Math.max(0, volumes.signedContracts - finance.primeNonReverseeCount) / volumes.signedContracts) * 100 : 0,
      signesPct: volumes.totalContracts > 0 ? (volumes.signedContracts / volumes.totalContracts) * 100 : 0,
      attenteContratsPct: volumes.totalContracts > 0 ? (volumes.pendingContracts / volumes.totalContracts) * 100 : 0,
    };
  });

  /** Arc SVG de la jauge (viewBox 100×56) pour un taux 0-100. */
  gaugeArc(rate: number | null): string {
    const r = Math.max(0, Math.min(100, rate ?? 0)) / 100;
    const angle = Math.PI * (1 - r);
    return `M8 52 A42 42 0 0 1 ${(50 + 42 * Math.cos(angle)).toFixed(2)} ${(52 - 42 * Math.sin(angle)).toFixed(2)}`;
  }

  /** Répartition par produit : 4 premiers + « Autres (n produits) ». */
  readonly produitBars = computed(() => {
    const items = [...this.produitBreakdown()].sort((a, b) => b.count - a.count);
    const total = items.reduce((s, i) => s + i.count, 0);
    const top = items.slice(0, 4).map((i) => ({ label: i.productName, count: i.count, autres: false }));
    const rest = items.slice(4);
    if (rest.length) {
      top.push({ label: `Autres (${rest.length} produit${rest.length > 1 ? 's' : ''})`, count: rest.reduce((s, i) => s + i.count, 0), autres: true });
    }
    const max = Math.max(1, ...top.map((t) => t.count));
    return {
      total,
      rows: top.map((t) => ({ ...t, pct: Math.max(2, (t.count / max) * 100), part: total ? Math.round((t.count / total) * 100) : 0 })),
    };
  });

  /** Apporteurs triés par taux d'atteinte (5 affichés, « Voir les N » déplie la liste). */
  readonly showAllApporteurs = signal(false);
  readonly apporteursSorted = computed(() => [...this.objectifApporteurRows()].sort((a, b) => b.taux - a.taux));
  readonly apporteursShown = computed(() => (this.showAllApporteurs() ? this.apporteursSorted() : this.apporteursSorted().slice(0, 5)));

  initials(nom: string): string {
    return nom
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase() ?? '')
      .join('');
  }

  tauxColor(taux: number): string {
    if (taux >= 100) return '#1f9d6b';
    if (taux >= 50) return '#b7791f';
    return '#d64545';
  }

  formatPercentFr(value: number, digits = 1): string {
    return `${new Intl.NumberFormat('fr-FR', { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(value)} %`;
  }

  /** 369 000 → « 369 k », 2 100 000 → « 2,1 M ». */
  formatCompact(value: number): string {
    const abs = Math.abs(value);
    if (abs >= 1_000_000) return `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(value / 1_000_000)} M`;
    if (abs >= 10_000) return `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(value / 1000)} k`;
    return this.formatInt(value);
  }

  formatTime(date: Date | null): string {
    return date ? date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) : '';
  }

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.dashboardService.getKpis(this.period()).subscribe({
      next: (kpis) => {
        this.kpis.set(kpis);
        this.lastUpdated.set(new Date());
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
