import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { IconSetService } from '@coreui/icons-angular';
import { Translation, TranslocoLoader, provideTransloco } from '@jsverse/transloco';
import { Observable, of } from 'rxjs';

import { VignettesMotoComponent } from './vignettes-moto.component';
import { iconSubset } from '../../icons/icon-subset';
import {
  DemandeVignetteMotoResponse,
  SimulationTarifVignetteMotoResponse,
  VignetteCommissionResponse,
  VignetteMotoDocumentTypeCode,
  VignetteMotoStatut,
} from '../../core/vignettes-moto/models/vignette-moto.model';
import { GedDocumentResponse } from '../../core/ged/models/ged-document.model';
import { TarifVignetteMotoResponse } from '../../core/vignettes-moto/models/vignette-tarif.model';
import { FraisLivraisonZoneResponse } from '../../core/vignettes-moto/models/vignette-livraison.model';

/** Aucun test ci-dessous n'a besoin d'une traduction réelle — ils vérifient les clés brutes
 * exposées par les signaux, jamais le texte traduit dans le DOM. Un loader factice évite tout
 * appel HTTP/fetch au chargement du scope 'vignettesMoto', qui sinon reste en attente sans jamais
 * être purgé (aucun test ne flush cette requête) et casse le TestBed du test suivant. */
class NoopTranslocoLoader implements TranslocoLoader {
  getTranslation(): Observable<Translation> {
    return of({});
  }
}

function simulationResponse(): SimulationTarifVignetteMotoResponse {
  return {
    categorieAge: '5_10_ANS',
    ageMoto: 6,
    categorieCylindree: 'MOINS_125_CM3',
    montantVignette: 3750,
    fraisOperation: 1500,
    fraisLivraison: 1500,
    fraisAccessoires: 3000,
    montantTotal: 6750,
    reductionApporteur: null,
  };
}

