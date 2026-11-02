import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { VignetteSuiviService } from './vignette-suivi.service';
import { environment } from '../../environments/environment';

describe('VignetteSuiviService', () => {
  let service: VignetteSuiviService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [VignetteSuiviService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(VignetteSuiviService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('appelle GET {apiUrl}/vignettes-moto/demandes/suivi-public avec le token en query param', () => {
    service.suiviPublic('tok-123').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/vignettes-moto/demandes/suivi-public`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('token')).toBe('tok-123');
    req.flush({});
  });
});
