import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { getStyle } from '@coreui/utils';
import { ChartjsComponent } from '@coreui/angular-chartjs';

import { ButtonDirective } from '@coreui/angular';

import { DashboardService } from '../../core/dashboard/dashboard.service';
import { AuthService } from '../../core/auth/auth.service';
import {
  AssureKpis,
  DashboardAssureResponse,
  DashboardGlobalResponse,
  DashboardSortieFondsResponse,
  DashboardVignetteResponse,
  KpiValue,
  SortieFondsKpis,
  Tendance,
  TypeClientCode,
  VignetteKpis,
  VignetteMotoCategorieAgeCode,
  VignetteMotoCategorieCylindreeCode,
  VignetteMotoStatutCode,
  VignetteMotoTypeDemandeCode,
} from '../../core/dashboard/models/kpi-dashboard.model';

type View = 'global' | 'vignettes' | 'sortiesFonds' | 'assures';

interface KpiCard {
  key: string;
  label: string;
  icon: string;
  color: string;
  value: string;
  variation: string;
  tendanceIcon: string;
}

interface LegendRow {
  label: string;
  value: string;
  color: string;
}

/** Palette cyclique pour les segments de répartition — se répète au-delà de 7 catégories
 * (ex. les 10 statuts vignette), acceptable car la légende reste lisible par libellé+valeur. */
const CHART_PALETTE = ['--cui-primary', '--cui-info', '--cui-success', '--cui-warning', '--cui-danger', '--cui-secondary', '--cui-dark'];

const TYPE_DEMANDE_LABEL: Record<VignetteMotoTypeDemandeCode, string> = {
  NOUVELLE_DEMANDE: 'Nouvelle demande',
  RENOUVELLEMENT: 'Renouvellement',
};

const CATEGORIE_CYLINDREE_LABEL: Record<VignetteMotoCategorieCylindreeCode, string> = {
  MOINS_125_CM3: 'Moins de 125 cm³',
  '125_CM3_ET_PLUS': '125 cm³ et plus',
};

const CATEGORIE_AGE_LABEL: Record<VignetteMotoCategorieAgeCode, string> = {
  '1_4_ANS': '1 à 4 ans',
  '5_10_ANS': '5 à 10 ans',
  '11_ANS_ET_PLUS': '11 ans et plus',
};

const VIGNETTE_STATUT_LABEL: Record<VignetteMotoStatutCode, string> = {
  BROUILLON: 'Brouillon',
  EN_ATTENTE_PAIEMENT: 'En attente de paiement',
  PAYEE: 'Payée',
  EN_TRAITEMENT: 'En traitement',
  EN_EDITION: 'En édition',
  EDITEE: 'Éditée',
  EN_LIVRAISON: 'En livraison',
  LIVREE: 'Livrée',
  ANNULEE: 'Annulée',
  REJETEE: 'Rejetée',
};

const TYPE_CLIENT_LABEL: Record<TypeClientCode, string> = {
  PARTICULIER: 'Particulier',
  ENTREPRISE: 'Entreprise',
};

/** Statuts de sortie de fonds : mêmes valeurs que FundOutflowStatus (voir
 * fund-outflows.component.ts) — copie locale, pas d'utilitaire de libellés partagé dans l'app. */
const SORTIE_FONDS_STATUT_LABEL: Record<string, string> = {
  DRAFT: 'Brouillon',
  SUBMITTED: 'Soumise',
  UNDER_REVIEW: "En cours d'examen",
  APPROVED: 'Approuvée',
  REJECTED: 'Rejetée',
  CANCELLED: 'Annulée',
  AUTHORIZED: 'Autorisée',
  PROCESSING: 'Décaissement en cours',
  DISBURSED: 'Décaissée',
  PARTIALLY_REPAID: 'Partiellement remboursée',
  REPAID: 'Remboursée',
  CLOSED: 'Clôturée',
};

type PeriodPreset = 'today' | '7d' | 'month' | 'lastMonth' | 'year' | 'custom';

interface HBar {
  label: string;
  value: string;
  /** Largeur de la barre en % de la plus grande valeur affichée. */
  pct: number;
}

