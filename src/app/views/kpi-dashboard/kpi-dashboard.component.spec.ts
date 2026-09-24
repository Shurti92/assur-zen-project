import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { IconSetService } from '@coreui/icons-angular';

import { KpiDashboardComponent } from './kpi-dashboard.component';
import { iconSubset } from '../../icons/icon-subset';
import {
  DashboardAssureResponse,
  DashboardGlobalResponse,
  DashboardSortieFondsResponse,
  DashboardVignetteResponse,
  KpiValue,
} from '../../core/dashboard/models/kpi-dashboard.model';

function kpi(valeur: number, variationPourcentage: number | null = 5): KpiValue<number> {
  return { valeur, valeurPrecedente: 0, variation: 0, variationPourcentage, tendance: 'HAUSSE' };
}

function vignetteResponse(overrides: Partial<DashboardVignetteResponse> = {}): DashboardVignetteResponse {
  return {
    periode: { dateDebut: '2026-09-01', dateFin: '2026-09-30' },
    kpis: {
      nombreDemandes: kpi(120),
      nouvellesDemandes: kpi(80),
      renouvellements: kpi(40),
      vignettesEditees: kpi(70),
      vignettesLivrees: kpi(60, null),
      demandesEnAttente: kpi(10),
      demandesRejetees: kpi(2),
      demandesAnnulees: kpi(1),
      montantVignettes: kpi(450000),
      totalFraisOperation: kpi(180000),
      totalFraisLivraison: kpi(180000),
      totalFraisAccessoires: kpi(360000),
      chiffreAffaires: kpi(810000),
      panierMoyen: kpi(6750),
      tauxTransformation: kpi(0),
      tauxEdition: kpi(0),
      tauxLivraison: kpi(0),
      tauxRejet: kpi(1.6),
      tauxAnnulation: kpi(0.8),
      delaiMoyenTraitementJours: null,
    },
    repartitions: {
      parTypeDemande: [{ type: 'NOUVELLE_DEMANDE', nombre: 80 }],
      parCylindree: [{ categorie: 'MOINS_125_CM3', nombre: 100 }],
      parAgeMoto: [{ categorie: '5_10_ANS', nombre: 60 }],
      parZoneLivraison: [{ zoneCode: 'ABIDJAN', nombre: 90 }],
      parStatut: [{ statut: 'BROUILLON', nombre: 20 }],
    },
    evolution: [{ periode: '2026-09-01', nombreDemandes: 5, montant: 30000 }],
    ...overrides,
  };
}

function sortieFondsResponse(overrides: Partial<DashboardSortieFondsResponse> = {}): DashboardSortieFondsResponse {
  return {
    periode: { dateDebut: '2026-09-01', dateFin: '2026-09-30' },
    kpis: {
      totalSorties: kpi(30),
      enAttente: kpi(5),
      validees: kpi(10),
      executees: kpi(12),
      rejetees: kpi(2),
      annulees: kpi(1),
      montantTotal: kpi(5000000),
      montantEnAttente: kpi(500000),
      montantValide: kpi(1000000),
      montantExecute: kpi(1200000),
      montantRejete: kpi(200000),
      montantAnnule: kpi(100000),
      montantMoyenParSortie: kpi(166666),
      montantPlusGrosseSortie: 900000,
      tauxExecution: kpi(80),
      tauxRejet: kpi(6.6),
      delaiMoyenTraitementJours: kpi(3.2),
      totalEncaissements: kpi(8000000),
      solde: kpi(3000000),
      ratioSortiesEncaissements: kpi(62.5),
    },
    repartitions: {
      parType: [{ typeCode: 'COMMISSION', nombre: 10, montant: 1000000 }],
      parBeneficiaire: [{ beneficiaire: 'KOUASSI Jean', nombre: 3, montant: 300000 }],
      parStatut: [{ statut: 'DISBURSED', nombre: 12, montant: 1200000 }],
    },
    evolution: [{ periode: '2026-09-01', nombre: 2, montant: 200000 }],
    ...overrides,
  };
}