function demandeResponse(overrides: Partial<DemandeVignetteMotoResponse> = {}): DemandeVignetteMotoResponse {
  return {
    id: 1,
    uuid: 'uuid-1',
    reference: 'VGN2026000001',
    typeDemande: 'NOUVELLE_DEMANDE',
    statut: 'BROUILLON',
    nomAssure: 'KOUASSI',
    prenomsAssure: 'Jean',
    telephoneAssure: '0700000000',
    telephoneAssureSecondaire: null,
    adresseLivraison: 'Cocody',
    villeLivraison: 'Abidjan',
    zoneLivraisonCode: 'ABIDJAN',
    marqueMoto: 'Yamaha',
    modeleMoto: 'Crypton',
    numeroChassisMoto: 'XXXXXXXXXXXX',
    cylindreeMoto: 110,
    datePremiereMiseCirculation: '2020-05-15',
    categorieAgeAppliquee: '5_10_ANS',
    categorieCylindreeAppliquee: 'MOINS_125_CM3',
    ageMotoCalcule: 6,
    tarifVignetteMotoId: 1,
    montantVignette: 3750,
    fraisOperation: 1500,
    fraisLivraison: 1500,
    fraisAccessoires: 3000,
    montantTotal: 6750,
    partenaireApporteurId: null,
    partenaireApporteurNom: null,
    commercialApporteurId: null,
    commercialApporteurNom: null,
    reductionApporteur: null,
    motifRejet: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function gedDocResponse(overrides: Partial<GedDocumentResponse> = {}): GedDocumentResponse {
  return {
    id: 99,
    uuid: 'ged-uuid',
    documentNumber: 'GED2026000001',
    documentTypeId: 1,
    documentTypeCode: 'CNI_ASSURE',
    documentTypeNom: "CNI de l'assuré",
    categoryId: null,
    categoryCode: null,
    categoryNom: null,
    title: 'CNI',
    description: null,
    status: 'UPLOADED',
    confidentialityLevel: 'INTERNAL',
    source: null,
    rejectionReason: null,
    checksumSha256: null,
    currentVersionNumber: 1,
    duplicateOfDocumentId: null,
    createdAt: '2026-01-01T00:00:00Z',
    createdBy: null,
    updatedAt: null,
    updatedBy: null,
    archivedAt: null,
    archivedBy: null,
    ...overrides,
  };
}

function tarifResponse(overrides: Partial<TarifVignetteMotoResponse> = {}): TarifVignetteMotoResponse {
  return {
    id: 1,
    categorieAge: '5_10_ANS',
    categorieCylindree: 'MOINS_125_CM3',
    montantVignette: 3750,
    fraisOperation: 1500,
    dateDebutValidite: '2026-01-01',
    dateFinValidite: null,
    actif: true,
    createdAt: '2026-01-01T00:00:00Z',
    createdBy: null,
    updatedAt: null,
    updatedBy: null,
    ...overrides,
  };
}

function livraisonResponse(overrides: Partial<FraisLivraisonZoneResponse> = {}): FraisLivraisonZoneResponse {
  return {
    id: 1,
    code: 'ABIDJAN',
    libelle: 'Abidjan',
    montant: 1500,
    actif: true,
    createdAt: '2026-01-01T00:00:00Z',
    createdBy: null,
    updatedAt: null,
    updatedBy: null,
    ...overrides,
  };
}

function fileSelectedEvent(file: File): Event {
  return { target: { files: [file], value: '' } } as unknown as Event;
}

function seedAssureFields(component: VignettesMotoComponent): void {
  component.demandeForm.patchValue({
    nomAssure: 'Kouassi',
    prenomsAssure: 'Jean',
    telephoneAssure: '0700000000',
    villeLivraison: 'Abidjan',
    adresseLivraison: 'Cocody',
    zoneLivraison: 'ABIDJAN',
  });
}

function seedMotoFields(component: VignettesMotoComponent): void {
  component.demandeForm.patchValue({
    marqueMoto: 'Yamaha',
    modeleMoto: 'Crypton',
    numeroChassisMoto: 'XXXXXXXXXXXX',
    cylindreeMoto: '110',
    datePremiereMiseCirculation: '2020-05-15',
  });
}

function seedValidForm(component: VignettesMotoComponent): void {
  seedAssureFields(component);
  seedMotoFields(component);
}

describe('VignettesMotoComponent', () => {
  let component: VignettesMotoComponent;
  let fixture: ComponentFixture<VignettesMotoComponent>;
  let httpMock: HttpTestingController;

  const flushEmptyDemandesList = () => {
    httpMock
      .expectOne((r) => r.url === '/vignettes-moto/demandes' && r.method === 'GET')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
  };

  /** Ouvre le parcours de création et solde les GET déclenchés au passage (openCreateDemande()
   * recharge zones de livraison + partenaires + commerciaux si les listes sont vides, ce qui est
   * le cas au premier test). */
  const openWizard = () => {
    component.openCreateDemande();
    httpMock
      .expectOne((r) => r.url === '/vignettes-moto/livraisons')
      .flush([{ id: 1, code: 'ABIDJAN', libelle: 'Abidjan', montant: 1500, actif: true, createdAt: '', createdBy: null, updatedAt: null, updatedBy: null }]);
    httpMock.expectOne((r) => r.url === '/partenaires').flush({ content: [], page: 0, size: 1000, totalElements: 0, totalPages: 1 });
    httpMock.expectOne((r) => r.url === '/commercials').flush({ content: [], page: 0, size: 500, totalElements: 0, totalPages: 1 });
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VignettesMotoComponent],
      providers: [
        IconSetService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['fr', 'en', 'es', 'ru'], defaultLang: 'fr', fallbackLang: 'fr', prodMode: true },
          loader: NoopTranslocoLoader,
        }),
      ],
    }).compileComponents();

    const iconSetService = TestBed.inject(IconSetService);
    iconSetService.icons = { ...iconSubset };

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(VignettesMotoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    flushEmptyDemandesList();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('parcours de création — validation par étape', () => {
    beforeEach(() => openWizard());

    it("bloque l'étape Assuré si des champs obligatoires manquent", () => {
      component.currentStep.set(1);
      component.nextStep();
      expect(component.wizardError()).toBe('vignettesMoto.errors.champsAssureObligatoires');
      expect(component.currentStep()).toBe(1);
    });

    it("laisse passer l'étape Assuré une fois tous les champs remplis", () => {
      component.currentStep.set(1);
      seedAssureFields(component);
      component.nextStep();
      expect(component.wizardError()).toBeNull();
      expect(component.currentStep()).toBe(2);
    });

    it("bloque l'étape Assuré si le téléphone est manquant (obligatoire pour le SMS de suivi)", () => {
      component.currentStep.set(1);
      seedAssureFields(component);
      component.demandeForm.controls.telephoneAssure.setValue('');
      component.nextStep();
      expect(component.wizardError()).toBe('vignettesMoto.errors.champsAssureObligatoires');
      expect(component.currentStep()).toBe(1);
    });

    it('rejette un téléphone de plus de 20 caractères', () => {
      component.currentStep.set(1);
      seedAssureFields(component);
      component.demandeForm.controls.telephoneAssure.setValue('0'.repeat(21));
      component.nextStep();
      expect(component.wizardError()).toBe('vignettesMoto.errors.telephoneTropLong');
      expect(component.currentStep()).toBe(1);
    });

    it('rejette une cylindrée à 0', () => {
      component.currentStep.set(2);
      seedMotoFields(component);
      component.demandeForm.controls.cylindreeMoto.setValue('0');
      component.nextStep();
      expect(component.wizardError()).toBe('vignettesMoto.errors.cylindreeInvalide');
      expect(component.currentStep()).toBe(2);
    });

    it('rejette une date de première mise en circulation dans le futur', () => {
      component.currentStep.set(2);
      seedMotoFields(component);
      const future = new Date();
      future.setFullYear(future.getFullYear() + 1);
      component.demandeForm.controls.datePremiereMiseCirculation.setValue(future.toISOString().slice(0, 10));
      component.nextStep();
      expect(component.wizardError()).toBe('vignettesMoto.errors.dateCirculationFutur');
      expect(component.currentStep()).toBe(2);
    });

    it("laisse passer l'étape Moto avec des données valides", () => {
      component.currentStep.set(2);
      seedMotoFields(component);
      component.nextStep();
      expect(component.wizardError()).toBeNull();
      expect(component.currentStep()).toBe(3);
    });

    it("bloque le passage au Récapitulatif tant qu'aucun tarif n'a été calculé", () => {
      component.currentStep.set(4);
      component.nextStep();
      expect(component.wizardError()).toBe('vignettesMoto.errors.calculerTarifAvant');
      expect(component.currentStep()).toBe(4);
    });

    it('verrouille la navigation une fois la demande créée', () => {
      seedValidForm(component);
      component.currentStep.set(5);
      component.confirmerDemande();
      httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes' && r.method === 'POST').flush(demandeResponse());

      component.prevStep();
      expect(component.currentStep()).toBe(6);
      component.goToStep(1);
      expect(component.currentStep()).toBe(6);
    });
  });

  describe('étape Origine — le message d’erreur ne survit pas à une action corrective', () => {
    beforeEach(() => openWizard());

    it('choisir un partenaire apporteur efface l’erreur « sélectionnez un partenaire »', () => {
      component.setOrigineType('partenaire');
      component.wizardError.set('vignettesMoto.errors.apporteurPartenaireObligatoire');
      component.selectPartenaireApporteur(5);
      expect(component.wizardError()).toBeNull();
    });

    it('choisir un commercial apporteur ou changer de type d’origine efface aussi l’erreur', () => {
      component.setOrigineType('commercial');
      component.wizardError.set('vignettesMoto.errors.apporteurCommercialObligatoire');
      component.selectCommercialApporteur(7);
      expect(component.wizardError()).toBeNull();

      component.wizardError.set('vignettesMoto.errors.apporteurCommercialObligatoire');
      component.setOrigineType('aucun');
      expect(component.wizardError()).toBeNull();
    });
  });

  describe('âge approximatif (affichage uniquement, jamais envoyé au backend)', () => {
    it('calcule un âge en années entières à partir de la date de mise en circulation', () => {
      const fiveYearsAgo = new Date();
      fiveYearsAgo.setFullYear(fiveYearsAgo.getFullYear() - 5);
      fiveYearsAgo.setDate(fiveYearsAgo.getDate() - 1);
      component.demandeForm.controls.datePremiereMiseCirculation.setValue(fiveYearsAgo.toISOString().slice(0, 10));
      expect(component.ageApproximatif()).toBe(5);
    });

    it("renvoie null si aucune date n'est saisie", () => {
      component.demandeForm.controls.datePremiereMiseCirculation.setValue('');
      expect(component.ageApproximatif()).toBeNull();
    });
  });

  describe('simulation tarifaire', () => {
    beforeEach(() => {
      openWizard();
      seedMotoFields(component);
      component.demandeForm.controls.zoneLivraison.setValue('ABIDJAN');
    });

    it('remplit simulationResult avec la réponse du backend', () => {
      component.simulerTarif();
      const req = httpMock.expectOne('/vignettes-moto/tarification/simuler');
      expect(req.request.body).toEqual({
        datePremiereMiseCirculation: '2020-05-15',
        cylindree: 110,
        zoneLivraison: 'ABIDJAN',
      });
      req.flush(simulationResponse());
      expect(component.simulationResult()).toEqual(simulationResponse());
      expect(component.simulationError()).toBeNull();
    });

    it('affiche le message backend sur 404 (ex. apporteur introuvable dès la simulation)', () => {
      component.simulerTarif();
      const req = httpMock.expectOne('/vignettes-moto/tarification/simuler');
      req.flush({ message: 'Partenaire apporteur introuvable: 99' }, { status: 404, statusText: 'Not Found' });
      expect(component.simulationError()).toBe('Partenaire apporteur introuvable: 99');
      expect(component.simulationResult()).toBeNull();
    });

    it('retombe sur un message compréhensible (clé i18n) sur 404 sans message backend', () => {
      component.simulerTarif();
      const req = httpMock.expectOne('/vignettes-moto/tarification/simuler');
      req.flush(null, { status: 404, statusText: 'Not Found' });
      expect(component.simulationError()).toBe('vignettesMoto.errors.calculTarifIntrouvable');
      expect(component.simulationResult()).toBeNull();
    });

    it("n'appelle pas le backend si les champs moto sont invalides", () => {
      component.demandeForm.controls.cylindreeMoto.setValue('0');
      component.simulerTarif();
      httpMock.expectNone('/vignettes-moto/tarification/simuler');
      expect(component.wizardError()).toBe('vignettesMoto.errors.cylindreeInvalide');
    });
  });

  describe('création de la demande', () => {
    beforeEach(() => {
      openWizard();
      seedValidForm(component);
    });

    it('POST un payload plat (pas assure{}/moto{} imbriqués) et passe à l’étape Documents', () => {
      component.confirmerDemande();
      const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes' && r.method === 'POST');
      expect((req.request.body as Record<string, unknown>)['assure']).toBeUndefined();
      expect((req.request.body as Record<string, unknown>)['telephoneAssure']).toBe('0700000000');
      req.flush(demandeResponse());

      expect(component.createdDemande()?.reference).toBe('VGN2026000001');
      expect(component.currentStep()).toBe(6);
    });

    it('propose les 5 documents requis pour une nouvelle demande (2026-09-19)', () => {
      component.demandeForm.controls.typeDemande.setValue('NOUVELLE_DEMANDE');
      component.confirmerDemande();
      httpMock.expectOne((r) => r.method === 'POST').flush(demandeResponse({ typeDemande: 'NOUVELLE_DEMANDE' }));
      expect(component.documentSlots().map((s) => s.typeCode)).toEqual([
        'CNI_ASSURE',
        'RECU_ACHAT_MOTO',
        'PHOTO_MOTO',
        'PHOTO_CHASSIS',
        'ASSURANCE',
      ]);
    });

    it("ajoute l'ancienne vignette aux documents attendus pour un renouvellement", () => {
      component.demandeForm.controls.typeDemande.setValue('RENOUVELLEMENT');
      component.confirmerDemande();
      httpMock.expectOne((r) => r.method === 'POST').flush(demandeResponse({ typeDemande: 'RENOUVELLEMENT' }));
      expect(component.documentSlots().map((s) => s.typeCode)).toEqual(['CNI_ASSURE', 'RECU_ACHAT_MOTO', 'ANCIENNE_VIGNETTE']);
    });

    it("affiche un message clair si aucun tarif n'est trouvé (404) — la demande n'est pas créée", () => {
      component.confirmerDemande();
      const req = httpMock.expectOne((r) => r.method === 'POST');
      req.flush({ message: 'x' }, { status: 404, statusText: 'Not Found' });
      expect(component.wizardError()).toBe('vignettesMoto.errors.tarifIntrouvable');
      expect(component.createdDemande()).toBeNull();
      expect(component.currentStep()).not.toBe(6);
    });
  });

  describe('dépôt de documents (après création)', () => {
    beforeEach(() => {
      openWizard();
      seedValidForm(component);
      component.confirmerDemande();
      httpMock.expectOne((r) => r.method === 'POST' && r.url === '/vignettes-moto/demandes').flush(demandeResponse());
    });

    it('met à jour le slot correspondant une fois le document déposé', () => {
      const file = new File(['contenu'], 'cni.pdf', { type: 'application/pdf' });
      component.onDocumentSelected('CNI_ASSURE', fileSelectedEvent(file));

      const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/documents');
      expect(req.request.params.get('typeCode')).toBe('CNI_ASSURE');
      req.flush(gedDocResponse());

      const slot = component.documentSlots().find((s) => s.typeCode === 'CNI_ASSURE');
      expect(slot?.uploaded?.documentNumber).toBe('GED2026000001');
      expect(slot?.uploading).toBe(false);
      expect(slot?.error).toBeNull();
    });

    it('affiche un message clair sur un format de fichier refusé (415)', () => {
      const file = new File(['contenu'], 'cni.exe');
      component.onDocumentSelected('CNI_ASSURE', fileSelectedEvent(file));

      const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/documents');
      req.flush({ message: 'unsupported' }, { status: 415, statusText: 'Unsupported Media Type' });

      const slot = component.documentSlots().find((s) => s.typeCode === 'CNI_ASSURE');
      expect(slot?.error).toBe('vignettesMoto.errors.formatNonAccepte');
      expect(slot?.uploading).toBe(false);
    });
  });

  describe('soumission de la demande', () => {
    beforeEach(() => {
      openWizard();
      seedValidForm(component);
      component.confirmerDemande();
      httpMock.expectOne((r) => r.method === 'POST' && r.url === '/vignettes-moto/demandes').flush(demandeResponse());
    });

    it('affiche le message backend tel quel sur 409 (documents manquants)', () => {
      component.soumettreDemande();
      const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/soumettre');
      req.flush(
        { code: '409 CONFLICT', message: 'Document(s) obligatoire(s) manquant(s) avant soumission: CNI_ASSURE, RECU_ACHAT_MOTO' },
        { status: 409, statusText: 'Conflict' },
      );
      expect(component.submitError()).toBe('Document(s) obligatoire(s) manquant(s) avant soumission: CNI_ASSURE, RECU_ACHAT_MOTO');
      expect(component.submitted()).toBe(false);
    });

    it('marque la demande comme soumise en cas de succès', () => {
      component.soumettreDemande();
      const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/soumettre');
      req.flush(demandeResponse({ statut: 'EN_ATTENTE_PAIEMENT' }));
      expect(component.submitted()).toBe(true);
      expect(component.createdDemande()?.statut).toBe('EN_ATTENTE_PAIEMENT');
    });
  });

  describe('suivi de statut', () => {
    it('statutVariant mappe chaque statut vers une variante sémantique cohérente', () => {
      expect(component.statutVariant('LIVREE')).toBe('success');
      expect(component.statutVariant('ANNULEE')).toBe('danger');
      expect(component.statutVariant('REJETEE')).toBe('danger');
      expect(component.statutVariant('BROUILLON')).toBe('neutral');
    });

    it('statutMilestoneIndex renvoie une étape active pour BROUILLON (régression : 0 est faux pour @if)', () => {
      const milestone = component.statutMilestoneIndex('BROUILLON');
      expect(milestone).not.toBeNull();
      expect(milestone).toBeGreaterThan(0);
    });

    it('statutMilestoneIndex renvoie null pour les statuts hors parcours linéaire (annulée/rejetée)', () => {
      expect(component.statutMilestoneIndex('ANNULEE')).toBeNull();
      expect(component.statutMilestoneIndex('REJETEE')).toBeNull();
    });

    it('statutMilestoneIndex progresse dans le bon ordre au fil du cycle de vie', () => {
      const order: VignetteMotoStatut[] = ['BROUILLON', 'EN_ATTENTE_PAIEMENT', 'PAYEE', 'EN_EDITION', 'EN_LIVRAISON', 'LIVREE'];
      const indices = order.map((s) => component.statutMilestoneIndex(s) as number);
      for (let i = 1; i < indices.length; i++) {
        expect(indices[i]).toBeGreaterThanOrEqual(indices[i - 1]);
      }
    });
  });

  describe('actions autorisées selon le statut', () => {
    it('canSubmitDemande uniquement pour BROUILLON', () => {
      expect(component.canSubmitDemande('BROUILLON')).toBe(true);
      expect(component.canSubmitDemande('EN_ATTENTE_PAIEMENT')).toBe(false);
    });

    it('canCancelDemande exclut LIVREE/ANNULEE/REJETEE', () => {
      expect(component.canCancelDemande('EN_TRAITEMENT')).toBe(true);
      expect(component.canCancelDemande('LIVREE')).toBe(false);
      expect(component.canCancelDemande('ANNULEE')).toBe(false);
      expect(component.canCancelDemande('REJETEE')).toBe(false);
    });

    it('canRejectDemande est réservé ADMIN — un non-admin ne peut pas rejeter sa propre demande', () => {
      expect(component.isAdmin()).toBe(false);
      expect(component.canRejectDemande('BROUILLON')).toBe(false);
      expect(component.canRejectDemande('EN_ATTENTE_PAIEMENT')).toBe(false);
    });
  });

  describe('Parcours d’intégration — bout en bout (composant + HttpTestingController, sans mock de service)', () => {
    it('nouvelle demande : simulation → création → 2 documents → soumission réussie', () => {
      openWizard();
      seedValidForm(component);

      component.simulerTarif();
      httpMock.expectOne('/vignettes-moto/tarification/simuler').flush(simulationResponse());
      expect(component.simulationResult()).toEqual(simulationResponse());

      component.confirmerDemande();
      httpMock.expectOne((r) => r.method === 'POST' && r.url === '/vignettes-moto/demandes').flush(demandeResponse());
      expect(component.currentStep()).toBe(6);
      const expectedTypeCodes: VignetteMotoDocumentTypeCode[] = ['CNI_ASSURE', 'RECU_ACHAT_MOTO', 'PHOTO_MOTO', 'PHOTO_CHASSIS', 'ASSURANCE'];
      expect(component.documentSlots().map((s) => s.typeCode)).toEqual(expectedTypeCodes);

      for (const typeCode of expectedTypeCodes) {
        component.onDocumentSelected(typeCode, fileSelectedEvent(new File(['x'], `${typeCode}.pdf`)));
        httpMock
          .expectOne((r) => r.url === '/vignettes-moto/demandes/1/documents' && r.params.get('typeCode') === typeCode)
          .flush(gedDocResponse({ documentTypeCode: typeCode }));
      }

      expect(component.documentSlots().every((s) => !!s.uploaded)).toBe(true);

      component.soumettreDemande();
      httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/soumettre').flush(demandeResponse({ statut: 'EN_ATTENTE_PAIEMENT' }));
      expect(component.submitted()).toBe(true);
      expect(component.createdDemande()?.statut).toBe('EN_ATTENTE_PAIEMENT');
    });

    it("renouvellement : soumission bloquée (409) tant que l'ancienne vignette manque, puis réussit une fois les 3 documents déposés", () => {
      openWizard();
      seedValidForm(component);
      component.demandeForm.controls.typeDemande.setValue('RENOUVELLEMENT');

      component.confirmerDemande();
      httpMock.expectOne((r) => r.method === 'POST' && r.url === '/vignettes-moto/demandes').flush(demandeResponse({ typeDemande: 'RENOUVELLEMENT' }));
      expect(component.documentSlots().map((s) => s.typeCode)).toEqual(['CNI_ASSURE', 'RECU_ACHAT_MOTO', 'ANCIENNE_VIGNETTE']);

      component.soumettreDemande();
      httpMock
        .expectOne((r) => r.url === '/vignettes-moto/demandes/1/soumettre')
        .flush(
          { message: 'Document(s) obligatoire(s) manquant(s) avant soumission: ANCIENNE_VIGNETTE' },
          { status: 409, statusText: 'Conflict' },
        );
      expect(component.submitError()).toContain('ANCIENNE_VIGNETTE');
      expect(component.submitted()).toBe(false);

      const typeCodes: VignetteMotoDocumentTypeCode[] = ['CNI_ASSURE', 'RECU_ACHAT_MOTO', 'ANCIENNE_VIGNETTE'];
      for (const typeCode of typeCodes) {
        component.onDocumentSelected(typeCode, fileSelectedEvent(new File(['x'], `${typeCode}.pdf`)));
        httpMock.expectOne((r) => r.params.get('typeCode') === typeCode).flush(gedDocResponse({ documentTypeCode: typeCode }));
      }

      component.soumettreDemande();
      httpMock
        .expectOne((r) => r.url === '/vignettes-moto/demandes/1/soumettre')
        .flush(demandeResponse({ typeDemande: 'RENOUVELLEMENT', statut: 'EN_ATTENTE_PAIEMENT' }));
      expect(component.submitted()).toBe(true);
    });

    it('depuis la liste : ouvrir le détail, déposer le document manquant, soumettre — la ligne de la liste se met à jour', () => {
      const existing = demandeResponse({ id: 5, reference: 'VGN2026000005', statut: 'BROUILLON' });
      component.demandes.set([existing]);

      component.openDetail(existing);
      httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/5/documents').flush({
        content: [gedDocResponse({ documentTypeCode: 'CNI_ASSURE' })],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      });
      expect(component.detailDocuments().length).toBe(1);

      component.newDocTypeCode.set('RECU_ACHAT_MOTO');
      component.onDetailDocSelected(fileSelectedEvent(new File(['x'], 'recu.pdf')));
      const uploadReq = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/5/documents');
      expect(uploadReq.request.params.get('typeCode')).toBe('RECU_ACHAT_MOTO');
      uploadReq.flush(gedDocResponse({ id: 101, documentTypeCode: 'RECU_ACHAT_MOTO' }));
      // uploadDetailDocument recharge la liste des documents après un dépôt réussi :
      httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/5/documents').flush({
        content: [gedDocResponse({ documentTypeCode: 'CNI_ASSURE' }), gedDocResponse({ id: 101, documentTypeCode: 'RECU_ACHAT_MOTO' })],
        page: 0,
        size: 50,
        totalElements: 2,
        totalPages: 1,
      });
      expect(component.detailDocuments().length).toBe(2);

      component.doSoumettreDetail();
      httpMock
        .expectOne((r) => r.url === '/vignettes-moto/demandes/5/soumettre')
        .flush(demandeResponse({ id: 5, reference: 'VGN2026000005', statut: 'EN_ATTENTE_PAIEMENT' }));

      expect(component.selectedDemande()?.statut).toBe('EN_ATTENTE_PAIEMENT');
      expect(component.demandes().find((d) => d.id === 5)?.statut).toBe('EN_ATTENTE_PAIEMENT');
    });

    it('téléchargement de document depuis le détail utilise le endpoint scopé /demandes/{id}/documents/{documentId}/download (pas le GED générique, ADMIN-only depuis le 2026-09-12)', () => {
      const existing = demandeResponse({ id: 5, reference: 'VGN2026000005', statut: 'BROUILLON' });
      component.demandes.set([existing]);
      component.openDetail(existing);
      const doc = gedDocResponse({ id: 101, documentTypeCode: 'CNI_ASSURE', documentNumber: 'GED2026000101' });
      httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/5/documents').flush({ content: [doc], page: 0, size: 50, totalElements: 1, totalPages: 1 });

      component.downloadDetailDocument(doc);
      const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/5/documents/101/download');
      expect(req.request.method).toBe('GET');
      req.flush(new Blob(['contenu']));
      expect(component.detailActionError()).toBeNull();
    });

    it('rejet depuis le détail : la liste reflète le nouveau statut et le motif', () => {
      const existing = demandeResponse({ id: 6, reference: 'VGN2026000006', statut: 'EN_ATTENTE_PAIEMENT' });
      component.demandes.set([existing]);
      component.openDetail(existing);
      httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/6/documents').flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 1 });

      component.askRejectDemande();
      expect(component.rejectModalVisible()).toBe(true);
      component.rejectForm.controls.motif.setValue('Pièces illisibles');
      component.submitRejectDemande();
      httpMock
        .expectOne((r) => r.url === '/vignettes-moto/demandes/6/rejeter')
        .flush(demandeResponse({ id: 6, reference: 'VGN2026000006', statut: 'REJETEE', motifRejet: 'Pièces illisibles' }));

      expect(component.rejectModalVisible()).toBe(false);
      expect(component.demandes().find((d) => d.id === 6)?.statut).toBe('REJETEE');
      expect(component.selectedDemande()?.motifRejet).toBe('Pièces illisibles');
    });

    it('annulation depuis le détail : confirmation → mise à jour de la liste', () => {
      const existing = demandeResponse({ id: 7, reference: 'VGN2026000007', statut: 'EN_TRAITEMENT' });
      component.demandes.set([existing]);
      component.openDetail(existing);
      httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/7/documents').flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 1 });

      component.askCancelDemande();
      expect(component.cancelTarget()?.id).toBe(7);
      component.confirmCancelDemande();
      httpMock
        .expectOne((r) => r.url === '/vignettes-moto/demandes/7/annuler')
        .flush(demandeResponse({ id: 7, reference: 'VGN2026000007', statut: 'ANNULEE' }));

      expect(component.cancelTarget()).toBeNull();
      expect(component.demandes().find((d) => d.id === 7)?.statut).toBe('ANNULEE');
    });
  });

});

