import { AfterContentInit, ChangeDetectorRef, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { getStyle } from '@coreui/utils';
import { ChartjsComponent } from '@coreui/angular-chartjs';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, ColComponent, RowComponent, SpinnerComponent } from '@coreui/angular';

import { LedgerService } from '../../../core/ledger/ledger.service';
import {
  BalanceCompteResponse,
  CompteComptableResponse,
  EcritureComptableResponse,
  TypeCompte,
  TypeOperation,
} from '../../../core/ledger/models/ledger.model';
import { ExerciceComptableService } from '../../../core/configuration/exercice-comptable.service';
import { ExerciceComptableResponse } from '../../../core/configuration/models/exercice-comptable.model';

interface KpiCard {
  key: string;
  label: string;
  icon: string;
  color: string;
  value: string;
  sub: string;
  subVariant: 'ok' | 'warning' | 'neutral';
}

const TYPE_OPERATION_LABEL: Record<TypeOperation, string> = {
  SOUSCRIPTION: 'Souscription',
  PAIEMENT: 'Paiement',
  COMMISSION: 'Commission',
  ANNULATION: 'Annulation',
};

const TYPE_OPERATION_COLOR: Record<TypeOperation, string> = {
  SOUSCRIPTION: '--cui-primary',
  PAIEMENT: '--cui-success',
  COMMISSION: '--cui-info',
  ANNULATION: '--cui-danger',
};

const TYPE_COMPTE_LABEL: Record<TypeCompte, string> = {
  ACTIF: 'Actif',
  PASSIF: 'Passif',
  CHARGE: 'Charge',
  PRODUIT: 'Produit',
};

const TYPE_COMPTE_COLOR: Record<TypeCompte, string> = {
  ACTIF: '--cui-primary',
  PASSIF: '--cui-warning',
  CHARGE: '--cui-danger',
  PRODUIT: '--cui-success',
};

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-ledger-dashboard',
  templateUrl: './ledger-dashboard.component.html',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, RowComponent, ColComponent, ChartjsComponent],
})
export class LedgerDashboardComponent implements AfterContentInit {
  private readonly ledgerService = inject(LedgerService);
  private readonly exerciceService = inject(ExerciceComptableService);
  private readonly router = inject(Router);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly ecritures = signal<EcritureComptableResponse[]>([]);
  readonly ecrituresTotalCount = signal(0);
  readonly comptes = signal<CompteComptableResponse[]>([]);
  readonly balanceRows = signal<BalanceCompteResponse[]>([]);
  readonly exercices = signal<ExerciceComptableResponse[]>([]);

  // KPIs -------------------------------------------------------------------

  readonly debitDuJour = computed(() => {
    const jour = today();
    let total = 0;
    for (const ecriture of this.ecritures()) {
      if (ecriture.dateEcriture.slice(0, 10) !== jour) continue;
      for (const ligne of ecriture.lignes) total += ligne.montantDebit;
    }
    return total;
  });

  readonly creditDuJour = computed(() => {
    const jour = today();
    let total = 0;
    for (const ecriture of this.ecritures()) {
      if (ecriture.dateEcriture.slice(0, 10) !== jour) continue;
      for (const ligne of ecriture.lignes) total += ligne.montantCredit;
    }
    return total;
  });

  readonly totalDebitGlobal = computed(() => this.balanceRows().reduce((sum, row) => sum + row.totalDebit, 0));
  readonly totalCreditGlobal = computed(() => this.balanceRows().reduce((sum, row) => sum + row.totalCredit, 0));
  readonly ecartBalance = computed(() => this.totalDebitGlobal() - this.totalCreditGlobal());