function assureResponse(overrides: Partial<DashboardAssureResponse> = {}): DashboardAssureResponse {
  return {
    periode: { dateDebut: '2026-09-01', dateFin: '2026-09-30' },
    kpis: {
      totalAssures: kpi(500),
      nouveauxAssures: kpi(20),
      assuresActifs: kpi(480),
      assuresInactifs: kpi(20),
      assuresPlusieursContrats: kpi(90),
      assuresMultiEquipes: kpi(30),
      nombreMoyenContratsParAssure: kpi(1.4),
      tauxCroissance: kpi(4.2),
      tauxMultiEquipement: kpi(6.0),
    },
    repartitions: {
      parVille: [{ ville: 'Abidjan', nombre: 300 }],
      parTypeClient: [{ typeClient: 'PARTICULIER', nombre: 400 }],
    },
    evolution: [{ periode: '2026-09-01', nouveauxAssures: 2, totalAssures: 500 }],
    ...overrides,
  };
}

function globalResponse(overrides: Partial<DashboardGlobalResponse> = {}): DashboardGlobalResponse {
  return {
    periode: { dateDebut: '2026-09-01', dateFin: '2026-09-30' },
    assures: assureResponse().kpis,
    vignettes: vignetteResponse().kpis,
    sortiesFonds: sortieFondsResponse().kpis,
    ...overrides,
  };
}

