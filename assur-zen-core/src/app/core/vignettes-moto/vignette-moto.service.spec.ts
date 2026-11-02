import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { VignetteMotoService } from './vignette-moto.service';
import { DemandeVignetteMotoResponse, SimulationTarifVignetteMotoResponse } from './models/vignette-moto.model';

describe('VignetteMotoService', () => {
  let service: VignetteMotoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [VignetteMotoService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(VignetteMotoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('simulerTarification envoie un POST sur /vignettes-moto/tarification/simuler avec le corps tel quel', () => {
    const payload = { datePremiereMiseCirculation: '2020-05-15', cylindree: 110, zoneLivraison: 'ABIDJAN' };
    const response: SimulationTarifVignetteMotoResponse = {
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

    service.simulerTarification(payload).subscribe((result) => {
      expect(result).toEqual(response);
    });

    const req = httpMock.expectOne('/vignettes-moto/tarification/simuler');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(response);
  });

  it('search envoie page/size et omet le paramètre statut quand non fourni', () => {
    service.search(0, 20).subscribe();

    const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('statut')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
  });

  it('search transmet le filtre statut quand fourni', () => {
    service.search(1, 10, 'BROUILLON').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes');
    expect(req.request.params.get('statut')).toBe('BROUILLON');
    expect(req.request.params.get('page')).toBe('1');
    req.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 1 });
  });

  it('get consulte /vignettes-moto/demandes/{id}', () => {
    service.get(42).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/demandes/42');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('create envoie un payload plat (pas assure{}/moto{} imbriqués)', () => {
    const payload = {
      typeDemande: 'NOUVELLE_DEMANDE' as const,
      nomAssure: 'KOUASSI',
      prenomsAssure: 'Jean',
      telephoneAssure: '0700000000',
      adresseLivraison: 'Cocody',
      villeLivraison: 'Abidjan',
      zoneLivraison: 'ABIDJAN',
      marqueMoto: 'Yamaha',
      modeleMoto: 'Crypton',
      numeroChassisMoto: 'XXXXXXXXXXXX',
      cylindreeMoto: 110,
      datePremiereMiseCirculation: '2020-05-15',
    };

    service.create(payload).subscribe();

    const req = httpMock.expectOne('/vignettes-moto/demandes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    // Le contrat backend exige des champs plats — un bug de régression ici (objets imbriqués)
    // romprait la création silencieusement côté backend.
    expect((req.request.body as Record<string, unknown>)['assure']).toBeUndefined();
    expect((req.request.body as Record<string, unknown>)['moto']).toBeUndefined();
    req.flush({} as DemandeVignetteMotoResponse);
  });

  it('soumettre poste sur /demandes/{id}/soumettre sans corps', () => {
    service.soumettre(7).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/demandes/7/soumettre');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({});
  });

  it('annuler envoie un PATCH sans corps', () => {
    service.annuler(7).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/demandes/7/annuler');
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });

  it('rejeter envoie le motif en PATCH', () => {
    service.rejeter(7, { motif: 'Dossier incomplet' }).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/demandes/7/rejeter');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ motif: 'Dossier incomplet' });
    req.flush({});
  });

  it('listDocuments pagine sur /demandes/{id}/documents', () => {
    service.listDocuments(7, 0, 50).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/7/documents');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 1 });
  });

  it('uploadDocument envoie typeCode en query param et le fichier seul en multipart', () => {
    const file = new File(['contenu'], 'cni.pdf', { type: 'application/pdf' });
    service.uploadDocument(7, 'CNI_ASSURE', file).subscribe();

    const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/7/documents');
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('typeCode')).toBe('CNI_ASSURE');
    expect(req.request.body instanceof FormData).toBe(true);
    const body = req.request.body as FormData;
    expect(body.get('file')).toBe(file);
    // Seul "file" doit voyager dans le corps — typeCode reste en query, pas dans le FormData.
    expect(body.get('typeCode')).toBeNull();
    req.flush({});
  });

  it('downloadDocument consulte le nouvel endpoint scopé /demandes/{id}/documents/{documentId}/download en blob', () => {
    let received: Blob | undefined;
    service.downloadDocument(7, 42).subscribe((blob) => (received = blob));

    const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/7/documents/42/download');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    const blob = new Blob(['contenu']);
    req.flush(blob);
    expect(received).toBe(blob);
  });

  it('historique consulte /demandes/{id}/historique', () => {
    service.historique(7).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/demandes/7/historique');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('changerStatut poste le nouveau statut sur /demandes/{id}/statut', () => {
    service.changerStatut(7, { statut: 'PAYEE', motif: 'Paiement confirmé' }).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/demandes/7/statut');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ statut: 'PAYEE', motif: 'Paiement confirmé' });
    req.flush({});
  });

  it('regenererLienSuivi poste sans corps sur /demandes/{id}/lien-suivi/regeneration', () => {
    service.regenererLienSuivi(7).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/demandes/7/lien-suivi/regeneration');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ tokenBrut: 't', lienComplet: 'https://x', dateExpiration: '2026-09-24T00:00:00Z' });
  });

  it('revoquerLienSuivi envoie le motif sur /demandes/{id}/lien-suivi/revocation', () => {
    service.revoquerLienSuivi(7, { motif: 'Compromission' }).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/demandes/7/lien-suivi/revocation');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ motif: 'Compromission' });
    req.flush({});
  });

  it('suiviPublic envoie le token en query param sur /demandes/suivi-public', () => {
    service.suiviPublic('abc123').subscribe();
    const req = httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/suivi-public');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('token')).toBe('abc123');
    req.flush({});
  });
});
