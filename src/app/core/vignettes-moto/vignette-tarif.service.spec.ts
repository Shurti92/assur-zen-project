import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { VignetteTarifService } from './vignette-tarif.service';
import { TarifVignetteMotoRequest } from './models/vignette-tarif.model';

describe('VignetteTarifService', () => {
  let service: VignetteTarifService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [VignetteTarifService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(VignetteTarifService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list ne pagine pas — GET simple sur /vignettes-moto/tarifs', () => {
    service.list().subscribe((tarifs) => expect(tarifs.length).toBe(0));
    const req = httpMock.expectOne('/vignettes-moto/tarifs');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush([]);
  });

  it('get consulte /vignettes-moto/tarifs/{id}', () => {
    service.get(3).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/tarifs/3');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('create poste le payload tel quel sur /vignettes-moto/tarifs', () => {
    const payload: TarifVignetteMotoRequest = {
      categorieAge: '5_10_ANS',
      categorieCylindree: 'MOINS_125_CM3',
      montantVignette: 3750,
      fraisOperation: 1500,
      dateDebutValidite: '2026-01-01',
    };
    service.create(payload).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/tarifs');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({});
  });

  it('update envoie un PUT sur /vignettes-moto/tarifs/{id}', () => {
    const payload: TarifVignetteMotoRequest = {
      categorieAge: '1_4_ANS',
      categorieCylindree: '125_CM3_ET_PLUS',
      montantVignette: 12000,
      fraisOperation: 3500,
      dateDebutValidite: '2026-01-01',
    };
    service.update(5, payload).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/tarifs/5');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush({});
  });

  it('desactiver envoie un PATCH sans corps sur /desactiver', () => {
    service.desactiver(5).subscribe();
    const req = httpMock.expectOne('/vignettes-moto/tarifs/5/desactiver');
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });
});