describe('KpiDashboardComponent — sans utilisateur ADMIN (défaut sûr)', () => {
  let component: KpiDashboardComponent;
  let fixture: ComponentFixture<KpiDashboardComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KpiDashboardComponent],
      providers: [IconSetService, provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const iconSetService = TestBed.inject(IconSetService);
    iconSetService.icons = { ...iconSubset };

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(KpiDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it("isAdmin() est faux et aucun appel réseau n'est émis (évite un 403 prévisible)", () => {
    expect(component.isAdmin()).toBe(false);
    httpMock.expectNone((r) => r.url.startsWith('/dashboard/'));
  });

  it("changer activeView() n'émet pas d'appel réseau non plus pour un non-admin", () => {
    fixture.componentRef.setInput('activeView', 'vignettes');
    fixture.detectChanges();
    httpMock.expectNone((r) => r.url.startsWith('/dashboard/'));
  });
});

describe('KpiDashboardComponent — utilisateur ADMIN', () => {
  let component: KpiDashboardComponent;
  let fixture: ComponentFixture<KpiDashboardComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.setItem(
      'auth_user',
      JSON.stringify({ id: '29', fullName: 'Administrateur AssurZen Core', email: '0757873487', role: 'ADMIN', mustChangePassword: false }),
    );

    await TestBed.configureTestingModule({
      imports: [KpiDashboardComponent],
      providers: [IconSetService, provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const iconSetService = TestBed.inject(IconSetService);
    iconSetService.icons = { ...iconSubset };

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(KpiDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem('auth_user');
  });

  /** activeView() est un input signal piloté depuis le parent (DashboardComponent) — plus de
   * setView() interne, voir le composant. */
  const setView = (view: 'global' | 'vignettes' | 'sortiesFonds' | 'assures') => {
    fixture.componentRef.setInput('activeView', view);
    fixture.detectChanges();
  };

  it('charge le tableau de bord global au démarrage avec la plage de dates par défaut', () => {
    const raw = component.rangeForm.getRawValue();
    const req = httpMock.expectOne(
      (r) => r.url === '/dashboard/global' && r.params.get('dateDebut') === raw.dateDebut && r.params.get('dateFin') === raw.dateFin,
    );
    req.flush(globalResponse());

    expect(component.globalData()).toEqual(globalResponse());
    expect(component.globalAssureCards().length).toBe(4);
    expect(component.globalVignetteCards().length).toBe(4);
    expect(component.globalSortieFondsCards().length).toBe(4);
  });

  it('formate une carte KPI avec variation, et "—" quand variationPourcentage est null', () => {
    httpMock.expectOne((r) => r.url === '/dashboard/global').flush(globalResponse());
    const vignettesCard = component.globalVignetteCards().find((c) => c.key === 'nombreDemandes');
    expect(vignettesCard?.value).toBe('120');
    expect(vignettesCard?.variation).toContain('%');

    const noVariationCard = component.globalVignetteCards().find((c) => c.key === 'vignettesLivrees');
    expect(noVariationCard?.variation).toBe('—');
  });

  it("setView('vignettes') déclenche un seul appel /dashboard/vignettes (mise en cache tant que la plage ne change pas)", () => {
    httpMock.expectOne((r) => r.url === '/dashboard/global').flush(globalResponse());

    setView('vignettes');
    const raw = component.rangeForm.getRawValue();
    const req = httpMock.expectOne((r) => r.url === '/dashboard/vignettes' && r.params.get('dateDebut') === raw.dateDebut);
    req.flush(vignetteResponse());
    expect(component.vignetteData()).toEqual(vignetteResponse());
    expect(component.vignetteCards().length).toBe(19);
    expect(component.vignetteDelaiMoyen()).toBeNull();

    // Un second passage sur le même onglet ne refait pas d'appel — données déjà en cache.
    setView('assures');
    httpMock.expectOne((r) => r.url === '/dashboard/assures').flush(assureResponse());
    setView('vignettes');
    httpMock.expectNone((r) => r.url === '/dashboard/vignettes');
  });

  it('applyRange() vide le cache des 4 vues et recharge la vue active avec la nouvelle plage', () => {
    httpMock.expectOne((r) => r.url === '/dashboard/global').flush(globalResponse());

    component.rangeForm.patchValue({ dateDebut: '2026-08-01', dateFin: '2026-08-31' });
    component.applyRange();
    const req = httpMock.expectOne((r) => r.url === '/dashboard/global' && r.params.get('dateDebut') === '2026-08-01');
    req.flush(globalResponse());
    expect(component.globalData()).toEqual(globalResponse());
  });

  it('construit un donut de répartition uniquement si des éléments existent (null sinon)', () => {
    httpMock.expectOne((r) => r.url === '/dashboard/global').flush(globalResponse());
    setView('sortiesFonds');
    httpMock.expectOne((r) => r.url === '/dashboard/sorties-fonds').flush(sortieFondsResponse({ repartitions: { parType: [], parBeneficiaire: [], parStatut: [] } }));

    expect(component.sortieFondsParStatutChart()).toBeNull();
    expect(component.sortieFondsParStatutLegend()).toEqual([]);
  });

  it('affiche le montant brut de la plus grosse sortie sans le traiter comme un KpiValue', () => {
    httpMock.expectOne((r) => r.url === '/dashboard/global').flush(globalResponse());
    setView('sortiesFonds');
    httpMock.expectOne((r) => r.url === '/dashboard/sorties-fonds').flush(sortieFondsResponse({ kpis: { ...sortieFondsResponse().kpis, montantPlusGrosseSortie: 1234567 } }));

    expect(component.sortieFondsMaxMontant()).toBe(1234567);
  });

  it('classe les répartitions à liste ouverte (zones/bénéficiaires/villes) par volume décroissant', () => {
    httpMock.expectOne((r) => r.url === '/dashboard/global').flush(globalResponse());
    setView('assures');
    httpMock.expectOne((r) => r.url === '/dashboard/assures').flush(
      assureResponse({ repartitions: { parVille: [{ ville: 'Bouaké', nombre: 10 }, { ville: 'Abidjan', nombre: 300 }], parTypeClient: [] } }),
    );

    expect(component.assureParVilleRanked().map((v) => v.ville)).toEqual(['Abidjan', 'Bouaké']);
  });
});