describe('VignettesMotoComponent — sans utilisateur ADMIN (Tarifs/Livraisons réservés, Demandes reste self-service)', () => {
  let component: VignettesMotoComponent;
  let fixture: ComponentFixture<VignettesMotoComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VignettesMotoComponent],
      providers: [
        IconSetService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['fr', 'en', 'es', 'ru'], defaultLang: 'fr', fallbackLang: 'fr', prodMode: true },
          loader: NoopTranslocoLoader,
        }),
      ],
    }).compileComponents();

    const iconSetService = TestBed.inject(IconSetService);
    iconSetService.icons = { ...iconSubset };

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(VignettesMotoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    // Demandes reste ouvert à tout compte authentifié (owner-scoping côté backend depuis le
    // durcissement IDOR du 2026-09-12, pas ADMIN-only) — le fetch initial se déclenche normalement.
    httpMock
      .expectOne((r) => r.url === '/vignettes-moto/demandes' && r.method === 'GET')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('isAdmin est faux sans utilisateur connecté (défaut sûr : Tarifs/Livraisons masqués)', () => {
    expect(component.isAdmin()).toBe(false);
  });

  it('setView ignore les vues tarifs/livraisons pour un non-admin', () => {
    component.setView('tarifs');
    expect(component.view()).toBe('demandes');
    component.setView('livraisons');
    expect(component.view()).toBe('demandes');
    component.setView('commissions');
    expect(component.view()).toBe('demandes');
  });
});

