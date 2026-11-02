import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { VignetteLivraisonService } from './vignette-livraison.service';
import { FraisLivraisonZoneRequest } from './models/vignette-livraison.model';

describe('VignetteLivraisonService', () => {
  let service: VignetteLivraisonService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [VignetteLivraisonService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(VignetteLivraisonService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list ne pagine pas — GET simple sur /vignettes-moto/livraisons', () => {
    service.list().subscribe((zones) => expect(zones.length).toBe(0));
    const req = httpMock.expectOne('/vignettes-moto/livraisons');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('create poste le payload tel quel', () => {
    const payload: FraisLivraisonZoneRequest = { code: 'ABIDJAN', libelle: 'Abidjan', montant: 1500 };
    service.create(payload).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/livraisons');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({});
  });

  it('update envoie un PUT sur /vignettes-moto/livraisons/{id}', () => {
    const payload: FraisLivraisonZoneRequest = { code: 'HORS_ABIDJAN', libelle: 'Hors Abidjan', montant: 2000 };
    service.update(2, payload).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/livraisons/2');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush({});
  });

  it('desactiver envoie un PATCH sans corps', () => {
    service.desactiver(2).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/livraisons/2/desactiver');
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });
});