function isoLocal(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Bornes (dates locales) des raccourcis de période du tableau de bord. */
function presetRange(preset: Exclude<PeriodPreset, 'custom'>): { debut: string; fin: string } {
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth();
  switch (preset) {
    case 'today':
      return { debut: isoLocal(now), fin: isoLocal(now) };
    case '7d':
      return { debut: isoLocal(new Date(y, m, now.getDate() - 6)), fin: isoLocal(now) };
    case 'lastMonth':
      return { debut: isoLocal(new Date(y, m - 1, 1)), fin: isoLocal(new Date(y, m, 0)) };
    case 'year':
      return { debut: isoLocal(new Date(y, 0, 1)), fin: isoLocal(now) };
    default:
      return { debut: isoLocal(new Date(y, m, 1)), fin: isoLocal(now) };
  }
}

function firstDayOfMonth(): string {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Nouveau module (voir docs/api-contracts/kpi-dashboard.md) — un seul composant à onglets
 * (Global/Vignettes/Sorties de fonds/Assurés), même pattern que GedComponent/FundOutflowsComponent.
 * Réservé ADMIN : masquage d'affichage uniquement, le vrai rempart reste le 403 backend (même
 * choix que Tarifs/Livraisons dans VignettesMotoComponent — AuthUser.role, pas
 * NavPermissionsService.canAccessAdminMenus() qui répond à une question différente).
 * Réutilise le chrome visuel .ldb-* (cartes KPI/panneaux/légendes) déjà partagé par le tableau de
 * bord Comptabilité ET celui de Satisfaction Client — générique malgré le préfixe "ledger". */
@Component({
  selector: 'app-kpi-dashboard',
  templateUrl: './kpi-dashboard.component.html',
  styleUrl: './kpi-dashboard.component.scss',
  imports: [
    ButtonDirective,
    ChartjsComponent,
    ReactiveFormsModule,
    NgTemplateOutlet,

    RouterLink,
  ],
})
export class KpiDashboardComponent {
  private readonly dashboardService = inject(DashboardService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');

  /** Onglet actif piloté depuis DashboardComponent (Contrats/Global/Vignettes/Sorties de
   * fonds/Assurés vivent tous dans le même bandeau d'onglets sur /dashboard) — ce composant
   * n'a plus son propre sélecteur d'onglet. */
  readonly activeView = input<View>('global');
  /** Liens « Détail → » de la vue Global : le parent (DashboardComponent) change d'onglet. */
  readonly navigate = output<View>();

  readonly rangeForm = this.fb.nonNullable.group({
    dateDebut: [firstDayOfMonth()],
    dateFin: [today()],
  });

  applyRange(): void {
    const raw = this.rangeForm.getRawValue();
    this.appliedRange.set({ debut: raw.dateDebut, fin: raw.dateFin });
    this.globalData.set(null);
    this.vignetteData.set(null);
    this.sortieFondsData.set(null);
    this.assureData.set(null);
    this.fetchCurrentView();
  }

  /** Masquage d'affichage uniquement (voir isAdmin()) — mais on évite quand même d'émettre des
   * appels vers des endpoints qui répondraient 403 de toute façon pour un non-admin. */
  private fetchCurrentView(): void {
    if (!this.isAdmin()) return;
    switch (this.activeView()) {
      case 'global':
        if (!this.globalData()) this.fetchGlobal();
        break;
      case 'vignettes':
        if (!this.vignetteData()) this.fetchVignettes();
        break;
      case 'sortiesFonds':
        if (!this.sortieFondsData()) this.fetchSortiesFonds();
        break;
      case 'assures':
        if (!this.assureData()) this.fetchAssures();
        break;
    }
  }

  constructor() {
    effect(() => {
      this.activeView();
      this.fetchCurrentView();
    });
  }

  // ================================================================ GLOBAL ================================================================

  readonly globalLoading = signal(false);
  readonly globalError = signal<string | null>(null);
  readonly globalData = signal<DashboardGlobalResponse | null>(null);

  private fetchGlobal(): void {
    const raw = this.rangeForm.getRawValue();
    this.globalLoading.set(true);
    this.globalError.set(null);
    this.dashboardService.getGlobalKpis(raw.dateDebut, raw.dateFin).subscribe({
      next: (data) => {
        this.globalData.set(data);
        this.globalLoading.set(false);
        // Petites courbes de tendance de la vue Global : l'API globale ne renvoie pas d'évolution,
        // on précharge donc les 3 domaines (qui servent aussi, en cache, à leurs onglets).
        if (!this.vignetteData() && !this.vignetteLoading()) this.fetchVignettes();
        if (!this.sortieFondsData() && !this.sortieFondsLoading()) this.fetchSortiesFonds();
        if (!this.assureData() && !this.assureLoading()) this.fetchAssures();
      },
      error: () => {
        this.globalError.set('Impossible de charger le tableau de bord global.');
        this.globalLoading.set(false);
      },
    });
  }

  readonly globalAssureCards = computed<KpiCard[]>(() => this.assureHeadlineCards(this.globalData()?.assures));
  readonly globalVignetteCards = computed<KpiCard[]>(() => this.vignetteHeadlineCards(this.globalData()?.vignettes));
  readonly globalSortieFondsCards = computed<KpiCard[]>(() => this.sortieFondsHeadlineCards(this.globalData()?.sortiesFonds));

  // ================================================================ VIGNETTES ================================================================

  readonly vignetteLoading = signal(false);
  readonly vignetteError = signal<string | null>(null);
  readonly vignetteData = signal<DashboardVignetteResponse | null>(null);

  private fetchVignettes(): void {
    const raw = this.rangeForm.getRawValue();
    this.vignetteLoading.set(true);
    this.vignetteError.set(null);
    this.dashboardService.getVignettesKpis(raw.dateDebut, raw.dateFin).subscribe({
      next: (data) => {
        this.vignetteData.set(data);
        this.vignetteLoading.set(false);
      },
      error: () => {
        this.vignetteError.set('Impossible de charger le tableau de bord vignettes.');
        this.vignetteLoading.set(false);
      },
    });
  }

  readonly vignetteCards = computed<KpiCard[]>(() => {
    const k = this.vignetteData()?.kpis;
    if (!k) return [];
    return [
      this.card('nombreDemandes', 'Demandes totales', 'cilDescription', 'primary', k.nombreDemandes, this.formatInt),
      this.card('nouvellesDemandes', 'Nouvelles demandes', 'cilPlus', 'info', k.nouvellesDemandes, this.formatInt),
      this.card('renouvellements', 'Renouvellements', 'cilHistory', 'info', k.renouvellements, this.formatInt),
      this.card('vignettesEditees', 'Vignettes éditées', 'cilPencil', 'primary', k.vignettesEditees, this.formatInt),
      this.card('vignettesLivrees', 'Vignettes livrées', 'cilCheckCircle', 'success', k.vignettesLivrees, this.formatInt),
      this.card('demandesEnAttente', 'Demandes en attente', 'cilWarning', 'warning', k.demandesEnAttente, this.formatInt),
      this.card('demandesRejetees', 'Demandes rejetées', 'cilXCircle', 'danger', k.demandesRejetees, this.formatInt),
      this.card('demandesAnnulees', 'Demandes annulées', 'cilXCircle', 'danger', k.demandesAnnulees, this.formatInt),
      this.card('montantVignettes', 'Montant vignettes', 'cilDollar', 'primary', k.montantVignettes, this.formatAmount),
      this.card('totalFraisOperation', "Frais d'opération", 'cilCalculator', 'info', k.totalFraisOperation, this.formatAmount),
      this.card('totalFraisLivraison', 'Frais de livraison', 'cilMap', 'info', k.totalFraisLivraison, this.formatAmount),
      this.card('totalFraisAccessoires', 'Frais accessoires', 'cilBasket', 'info', k.totalFraisAccessoires, this.formatAmount),
      this.card('chiffreAffaires', "Chiffre d'affaires", 'cilChartPie', 'success', k.chiffreAffaires, this.formatAmount),
      this.card('panierMoyen', 'Panier moyen', 'cilCreditCard', 'primary', k.panierMoyen, this.formatAmount),
      this.card('tauxTransformation', 'Taux de transformation', 'cilSpeedometer', 'secondary', k.tauxTransformation, this.formatPercent),
      this.card('tauxEdition', "Taux d'édition", 'cilSpeedometer', 'secondary', k.tauxEdition, this.formatPercent),
      this.card('tauxLivraison', 'Taux de livraison', 'cilSpeedometer', 'secondary', k.tauxLivraison, this.formatPercent),
      this.card('tauxRejet', 'Taux de rejet', 'cilSpeedometer', 'secondary', k.tauxRejet, this.formatPercent),
      this.card('tauxAnnulation', "Taux d'annulation", 'cilSpeedometer', 'secondary', k.tauxAnnulation, this.formatPercent),
    ];
  });

  /** delaiMoyenTraitementJours reste null en permanence pour l'instant (pas de workflow
   * paiement/édition/livraison derrière) — affiché à part, pas comme une KpiCard à variation. */
  readonly vignetteDelaiMoyen = computed(() => this.vignetteData()?.kpis.delaiMoyenTraitementJours ?? null);

  readonly vignetteEvolutionChart = computed(() => {
    const items = this.vignetteData()?.evolution ?? [];
    return this.buildLineChart(items.map((i) => i.periode), [
      { label: 'Demandes', data: items.map((i) => i.nombreDemandes), color: getStyle('--cui-primary') ?? '#5856d6', axis: 'y' },
      { label: 'Montant (FCFA)', data: items.map((i) => i.montant), color: getStyle('--cui-success') ?? '#2eb85c', axis: 'y1' },
    ]);
  });

  readonly vignetteParStatutChart = computed(() =>
    this.buildDonutChart((this.vignetteData()?.repartitions.parStatut ?? []).map((i) => ({ label: VIGNETTE_STATUT_LABEL[i.statut], value: i.nombre }))),
  );
  readonly vignetteParStatutLegend = computed(() =>
    this.legendFor((this.vignetteData()?.repartitions.parStatut ?? []).map((i) => ({ label: VIGNETTE_STATUT_LABEL[i.statut], value: i.nombre }))),
  );

  readonly vignetteParTypeDemandeChart = computed(() =>
    this.buildDonutChart((this.vignetteData()?.repartitions.parTypeDemande ?? []).map((i) => ({ label: TYPE_DEMANDE_LABEL[i.type], value: i.nombre }))),
  );
  readonly vignetteParTypeDemandeLegend = computed(() =>
    this.legendFor((this.vignetteData()?.repartitions.parTypeDemande ?? []).map((i) => ({ label: TYPE_DEMANDE_LABEL[i.type], value: i.nombre }))),
  );

  readonly vignetteParCylindreeChart = computed(() =>
    this.buildDonutChart(
      (this.vignetteData()?.repartitions.parCylindree ?? []).map((i) => ({ label: CATEGORIE_CYLINDREE_LABEL[i.categorie], value: i.nombre })),
    ),
  );
  readonly vignetteParCylindreeLegend = computed(() =>
    this.legendFor((this.vignetteData()?.repartitions.parCylindree ?? []).map((i) => ({ label: CATEGORIE_CYLINDREE_LABEL[i.categorie], value: i.nombre }))),
  );

  readonly vignetteParAgeMotoChart = computed(() =>
    this.buildDonutChart((this.vignetteData()?.repartitions.parAgeMoto ?? []).map((i) => ({ label: CATEGORIE_AGE_LABEL[i.categorie], value: i.nombre }))),
  );
  readonly vignetteParAgeMotoLegend = computed(() =>
    this.legendFor((this.vignetteData()?.repartitions.parAgeMoto ?? []).map((i) => ({ label: CATEGORIE_AGE_LABEL[i.categorie], value: i.nombre }))),
  );

  /** Zones de livraison : liste ouverte (codes dynamiques), pas un donut — classement en tableau. */
  readonly vignetteParZoneLivraisonRanked = computed(() =>
    [...(this.vignetteData()?.repartitions.parZoneLivraison ?? [])].sort((a, b) => b.nombre - a.nombre),
  );

  // ================================================================ SORTIES DE FONDS ================================================================

  readonly sortieFondsLoading = signal(false);
  readonly sortieFondsError = signal<string | null>(null);
  readonly sortieFondsData = signal<DashboardSortieFondsResponse | null>(null);

  private fetchSortiesFonds(): void {
    const raw = this.rangeForm.getRawValue();
    this.sortieFondsLoading.set(true);
    this.sortieFondsError.set(null);
    this.dashboardService.getSortiesFondsKpis(raw.dateDebut, raw.dateFin).subscribe({
      next: (data) => {
        this.sortieFondsData.set(data);
        this.sortieFondsLoading.set(false);
      },
      error: () => {
        this.sortieFondsError.set('Impossible de charger le tableau de bord des sorties de fonds.');
        this.sortieFondsLoading.set(false);
      },
    });
  }

  readonly sortieFondsCards = computed<KpiCard[]>(() => {
    const k = this.sortieFondsData()?.kpis;
    if (!k) return [];
    return [
      this.card('totalSorties', 'Sorties totales', 'cilDescription', 'primary', k.totalSorties, this.formatInt),
      this.card('enAttente', 'En attente', 'cilWarning', 'warning', k.enAttente, this.formatInt),
      this.card('validees', 'Validées', 'cilCheckCircle', 'info', k.validees, this.formatInt),
      this.card('executees', 'Exécutées', 'cilCheckCircle', 'success', k.executees, this.formatInt),
      this.card('rejetees', 'Rejetées', 'cilXCircle', 'danger', k.rejetees, this.formatInt),
      this.card('annulees', 'Annulées', 'cilXCircle', 'danger', k.annulees, this.formatInt),
      this.card('montantTotal', 'Montant total', 'cilDollar', 'primary', k.montantTotal, this.formatAmount),
      this.card('montantEnAttente', 'Montant en attente', 'cilWarning', 'warning', k.montantEnAttente, this.formatAmount),
      this.card('montantValide', 'Montant validé', 'cilCheckCircle', 'info', k.montantValide, this.formatAmount),
      this.card('montantExecute', 'Montant exécuté', 'cilCreditCard', 'success', k.montantExecute, this.formatAmount),
      this.card('montantRejete', 'Montant rejeté', 'cilXCircle', 'danger', k.montantRejete, this.formatAmount),
      this.card('montantAnnule', 'Montant annulé', 'cilXCircle', 'danger', k.montantAnnule, this.formatAmount),
      this.card('montantMoyenParSortie', 'Montant moyen / sortie', 'cilCalculator', 'primary', k.montantMoyenParSortie, this.formatAmount),
      this.card('tauxExecution', "Taux d'exécution", 'cilSpeedometer', 'secondary', k.tauxExecution, this.formatPercent),
      this.card('tauxRejet', 'Taux de rejet', 'cilSpeedometer', 'secondary', k.tauxRejet, this.formatPercent),
      this.card('delaiMoyenTraitementJours', 'Délai moyen de traitement', 'cilHistory', 'secondary', k.delaiMoyenTraitementJours, this.formatDays),
      this.card('totalEncaissements', 'Total encaissements', 'cilInbox', 'success', k.totalEncaissements, this.formatAmount),
      this.card('solde', 'Solde (trésorerie)', 'cilChartPie', 'primary', k.solde, this.formatAmount),
      this.card('ratioSortiesEncaissements', 'Ratio sorties / encaissements', 'cilSpeedometer', 'secondary', k.ratioSortiesEncaissements, this.formatPercent),
    ];
  });

  /** Jamais un KpiValue — un maximum instantané n'a pas de sens comparé à une période précédente. */
  readonly sortieFondsMaxMontant = computed(() => this.sortieFondsData()?.kpis.montantPlusGrosseSortie ?? null);

  readonly sortieFondsEvolutionChart = computed(() => {
    const items = this.sortieFondsData()?.evolution ?? [];
    return this.buildLineChart(items.map((i) => i.periode), [
      { label: 'Sorties', data: items.map((i) => i.nombre), color: getStyle('--cui-primary') ?? '#5856d6', axis: 'y' },
      { label: 'Montant (FCFA)', data: items.map((i) => i.montant), color: getStyle('--cui-danger') ?? '#e55353', axis: 'y1' },
    ]);
  });

  readonly sortieFondsParStatutChart = computed(() =>
    this.buildDonutChart(
      (this.sortieFondsData()?.repartitions.parStatut ?? []).map((i) => ({ label: SORTIE_FONDS_STATUT_LABEL[i.statut] ?? i.statut, value: i.nombre })),
    ),
  );
  readonly sortieFondsParStatutLegend = computed(() =>
    this.legendFor((this.sortieFondsData()?.repartitions.parStatut ?? []).map((i) => ({ label: SORTIE_FONDS_STATUT_LABEL[i.statut] ?? i.statut, value: i.nombre }))),
  );

  readonly sortieFondsParTypeChart = computed(() =>
    this.buildDonutChart((this.sortieFondsData()?.repartitions.parType ?? []).map((i) => ({ label: i.typeCode, value: i.montant }))),
  );
  readonly sortieFondsParTypeLegend = computed(() =>
    this.legendFor((this.sortieFondsData()?.repartitions.parType ?? []).map((i) => ({ label: i.typeCode, value: i.montant })), this.formatAmount),
  );

  /** Bénéficiaires : liste ouverte, pas un donut — classement en tableau par montant. */
  readonly sortieFondsParBeneficiaireRanked = computed(() =>
    [...(this.sortieFondsData()?.repartitions.parBeneficiaire ?? [])].sort((a, b) => b.montant - a.montant),
  );

  // ================================================================ ASSURÉS ================================================================

  readonly assureLoading = signal(false);
  readonly assureError = signal<string | null>(null);
  readonly assureData = signal<DashboardAssureResponse | null>(null);

  private fetchAssures(): void {
    const raw = this.rangeForm.getRawValue();
    this.assureLoading.set(true);
    this.assureError.set(null);
    this.dashboardService.getAssuresKpis(raw.dateDebut, raw.dateFin).subscribe({
      next: (data) => {
        this.assureData.set(data);
        this.assureLoading.set(false);
      },
      error: () => {
        this.assureError.set('Impossible de charger le tableau de bord des assurés.');
        this.assureLoading.set(false);
      },
    });
  }

  readonly assureCards = computed<KpiCard[]>(() => {
    const k = this.assureData()?.kpis;
    if (!k) return [];
    return [
      this.card('totalAssures', 'Assurés totaux', 'cilPeople', 'primary', k.totalAssures, this.formatInt),
      this.card('nouveauxAssures', 'Nouveaux assurés', 'cilUserFollow', 'info', k.nouveauxAssures, this.formatInt),
      this.card('assuresActifs', 'Assurés actifs', 'cilCheckCircle', 'success', k.assuresActifs, this.formatInt),
      this.card('assuresInactifs', 'Assurés inactifs', 'cilUserUnfollow', 'warning', k.assuresInactifs, this.formatInt),
      this.card('assuresPlusieursContrats', 'Assurés multi-contrats', 'cilLayers', 'secondary', k.assuresPlusieursContrats, this.formatInt),
      this.card('assuresMultiEquipes', 'Assurés multi-équipés', 'cilPuzzle', 'secondary', k.assuresMultiEquipes, this.formatInt),
      this.card('nombreMoyenContratsParAssure', 'Contrats moyens / assuré', 'cilCalculator', 'primary', k.nombreMoyenContratsParAssure, this.formatDecimal),
      this.card('tauxCroissance', 'Taux de croissance', 'cilSpeedometer', 'secondary', k.tauxCroissance, this.formatPercent),
      this.card('tauxMultiEquipement', "Taux de multi-équipement", 'cilSpeedometer', 'secondary', k.tauxMultiEquipement, this.formatPercent),
    ];
  });

  readonly assureEvolutionChart = computed(() => {
    const items = this.assureData()?.evolution ?? [];
    return this.buildLineChart(items.map((i) => i.periode), [
      { label: 'Nouveaux assurés', data: items.map((i) => i.nouveauxAssures), color: getStyle('--cui-info') ?? '#39f' },
      { label: 'Assurés totaux (cumul)', data: items.map((i) => i.totalAssures), color: getStyle('--cui-primary') ?? '#5856d6' },
    ]);
  });

  readonly assureParTypeClientChart = computed(() =>
    this.buildDonutChart((this.assureData()?.repartitions.parTypeClient ?? []).map((i) => ({ label: TYPE_CLIENT_LABEL[i.typeClient], value: i.nombre }))),
  );
  readonly assureParTypeClientLegend = computed(() =>
    this.legendFor((this.assureData()?.repartitions.parTypeClient ?? []).map((i) => ({ label: TYPE_CLIENT_LABEL[i.typeClient], value: i.nombre }))),
  );

  /** Villes : liste ouverte, pas un donut — classement en tableau. */
  readonly assureParVilleRanked = computed(() => [...(this.assureData()?.repartitions.parVille ?? [])].sort((a, b) => b.nombre - a.nombre));

  // ================================================================ REFONTE (maquette validée le 2026-10-02) ================================================================

  // ---- Période ----

  readonly preset = signal<PeriodPreset>('month');
  readonly appliedRange = signal<{ debut: string; fin: string }>({ debut: firstDayOfMonth(), fin: today() });
  readonly presets: { key: PeriodPreset; label: string }[] = [
    { key: 'today', label: "Aujourd'hui" },
    { key: '7d', label: '7 derniers jours' },
    { key: 'month', label: 'Ce mois-ci' },
    { key: 'lastMonth', label: 'Mois dernier' },
    { key: 'year', label: 'Cette année' },
    { key: 'custom', label: 'Personnalisée…' },
  ];

  setPreset(preset: PeriodPreset): void {
    this.preset.set(preset);
    if (preset === 'custom') return; // l'utilisateur saisit ses dates puis « Appliquer »
    const range = presetRange(preset);
    this.rangeForm.patchValue({ dateDebut: range.debut, dateFin: range.fin });
    this.applyRange();
  }

  readonly compareLabel = computed(() => {
    switch (this.preset()) {
      case 'today':
        return 'comparé à la veille';
      case 'month':
      case 'lastMonth':
        return 'comparé au mois précédent';
      case 'year':
        return "comparé à l'année précédente";
      default:
        return 'comparé à la période précédente';
    }
  });

  formatDateFr(iso: string): string {
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }

  // ---- Variations (pastilles ↑ ↓ =) ----

  /** inverse = une hausse est une mauvaise nouvelle (en attente, sorties, rejets…). */
  delta(kpi: KpiValue<number> | null | undefined, inverse = false): { label: string; cls: string } {
    if (!kpi) return { label: '—', cls: 'flat' };
    const pctNul = kpi.variationPourcentage === null ? kpi.variation === 0 : kpi.variationPourcentage === 0;
    if (kpi.tendance === 'STABLE' || pctNul) return { label: '= 0 %', cls: 'flat' };
    const up = kpi.tendance === 'HAUSSE';
    const good = inverse ? !up : up;
    // variationPourcentage null = période précédente à 0 : on affiche l'écart absolu (« ↑ 17 »).
    const amount =
      kpi.variationPourcentage === null
        ? this.formatCompact(Math.abs(kpi.variation))
        : `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(Math.abs(kpi.variationPourcentage))} %`;
    return { label: `${up ? '↑' : '↓'} ${amount}`, cls: good ? 'up' : 'down' };
  }

  val(kpi: KpiValue<number> | null | undefined): number | null {
    return kpi ? kpi.valeur : null;
  }

  fmtInt(kpi: KpiValue<number> | null | undefined): string {
    return kpi ? this.formatInt(kpi.valeur) : '—';
  }

  fmtAmount(kpi: KpiValue<number> | null | undefined): string {
    return kpi ? this.formatAmount(kpi.valeur) : '—';
  }

  fmtPercent(kpi: KpiValue<number> | null | undefined): string {
    return kpi ? this.formatPercentFr(kpi.valeur) : '—';
  }

  formatDecimalFr(value: number): string {
    return new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value);
  }

  formatPercentFr(value: number): string {
    return `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(value)} %`;
  }

  /** 2 108 000 → « 2,1 M », 640 000 → « 640 k » (barres et sous-indicateurs compacts). */
  formatCompact(value: number): string {
    const abs = Math.abs(value);
    if (abs >= 1_000_000) return `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(value / 1_000_000)} M`;
    if (abs >= 10_000) return `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(value / 1000)} k`;
    return this.formatInt(value);
  }

  share(part: number | null, total: number | null): string {
    if (!part || !total) return '0 %';
    return `${Math.round((part / total) * 100)} %`;
  }

  // ---- Global : petites courbes de tendance (évolution des 3 domaines) ----

  /** Chemin SVG (viewBox 200×44) d'une série : ligne + aire. null si moins de 2 points. */
  sparkline(values: number[]): { line: string; area: string } | null {
    if (values.length < 2) return null;
    const max = Math.max(...values);
    const min = Math.min(...values);
    const span = max - min || 1;
    const points = values.map((v, i) => {
      const x = (i / (values.length - 1)) * 200;
      const y = 40 - ((v - min) / span) * 32;
      return `${x.toFixed(1)} ${y.toFixed(1)}`;
    });
    const line = `M${points.join(' L')}`;
    return { line, area: `${line} V44 H0Z` };
  }

  readonly assureSpark = computed(() => this.sparkline((this.assureData()?.evolution ?? []).map((i) => i.totalAssures)));
  readonly vignetteSpark = computed(() => this.sparkline((this.vignetteData()?.evolution ?? []).map((i) => i.montant)));
  readonly sortieFondsSpark = computed(() => this.sparkline((this.sortieFondsData()?.evolution ?? []).map((i) => i.montant)));

  // ---- Barres horizontales génériques ----

  private bars(items: { label: string; value: number }[], formatter: (n: number) => string, limit?: number): HBar[] {
    const sorted = [...items].filter((i) => i.value > 0).sort((a, b) => b.value - a.value);
    const shown = limit ? sorted.slice(0, limit) : sorted;
    const max = Math.max(1, ...shown.map((i) => i.value));
    return shown.map((i) => ({ label: i.label, value: formatter(i.value), pct: Math.max(3, (i.value / max) * 100) }));
  }

  // ---- Vignettes ----

  readonly vignetteFunnel = computed(() => {
    const k = this.vignetteData()?.kpis;
    if (!k) return [];
    const total = k.nombreDemandes?.valeur ?? 0;
    const rate = (n: number) => (total ? Math.round((n / total) * 100) : 0);
    const editees = k.vignettesEditees?.valeur ?? 0;
    const livrees = k.vignettesLivrees?.valeur ?? 0;
    return [
      { label: 'Demandes', value: total, pct: total ? 100 : 0, rate: total ? 100 : 0, color: '#1b3a8f' },
      { label: 'Éditées', value: editees, pct: rate(editees), rate: rate(editees), color: '#3d5bb3' },
      { label: 'Livrées', value: livrees, pct: rate(livrees), rate: rate(livrees), color: '#1f9d6b' },
    ];
  });

  readonly vignetteComposition = computed(() => {
    const k = this.vignetteData()?.kpis;
    if (!k) return [];
    const rows = [
      { label: 'Montant vignettes', value: k.montantVignettes?.valeur ?? 0, color: '#7357d6' },
      { label: "Frais d'opération", value: k.totalFraisOperation?.valeur ?? 0, color: '#2f6fd6' },
      { label: 'Frais de livraison', value: k.totalFraisLivraison?.valeur ?? 0, color: '#e8792b' },
      { label: 'Frais accessoires', value: k.totalFraisAccessoires?.valeur ?? 0, color: '#b7791f' },
    ];
    const total = rows.reduce((s, r) => s + r.value, 0);
    return rows.map((r) => ({ ...r, pct: total ? (r.value / total) * 100 : 0, display: this.formatInt(r.value) }));
  });

  /** 10 statuts techniques regroupés en 4 familles lisibles pour l'anneau. */
  readonly vignetteStatutGroups = computed(() => {
    const items = this.vignetteData()?.repartitions.parStatut ?? [];
    const sum = (codes: VignetteMotoStatutCode[]) => items.filter((i) => codes.includes(i.statut)).reduce((s, i) => s + i.nombre, 0);
    const groups = [
      { label: 'Livrées', value: sum(['LIVREE']), color: '#1f9d6b' },
      { label: 'Éditées / en livraison', value: sum(['EDITEE', 'EN_LIVRAISON']), color: '#2f6fd6' },
      { label: 'En cours', value: sum(['BROUILLON', 'EN_ATTENTE_PAIEMENT', 'PAYEE', 'EN_TRAITEMENT', 'EN_EDITION']), color: '#b7791f' },
      { label: 'Rejetées / annulées', value: sum(['REJETEE', 'ANNULEE']), color: '#d64545' },
    ];
    return groups;
  });

  readonly vignetteStatutTotal = computed(() => this.vignetteStatutGroups().reduce((s, g) => s + g.value, 0));

  readonly vignetteStatutDonut = computed(() => {
    const groups = this.vignetteStatutGroups().filter((g) => g.value > 0);
    if (groups.length === 0) return null;
    return {
      data: { labels: groups.map((g) => g.label), datasets: [{ data: groups.map((g) => g.value), backgroundColor: groups.map((g) => g.color), borderWidth: 0 }] },
      options: { maintainAspectRatio: false, cutout: '72%', plugins: { legend: { display: false } } },
    };
  });

  readonly vignetteCylindreeBars = computed(() =>
    this.bars((this.vignetteData()?.repartitions.parCylindree ?? []).map((i) => ({ label: CATEGORIE_CYLINDREE_LABEL[i.categorie], value: i.nombre })), this.formatInt),
  );
  readonly vignetteAgeBars = computed(() =>
    this.bars((this.vignetteData()?.repartitions.parAgeMoto ?? []).map((i) => ({ label: CATEGORIE_AGE_LABEL[i.categorie], value: i.nombre })), this.formatInt),
  );
  readonly vignetteZoneBars = computed(() =>
    this.bars((this.vignetteData()?.repartitions.parZoneLivraison ?? []).map((i) => ({ label: i.zoneCode, value: i.nombre })), this.formatInt, 5),
  );

  readonly vignetteTypeSplit = computed(() => {
    const items = this.vignetteData()?.repartitions.parTypeDemande ?? [];
    const colors: Record<string, string> = { NOUVELLE_DEMANDE: '#7357d6', RENOUVELLEMENT: '#e8792b' };
    const total = items.reduce((s, i) => s + i.nombre, 0);
    return items.map((i) => ({ label: i.type === 'NOUVELLE_DEMANDE' ? 'Nouvelles' : 'Renouvellements', value: this.formatInt(i.nombre), pct: total ? (i.nombre / total) * 100 : 0, color: colors[i.type] ?? '#8fa3d9' }));
  });

  readonly vignetteComboChart = computed(() => {
    const items = this.vignetteData()?.evolution ?? [];
    return this.buildComboChart(items.map((i) => i.periode), { label: 'Demandes', data: items.map((i) => i.nombreDemandes), color: 'rgba(115, 87, 214, 0.28)' }, { label: "Chiffre d'affaires (FCFA)", data: items.map((i) => i.montant), color: '#7357d6' });
  });

  // ---- Sorties de fonds ----

  readonly sortieFondsTreasury = computed(() => {
    const k = this.sortieFondsData()?.kpis;
    const enc = k?.totalEncaissements?.valeur ?? 0;
    const sorties = k?.montantExecute?.valeur ?? 0;
    const total = enc + sorties;
    return { encPct: total ? (enc / total) * 100 : 50, sortiesPct: total ? (sorties / total) * 100 : 50, ratio: k?.ratioSortiesEncaissements?.valeur ?? null };
  });

  /** Arc SVG de la jauge (viewBox 100×56) pour un taux 0-100. */
  gaugeArc(rate: number | null): string {
    const r = Math.max(0, Math.min(100, rate ?? 0)) / 100;
    const angle = Math.PI * (1 - r);
    const x = 50 + 42 * Math.cos(angle);
    const y = 52 - 42 * Math.sin(angle);
    return `M8 52 A42 42 0 0 1 ${x.toFixed(2)} ${y.toFixed(2)}`;
  }

  readonly sortieFondsPipeline = computed(() => {
    const k = this.sortieFondsData()?.kpis;
    if (!k) return [];
    return [
      { label: 'En attente', cls: 'warn', count: this.fmtInt(k.enAttente), amount: this.fmtAmount(k.montantEnAttente) },
      { label: 'Validées', cls: 'info', count: this.fmtInt(k.validees), amount: this.fmtAmount(k.montantValide) },
      { label: 'Exécutées', cls: 'up', count: this.fmtInt(k.executees), amount: this.fmtAmount(k.montantExecute) },
      { label: 'Rejetées', cls: 'down', count: this.fmtInt(k.rejetees), amount: this.fmtAmount(k.montantRejete) },
      { label: 'Annulées', cls: 'flat', count: this.fmtInt(k.annulees), amount: this.fmtAmount(k.montantAnnule) },
    ];
  });

  readonly sortieFondsTypeBars = computed(() =>
    this.bars((this.sortieFondsData()?.repartitions.parType ?? []).map((i) => ({ label: i.typeCode, value: i.montant })), (n) => this.formatCompact(n)),
  );
  readonly sortieFondsBeneficiaireBars = computed(() =>
    this.bars((this.sortieFondsData()?.repartitions.parBeneficiaire ?? []).map((i) => ({ label: i.beneficiaire, value: i.montant })), (n) => this.formatCompact(n), 5),
  );

  readonly sortieFondsAreaChart = computed(() => {
    const items = this.sortieFondsData()?.evolution ?? [];
    return this.buildComboChart(items.map((i) => i.periode), null, { label: 'Montant (FCFA)', data: items.map((i) => i.montant), color: '#e8792b', fill: true });
  });

  // ---- Assurés ----

  readonly assureActifsPct = computed(() => {
    const k = this.assureData()?.kpis;
    const actifs = k?.assuresActifs?.valeur ?? 0;
    const total = actifs + (k?.assuresInactifs?.valeur ?? 0);
    return total ? (actifs / total) * 100 : 0;
  });

  readonly assureTypeSplit = computed(() => {
    const items = this.assureData()?.repartitions.parTypeClient ?? [];
    const total = items.reduce((s, i) => s + i.nombre, 0);
    const colors: Record<string, string> = { PARTICULIER: '#1b3a8f', ENTREPRISE: '#e8792b' };
    return items.map((i) => ({
      label: i.typeClient === 'PARTICULIER' ? 'Particuliers' : 'Entreprises',
      value: `${this.formatInt(i.nombre)} · ${this.share(i.nombre, total)}`,
      pct: total ? (i.nombre / total) * 100 : 0,
      color: colors[i.typeClient] ?? '#8fa3d9',
    }));
  });

  readonly assureVilleBars = computed(() =>
    this.bars((this.assureData()?.repartitions.parVille ?? []).map((i) => ({ label: i.ville, value: i.nombre })), this.formatInt, 5),
  );

  readonly assureComboChart = computed(() => {
    const items = this.assureData()?.evolution ?? [];
    return this.buildComboChart(items.map((i) => i.periode), { label: 'Nouveaux assurés', data: items.map((i) => i.nouveauxAssures), color: 'rgba(47, 111, 214, 0.32)' }, { label: 'Total cumulé', data: items.map((i) => i.totalAssures), color: '#1b3a8f' });
  });

  /** Barres (axe gauche) + courbe (axe droit), style de la maquette : sans quadrillage vertical,
   * légende en bas, infobulles chart.js conservées. */
  private buildComboChart(
    labels: string[],
    bars: { label: string; data: number[]; color: string } | null,
    line: { label: string; data: number[]; color: string; fill?: boolean },
  ): { data: any; options: any } | null {
    if (labels.length === 0) return null;
    const grid = '#e4e7ef';
    const tick = '#6b7489';
    const datasets: any[] = [];
    if (bars) {
      datasets.push({ type: 'bar', label: bars.label, data: bars.data, backgroundColor: bars.color, borderRadius: 4, maxBarThickness: 26, yAxisID: 'y', order: 2 });
    }
    datasets.push({
      type: 'line',
      label: line.label,
      data: line.data,
      borderColor: line.color,
      backgroundColor: line.fill ? `${line.color}22` : 'transparent',
      fill: !!line.fill,
      tension: 0.35,
      borderWidth: 2.5,
      pointRadius: 0,
      pointHoverRadius: 4,
      yAxisID: bars ? 'y1' : 'y',
      order: 1,
    });
    return {
      data: { labels, datasets },
      options: {
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: true, position: 'bottom', labels: { color: tick, boxWidth: 10, usePointStyle: true } } },
        scales: {
          x: { grid: { display: false }, ticks: { color: tick, maxRotation: 0, autoSkip: true, maxTicksLimit: 8 } },
          y: { position: 'left', border: { display: false }, grid: { color: grid }, beginAtZero: true, ticks: { color: tick } },
          ...(bars ? { y1: { position: 'right', border: { display: false }, grid: { drawOnChartArea: false }, beginAtZero: true, ticks: { color: tick } } } : {}),
        },
      },
    };
  }

  // ================================================================ HELPERS PARTAGÉS ================================================================

  /** kpi peut être null en pratique (pas seulement variationPourcentage) — constaté en direct sur
   * des indicateurs calculés (ex. délai moyen de traitement) quand le volume sous-jacent est nul,
   * malgré un schéma OpenAPI qui ne déclare pas le champ nullable. On l'affiche comme "—" plutôt
   * que de planter. */
  private card(key: string, label: string, icon: string, color: string, kpi: KpiValue<number> | null | undefined, formatter: (n: number) => string): KpiCard {
    if (!kpi) {
      return { key, label, icon, color, value: '—', variation: '—', tendanceIcon: 'cilArrowRight' };
    }
    return {
      key,
      label,
      icon,
      color,
      value: formatter(kpi.valeur),
      variation: kpi.variationPourcentage === null ? '—' : `${kpi.variationPourcentage > 0 ? '+' : ''}${kpi.variationPourcentage.toFixed(1)} % vs période précédente`,
      tendanceIcon: this.tendanceIcon(kpi.tendance),
    };
  }

  private tendanceIcon(tendance: Tendance): string {
    if (tendance === 'HAUSSE') return 'cilArrowTop';
    if (tendance === 'BAISSE') return 'cilArrowBottom';
    return 'cilArrowRight';
  }

  /** Sous-ensemble de cartes "phares" par domaine pour l'onglet Global — le contrat backend ne
   * renvoie que l'objet kpis complet (pas de répartitions/évolution ici), donc on choisit les
   * indicateurs les plus représentatifs plutôt que d'afficher les 20/9 champs en condensé. */
  private vignetteHeadlineCards(k: VignetteKpis | undefined): KpiCard[] {
    if (!k) return [];
    return [
      this.card('nombreDemandes', 'Demandes totales', 'cilDescription', 'primary', k.nombreDemandes, this.formatInt),
      this.card('vignettesLivrees', 'Vignettes livrées', 'cilCheckCircle', 'success', k.vignettesLivrees, this.formatInt),
      this.card('chiffreAffaires', "Chiffre d'affaires", 'cilChartPie', 'success', k.chiffreAffaires, this.formatAmount),
      this.card('demandesEnAttente', 'Demandes en attente', 'cilWarning', 'warning', k.demandesEnAttente, this.formatInt),
    ];
  }

  private sortieFondsHeadlineCards(k: SortieFondsKpis | undefined): KpiCard[] {
    if (!k) return [];
    return [
      this.card('totalSorties', 'Sorties totales', 'cilDescription', 'primary', k.totalSorties, this.formatInt),
      this.card('montantExecute', 'Montant exécuté', 'cilCreditCard', 'success', k.montantExecute, this.formatAmount),
      this.card('solde', 'Solde (trésorerie)', 'cilChartPie', 'primary', k.solde, this.formatAmount),
      this.card('enAttente', 'En attente', 'cilWarning', 'warning', k.enAttente, this.formatInt),
    ];
  }

  private assureHeadlineCards(k: AssureKpis | undefined): KpiCard[] {
    if (!k) return [];
    return [
      this.card('totalAssures', 'Assurés totaux', 'cilPeople', 'primary', k.totalAssures, this.formatInt),
      this.card('nouveauxAssures', 'Nouveaux assurés', 'cilUserFollow', 'info', k.nouveauxAssures, this.formatInt),
      this.card('assuresActifs', 'Assurés actifs', 'cilCheckCircle', 'success', k.assuresActifs, this.formatInt),
      this.card('tauxMultiEquipement', 'Taux de multi-équipement', 'cilSpeedometer', 'secondary', k.tauxMultiEquipement, this.formatPercent),
    ];
  }

  private legendFor(items: { label: string; value: number }[], formatter: (n: number) => string = this.formatInt, palette = CHART_PALETTE): LegendRow[] {
    return items.map((item, index) => ({
      label: item.label,
      value: formatter(item.value),
      color: getStyle(palette[index % palette.length]) ?? '#5856d6',
    }));
  }

  private buildDonutChart(items: { label: string; value: number }[], palette = CHART_PALETTE): { data: any; options: any } | null {
    if (items.length === 0) return null;
    const colorBody = getStyle('--cui-body-color');
    return {
      data: {
        labels: items.map((i) => i.label),
        datasets: [
          {
            data: items.map((i) => i.value),
            backgroundColor: items.map((_, index) => getStyle(palette[index % palette.length])),
            borderWidth: 0,
          },
        ],
      },
      options: {
        maintainAspectRatio: false,
        cutout: '68%',
        plugins: { legend: { display: false }, tooltip: { titleColor: colorBody } },
      },
    };
  }

  private buildLineChart(
    labels: string[],
    series: { label: string; data: number[]; color: string; axis?: 'y' | 'y1' }[],
  ): { data: any; options: any } | null {
    if (labels.length === 0) return null;
    const colorBorderTranslucent = getStyle('--cui-border-color-translucent');
    const colorBody = getStyle('--cui-body-color');
    const usesSecondAxis = series.some((s) => s.axis === 'y1');
    return {
      data: {
        labels,
        datasets: series.map((s) => ({
          label: s.label,
          data: s.data,
          borderColor: s.color,
          backgroundColor: 'transparent',
          fill: false,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 4,
          yAxisID: s.axis === 'y1' ? 'y1' : 'y',
        })),
      },
      options: {
        maintainAspectRatio: false,
        plugins: { legend: { display: true, position: 'bottom', labels: { color: colorBody, boxWidth: 10, usePointStyle: true } } },
        scales: {
          x: { grid: { color: colorBorderTranslucent, drawOnChartArea: false }, ticks: { color: colorBody, maxRotation: 0, autoSkip: true, maxTicksLimit: 10 } },
          y: { position: 'left', border: { color: colorBorderTranslucent }, grid: { color: colorBorderTranslucent }, beginAtZero: true, ticks: { color: colorBody } },
          ...(usesSecondAxis
            ? { y1: { position: 'right', grid: { drawOnChartArea: false }, beginAtZero: true, ticks: { color: colorBody } } }
            : {}),
        },
      },
    };
  }

  formatInt(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(Math.round(value));
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(Math.round(value))} FCFA`;
  }

  formatPercent(value: number): string {
    return `${value.toFixed(1)} %`;
  }

  formatDecimal(value: number): string {
    return value.toFixed(1);
  }

  formatDays(value: number | null): string {
    return value === null ? '—' : `${value.toFixed(1)} j`;
  }
}