  readonly kpis = computed<KpiCard[]>(() => [
    {
      key: 'ecritures',
      label: "Nombre d'écritures",
      icon: 'cilDescription',
      color: 'primary',
      value: this.formatInt(this.ecrituresTotalCount()),
      sub: `${this.comptes().length ? this.formatInt(this.ecritures().filter((e) => e.contrepassee).length) : 0} contre-passée(s)`,
      subVariant: 'neutral',
    },
    {
      key: 'comptes',
      label: 'Nombre de comptes',
      icon: 'cilCalculator',
      color: 'info',
      value: this.formatInt(this.comptes().length),
      sub: `${this.formatInt(this.comptes().filter((c) => c.active).length)} actif(s)`,
      subVariant: 'neutral',
    },
    {
      key: 'debit-jour',
      label: 'Débit du jour',
      icon: 'cilArrowBottom',
      color: 'warning',
      value: this.formatAmount(this.debitDuJour()),
      sub: "Aujourd'hui",
      subVariant: 'neutral',
    },
    {
      key: 'credit-jour',
      label: 'Crédit du jour',
      icon: 'cilArrowTop',
      color: 'success',
      value: this.formatAmount(this.creditDuJour()),
      sub: "Aujourd'hui",
      subVariant: 'neutral',
    },
    {
      key: 'balance',
      label: 'Balance',
      icon: 'cilSpeedometer',
      color: this.ecartBalance() === 0 ? 'primary' : 'danger',
      value: this.formatAmount(this.totalDebitGlobal()),
      sub: this.ecartBalance() === 0 ? 'Équilibrée' : `Écart ${this.formatAmount(this.ecartBalance())}`,
      subVariant: this.ecartBalance() === 0 ? 'ok' : 'warning',
    },
  ]);

  // Alerts -------------------------------------------------------------------

  readonly ecrituresContrepassees = computed(() =>
    this.ecritures()
      .filter((e) => e.contrepassee)
      .sort((a, b) => b.dateEcriture.localeCompare(a.dateEcriture))
      .slice(0, 8),
  );

  readonly periodesExpirees = computed(() => {
    const jour = today();
    return this.exercices()
      .filter((ex) => ex.active && ex.dateFin.slice(0, 10) < jour)
      .sort((a, b) => a.dateFin.localeCompare(b.dateFin));
  });

  // Charts -------------------------------------------------------------------

  evolutionData: any = null;
  evolutionOptions: any = null;
  typeOperationData: any = null;
  typeOperationOptions: any = null;
  typeCompteData: any = null;
  typeCompteOptions: any = null;

  readonly typeOperationLegend = computed(() => this.buildTypeOperationLegend());
  readonly typeCompteLegend = computed(() => this.buildTypeCompteLegend());

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
    this.pendingRequests = 3;

    this.ledgerService.listAllEcritures().subscribe({
      next: (page) => {
        this.ecritures.set(page.content);
        this.ecrituresTotalCount.set(page.totalElements);
        this.buildCharts();
        this.checkDone();
      },
      error: () => {
        this.error.set('Impossible de charger les écritures comptables.');
        this.checkDone();
      },
    });

    this.ledgerService.listComptes().subscribe({
      next: (comptes) => {
        this.comptes.set(comptes);
        this.buildCharts();
        this.checkDone();
      },
      error: () => {
        this.error.set('Impossible de charger le plan comptable.');
        this.checkDone();
      },
    });

    this.ledgerService.balance().subscribe({
      next: (rows) => {
        this.balanceRows.set(rows);
        this.buildCharts();
        this.checkDone();
      },
      error: () => {
        this.error.set('Impossible de charger la balance générale.');
        this.checkDone();
      },
    });