describe('VignettesMotoComponent — utilisateur ADMIN', () => {
  let adminComponent: VignettesMotoComponent;
  let adminFixture: ComponentFixture<VignettesMotoComponent>;
  let adminHttpMock: HttpTestingController;

  beforeEach(async () => {
    // AuthService lit tokenStorage.getUser() (localStorage 'auth_user') à sa construction —
    // il faut le poser avant TestBed.createComponent(), qui déclenche l'injection. Valeur
    // vérifiée en direct sur le compte "Administrateur AssurZen Core" : role === "ADMIN".
    localStorage.setItem(
      'auth_user',
      JSON.stringify({ id: '29', fullName: 'Administrateur AssurZen Core', email: '0757873487', role: 'ADMIN', mustChangePassword: false }),
    );

    await TestBed.configureTestingModule({
      imports: [VignettesMotoComponent],
      providers: [
        IconSetService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['fr', 'en', 'es', 'ru'], defaultLang: 'fr', fallbackLang: 'fr', prodMode: true },
          loader: NoopTranslocoLoader,
        }),
      ],
    }).compileComponents();

    const iconSetService = TestBed.inject(IconSetService);
    iconSetService.icons = { ...iconSubset };

    adminHttpMock = TestBed.inject(HttpTestingController);
    adminFixture = TestBed.createComponent(VignettesMotoComponent);
    adminComponent = adminFixture.componentInstance;
    adminFixture.detectChanges();
    adminHttpMock
      .expectOne((r) => r.url === '/vignettes-moto/demandes' && r.method === 'GET')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
  });

  afterEach(() => {
    adminHttpMock.verify();
    localStorage.removeItem('auth_user');
  });

  it('isAdmin est vrai quand AuthUser.role === "ADMIN"', () => {
    expect(adminComponent.isAdmin()).toBe(true);
  });

  it('setView autorise les vues tarifs/livraisons pour un admin', () => {
    adminComponent.setView('tarifs');
    expect(adminComponent.view()).toBe('tarifs');
    adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/tarifs').flush([]);

    adminComponent.setView('livraisons');
    expect(adminComponent.view()).toBe('livraisons');
    adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/livraisons').flush([]);
  });

  it('canRejectDemande autorise un admin pour BROUILLON/EN_ATTENTE_PAIEMENT uniquement', () => {
    expect(adminComponent.canRejectDemande('BROUILLON')).toBe(true);
    expect(adminComponent.canRejectDemande('EN_ATTENTE_PAIEMENT')).toBe(true);
    expect(adminComponent.canRejectDemande('PAYEE')).toBe(false);
  });

  describe('commissions apporteur (livré 2026-09-19, ADMIN)', () => {
    const commissionsUrl = (r: { url: string; method: string }, method = 'GET') =>
      r.url === '/vignettes-moto/commissions' && r.method === method;

    function commission(overrides: Partial<VignetteCommissionResponse> = {}): VignetteCommissionResponse {
      return {
        id: 1,
        demandeVignetteMotoId: 10,
        demandeVignetteMotoReference: 'VIG-2026-0010',
        partenaireId: 5,
        partenaireNom: 'Partenaire Test',
        commercialId: null,
        commercialNom: null,
        montant: 500,
        statut: 'pending',
        paidByUserId: null,
        paidByUsername: null,
        reference: null,
        paidAt: null,
        createdAt: '2026-09-19T08:00:00Z',
        notificationEnvoyee: null,
        ...overrides,
      };
    }

    function openCommissions(content: VignetteCommissionResponse[]): void {
      adminComponent.setView('commissions');
      const req = adminHttpMock.expectOne((r) => commissionsUrl(r));
      req.flush({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
    }

    it('setView("commissions") charge la liste filtrée sur "pending" par défaut', () => {
      adminComponent.setView('commissions');
      expect(adminComponent.view()).toBe('commissions');
      const req = adminHttpMock.expectOne((r) => commissionsUrl(r));
      expect(req.request.params.get('statut')).toBe('pending');
      expect(req.request.params.get('page')).toBe('0');
      req.flush({ content: [commission()], page: 0, size: 20, totalElements: 1, totalPages: 1 });
      expect(adminComponent.commissions().length).toBe(1);
      expect(adminComponent.commissionsLoading()).toBe(false);
    });

    it('le filtre "all" n’envoie aucun paramètre statut', () => {
      openCommissions([]);
      adminComponent.setCommissionStatutFilter('all');
      const req = adminHttpMock.expectOne((r) => commissionsUrl(r));
      expect(req.request.params.has('statut')).toBe(false);
      req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
    });

    it('expose une clé d’erreur si le chargement échoue', () => {
      adminComponent.setView('commissions');
      adminHttpMock.expectOne((r) => commissionsUrl(r)).flush(null, { status: 500, statusText: 'Server Error' });
      expect(adminComponent.commissionsError()).toBe('vignettesMoto.errors.chargementCommissions');
      expect(adminComponent.commissionsLoading()).toBe(false);
    });

    it('la sélection ne porte que sur les commissions en attente et le total suit la sélection', () => {
      openCommissions([commission({ id: 1, montant: 500 }), commission({ id: 2, montant: 1000 }), commission({ id: 3, statut: 'paid', montant: 500 })]);
      expect(adminComponent.pendingCommissions().map((c) => c.id)).toEqual([1, 2]);

      adminComponent.toggleCommission(2);
      expect(adminComponent.selectedCommissionsTotal()).toBe(1000);
      expect(adminComponent.allPendingCommissionsSelected()).toBe(false);

      adminComponent.toggleAllPendingCommissions();
      expect([...adminComponent.selectedCommissionIds()].sort()).toEqual([1, 2]);
      expect(adminComponent.selectedCommissionsTotal()).toBe(1500);
      expect(adminComponent.allPendingCommissionsSelected()).toBe(true);

      adminComponent.toggleAllPendingCommissions();
      expect(adminComponent.selectedCommissionIds().size).toBe(0);
    });

    it('changer de filtre vide la sélection', () => {
      openCommissions([commission({ id: 1 })]);
      adminComponent.toggleCommission(1);
      adminComponent.setCommissionStatutFilter('paid');
      adminHttpMock.expectOne((r) => commissionsUrl(r)).flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
      expect(adminComponent.selectedCommissionIds().size).toBe(0);
    });

    it('le bandeau de résultat de paiement disparaît au changement de filtre', () => {
      openCommissions([commission()]);
      adminComponent.commissionPaymentResult.set({ count: 1, notificationsEchouees: 0 });
      adminComponent.setCommissionStatutFilter('paid');
      adminHttpMock.expectOne((r) => commissionsUrl(r)).flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
      expect(adminComponent.commissionPaymentResult()).toBeNull();
    });

    it('askPayCommissions n’ouvre pas la modale sans sélection', () => {
      openCommissions([commission()]);
      adminComponent.askPayCommissions();
      expect(adminComponent.payCommissionsModalVisible()).toBe(false);
    });

    it('submitPayCommissions exige une référence de paiement (aucun appel réseau sans elle)', () => {
      openCommissions([commission()]);
      adminComponent.toggleCommission(1);
      adminComponent.askPayCommissions();
      expect(adminComponent.payCommissionsModalVisible()).toBe(true);

      adminComponent.payCommissionsForm.controls.reference.setValue('   ');
      adminComponent.submitPayCommissions();
      expect(adminComponent.payCommissionsError()).toBe('vignettesMoto.errors.referencePaiementObligatoire');
      adminHttpMock.expectNone((r) => r.url === '/vignettes-moto/commissions/payer');
    });

    it('submitPayCommissions poste sur /payer, résume les SMS échoués et recharge la liste', () => {
      openCommissions([commission({ id: 1 }), commission({ id: 2 })]);
      adminComponent.toggleAllPendingCommissions();
      adminComponent.askPayCommissions();
      adminComponent.payCommissionsForm.controls.reference.setValue('  VIR-31/07  ');
      adminComponent.submitPayCommissions();

      const req = adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/commissions/payer' && r.method === 'POST');
      expect(req.request.body).toEqual({ commissionIds: [1, 2], reference: 'VIR-31/07' });
      req.flush([
        commission({ id: 1, statut: 'paid', reference: 'VIR-31/07', notificationEnvoyee: true }),
        commission({ id: 2, statut: 'paid', reference: 'VIR-31/07', notificationEnvoyee: false }),
      ]);

      expect(adminComponent.payCommissionsModalVisible()).toBe(false);
      expect(adminComponent.selectedCommissionIds().size).toBe(0);
      expect(adminComponent.commissionPaymentResult()).toEqual({ count: 2, notificationsEchouees: 1 });
      adminHttpMock.expectOne((r) => commissionsUrl(r)).flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
    });

    it('submitPayCommissions garde la modale ouverte et affiche une erreur si le backend refuse', () => {
      openCommissions([commission()]);
      adminComponent.toggleCommission(1);
      adminComponent.askPayCommissions();
      adminComponent.payCommissionsForm.controls.reference.setValue('VIR-1');
      adminComponent.submitPayCommissions();
      adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/commissions/payer').flush(null, { status: 409, statusText: 'Conflict' });

      expect(adminComponent.payCommissionsModalVisible()).toBe(true);
      expect(adminComponent.payCommissionsError()).toBe('vignettesMoto.errors.paiementCommissions');
      expect(adminComponent.payCommissionsSaving()).toBe(false);
      expect(adminComponent.selectedCommissionIds().size).toBe(1);
    });
  });

  describe('suivi d’évolution + lien SMS (livré 2026-09-17, ADMIN)', () => {
    it('statutsCiblesDisponibles suit la matrice backend — BROUILLON/ANNULEE/LIVREE/REJETEE restent hors de POST /statut', () => {
      expect(adminComponent.statutsCiblesDisponibles('EN_ATTENTE_PAIEMENT')).toEqual(['PAYEE', 'REJETEE']);
      expect(adminComponent.statutsCiblesDisponibles('PAYEE')).toEqual(['EN_TRAITEMENT', 'REJETEE']);
      expect(adminComponent.statutsCiblesDisponibles('EN_LIVRAISON')).toEqual(['LIVREE', 'REJETEE']);
      expect(adminComponent.statutsCiblesDisponibles('BROUILLON')).toEqual([]);
      expect(adminComponent.statutsCiblesDisponibles('LIVREE')).toEqual([]);
      expect(adminComponent.statutsCiblesDisponibles('REJETEE')).toEqual([]);
    });

    it('openDetail charge /historique pour un admin', () => {
      adminComponent.openDetail(demandeResponse({ statut: 'PAYEE' }));
      adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/documents').flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 1 });

      const req = adminHttpMock.expectOne('/vignettes-moto/demandes/1/historique');
      req.flush([
        { ancienStatut: 'EN_ATTENTE_PAIEMENT', nouveauStatut: 'PAYEE', motif: null, referenceOperation: 'OP-1', auteurNom: 'Admin', notificationEnvoyee: true, createdAt: '2026-09-17T09:00:00Z' },
      ]);
      expect(adminComponent.historique().length).toBe(1);
      expect(adminComponent.historique()[0].notificationEnvoyee).toBe(true);
    });

    it('la timeline trie du plus récent au plus ancien, montre 3 entrées et se déplie à la demande', () => {
      adminComponent.openDetail(demandeResponse({ statut: 'LIVREE' }));
      adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/documents').flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 1 });
      const entry = (nouveauStatut: string, createdAt: string) => ({ ancienStatut: null, nouveauStatut, motif: null, referenceOperation: null, auteurNom: 'Admin', notificationEnvoyee: true, createdAt });
      // Volontairement désordonné dans la réponse backend.
      adminHttpMock.expectOne('/vignettes-moto/demandes/1/historique').flush([
        entry('PAYEE', '2026-09-17T09:01:00Z'),
        entry('LIVREE', '2026-09-17T09:05:00Z'),
        entry('EN_TRAITEMENT', '2026-09-17T09:02:00Z'),
        entry('EDITEE', '2026-09-17T09:04:00Z'),
        entry('EN_EDITION', '2026-09-17T09:03:00Z'),
      ]);

      expect(adminComponent.historiqueTriee().map((e) => e.nouveauStatut)).toEqual(['LIVREE', 'EDITEE', 'EN_EDITION', 'EN_TRAITEMENT', 'PAYEE']);
      expect(adminComponent.historiqueVisible().length).toBe(3);
      expect(adminComponent.historiqueMasquees()).toBe(2);

      adminComponent.toggleHistoriqueExpanded();
      expect(adminComponent.historiqueVisible().length).toBe(5);

      // Rouvrir un détail repart replié.
      adminComponent.openDetail(demandeResponse({ id: 2 }));
      adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/2/documents').flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 1 });
      adminHttpMock.expectOne('/vignettes-moto/demandes/2/historique').flush([]);
      expect(adminComponent.historiqueExpanded()).toBe(false);
    });

    it('documentPillVariant : vert par défaut, rouge si rejeté/supprimé, orange si archivé', () => {
      expect(adminComponent.documentPillVariant('UPLOADED')).toBe('');
      expect(adminComponent.documentPillVariant('VALIDATED')).toBe('');
      expect(adminComponent.documentPillVariant('REJECTED')).toBe('danger');
      expect(adminComponent.documentPillVariant('DELETED')).toBe('danger');
      expect(adminComponent.documentPillVariant('ARCHIVED')).toBe('warning');
    });

    it('submitChangerStatut poste sur /statut et met à jour la demande + rafraîchit l’historique', () => {
      const demande = demandeResponse({ statut: 'PAYEE' });
      adminComponent.openDetail(demande);
      adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/documents').flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 1 });
      adminHttpMock.expectOne('/vignettes-moto/demandes/1/historique').flush([]);

      adminComponent.askChangerStatut();
      expect(adminComponent.statutChangeForm.controls.statut.value).toBe('EN_TRAITEMENT');
      adminComponent.submitChangerStatut();

      const req = adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/statut' && r.method === 'POST');
      expect(req.request.body).toEqual({ statut: 'EN_TRAITEMENT', motif: undefined, referenceOperation: undefined });
      req.flush(demandeResponse({ statut: 'EN_TRAITEMENT' }));
      adminHttpMock.expectOne('/vignettes-moto/demandes/1/historique').flush([]);

      expect(adminComponent.statutChangeModalVisible()).toBe(false);
      expect(adminComponent.selectedDemande()?.statut).toBe('EN_TRAITEMENT');
    });

    it('submitChangerStatut affiche le message backend sur 409 (transition refusée)', () => {
      const demande = demandeResponse({ statut: 'PAYEE' });
      adminComponent.openDetail(demande);
      adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/documents').flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 1 });
      adminHttpMock.expectOne('/vignettes-moto/demandes/1/historique').flush([]);

      adminComponent.askChangerStatut();
      adminComponent.submitChangerStatut();
      adminHttpMock
        .expectOne((r) => r.url === '/vignettes-moto/demandes/1/statut')
        .flush({ message: 'Transition non autorisée depuis PAYEE' }, { status: 409, statusText: 'Conflict' });

      expect(adminComponent.statutChangeError()).toBe('Transition non autorisée depuis PAYEE');
    });

    it('regenererLienSuivi appelle /lien-suivi/regeneration et expose le lien renvoyé', () => {
      adminComponent.openDetail(demandeResponse());
      adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/documents').flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 1 });
      adminHttpMock.expectOne('/vignettes-moto/demandes/1/historique').flush([]);

      adminComponent.regenererLienSuivi();
      const req = adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/lien-suivi/regeneration' && r.method === 'POST');
      req.flush({ tokenBrut: 'abc', lienComplet: 'https://x/suivi-vignette?token=abc', dateExpiration: '2026-09-24T00:00:00Z' });

      expect(adminComponent.lienRegenerated()?.lienComplet).toBe('https://x/suivi-vignette?token=abc');
    });

    it('submitRevoquerLien exige un motif puis poste sur /lien-suivi/revocation', () => {
      adminComponent.openDetail(demandeResponse());
      adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/documents').flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 1 });
      adminHttpMock.expectOne('/vignettes-moto/demandes/1/historique').flush([]);

      adminComponent.askRevoquerLien();
      adminComponent.submitRevoquerLien();
      expect(adminComponent.revokeLienError()).toBe('vignettesMoto.errors.motifObligatoire');

      adminComponent.revokeLienForm.controls.motif.setValue('Compromission suspectée');
      adminComponent.submitRevoquerLien();
      const req = adminHttpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/1/lien-suivi/revocation' && r.method === 'POST');
      expect(req.request.body).toEqual({ motif: 'Compromission suspectée' });
      req.flush(null);

      expect(adminComponent.revokeLienModalVisible()).toBe(false);
    });
  });
});