    this.exerciceService.list().subscribe({
      next: (exercices) => this.exercices.set(exercices),
      error: () => {
        // Silent: the "périodes expirées" alert just stays empty if this fails.
      },
    });
  }

  private checkDone(): void {
    this.pendingRequests -= 1;
    if (this.pendingRequests <= 0) {
      this.loading.set(false);
    }
  }

  private buildCharts(): void {
    this.buildEvolutionChart();
    this.buildTypeOperationChart();
    this.buildTypeCompteChart();
  }

  private buildEvolutionChart(): void {
    const byDay = new Map<string, { debit: number; credit: number }>();
    for (const ecriture of this.ecritures()) {
      const day = ecriture.dateEcriture.slice(0, 10);
      const entry = byDay.get(day) ?? { debit: 0, credit: 0 };
      for (const ligne of ecriture.lignes) {
        entry.debit += ligne.montantDebit;
        entry.credit += ligne.montantCredit;
      }
      byDay.set(day, entry);
    }
    const days = [...byDay.keys()].sort().slice(-30);

    const colorBorderTranslucent = getStyle('--cui-border-color-translucent');
    const colorBody = getStyle('--cui-body-color');
    const brandPrimary = getStyle('--cui-primary') ?? '#5856d6';
    const brandPrimaryRgb = getStyle('--cui-primary-rgb') ?? '88, 86, 214';
    const brandSuccess = getStyle('--cui-success') ?? '#2eb85c';

    this.evolutionData = {
      labels: days.map((d) => this.formatShortDate(d)),
      datasets: [
        {
          label: 'Débit',
          data: days.map((d) => byDay.get(d)!.debit),
          borderColor: brandPrimary,
          backgroundColor: `rgba(${brandPrimaryRgb}, .14)`,
          fill: true,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
        {
          label: 'Crédit',
          data: days.map((d) => byDay.get(d)!.credit),
          borderColor: brandSuccess,
          backgroundColor: 'transparent',
          fill: false,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
      ],
    };
    this.evolutionOptions = {
      maintainAspectRatio: false,
      plugins: { legend: { display: true, position: 'bottom', labels: { color: colorBody, boxWidth: 10, usePointStyle: true } } },
      scales: {
        x: { grid: { color: colorBorderTranslucent, drawOnChartArea: false }, ticks: { color: colorBody, maxRotation: 0, autoSkip: true, maxTicksLimit: 10 } },
        y: { border: { color: colorBorderTranslucent }, grid: { color: colorBorderTranslucent }, min: 0, beginAtZero: true, ticks: { color: colorBody } },
      },
    };
  }

  private buildTypeOperationChart(): void {
    const totals = new Map<TypeOperation, number>();
    for (const ecriture of this.ecritures()) {
      const debit = ecriture.lignes.reduce((sum, l) => sum + l.montantDebit, 0);
      totals.set(ecriture.typeOperation, (totals.get(ecriture.typeOperation) ?? 0) + debit);
    }
    const types = [...totals.keys()];
    const colorBody = getStyle('--cui-body-color');

    this.typeOperationData = {
      labels: types.map((t) => TYPE_OPERATION_LABEL[t]),
      datasets: [
        {
          data: types.map((t) => totals.get(t) ?? 0),
          backgroundColor: types.map((t) => getStyle(TYPE_OPERATION_COLOR[t])),
          borderWidth: 0,
        },
      ],
    };
    this.typeOperationOptions = {
      maintainAspectRatio: false,
      cutout: '68%',
      plugins: { legend: { display: false }, tooltip: { titleColor: colorBody } },
    };
  }

  private buildTypeCompteChart(): void {
    const totals = new Map<TypeCompte, number>();
    for (const row of this.balanceRows()) {
      totals.set(row.type, (totals.get(row.type) ?? 0) + Math.abs(row.solde));
    }
    const types = [...totals.keys()];
    const colorBody = getStyle('--cui-body-color');

    this.typeCompteData = {
      labels: types.map((t) => TYPE_COMPTE_LABEL[t]),
      datasets: [
        {
          data: types.map((t) => totals.get(t) ?? 0),
          backgroundColor: types.map((t) => getStyle(TYPE_COMPTE_COLOR[t])),
          borderWidth: 0,
        },
      ],
    };
    this.typeCompteOptions = {
      maintainAspectRatio: false,
      cutout: '68%',
      plugins: { legend: { display: false }, tooltip: { titleColor: colorBody } },
    };
  }

  private buildTypeOperationLegend(): { label: string; value: string; color: string }[] {
    const totals = new Map<TypeOperation, number>();
    for (const ecriture of this.ecritures()) {
      const debit = ecriture.lignes.reduce((sum, l) => sum + l.montantDebit, 0);
      totals.set(ecriture.typeOperation, (totals.get(ecriture.typeOperation) ?? 0) + debit);
    }
    return [...totals.entries()].map(([type, amount]) => ({
      label: TYPE_OPERATION_LABEL[type],
      value: this.formatAmount(amount),
      color: getStyle(TYPE_OPERATION_COLOR[type]) ?? '#5856d6',
    }));
  }

  private buildTypeCompteLegend(): { label: string; value: string; color: string }[] {
    const totals = new Map<TypeCompte, number>();
    for (const row of this.balanceRows()) {
      totals.set(row.type, (totals.get(row.type) ?? 0) + Math.abs(row.solde));
    }
    return [...totals.entries()].map(([type, amount]) => ({
      label: TYPE_COMPTE_LABEL[type],
      value: this.formatAmount(amount),
      color: getStyle(TYPE_COMPTE_COLOR[type]) ?? '#5856d6',
    }));
  }

  goToExercices(): void {
    this.router.navigate(['/configuration/exercice-comptable']);
  }

  formatInt(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(value);
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatShortDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }
}
