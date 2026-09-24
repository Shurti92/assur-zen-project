import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpParams, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { loggingInterceptor } from './logging.interceptor';
import { LogService } from '../logging/log.service';

describe('loggingInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let logService: { debug: ReturnType<typeof vi.fn>; warn: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    logService = { debug: vi.fn(), warn: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([loggingInterceptor])),
        provideHttpClientTesting(),
        { provide: LogService, useValue: logService },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('masque le paramètre token avant de logger une réponse réussie', () => {
    http.get('/vignettes-moto/demandes/suivi-public', { params: new HttpParams().set('token', 'SECRET123') }).subscribe();
    httpMock.expectOne((r) => r.url === '/vignettes-moto/demandes/suivi-public').flush({});

    expect(logService.debug).toHaveBeenCalled();
    const message = logService.debug.mock.calls.at(-1)?.[1];
    expect(message).not.toContain('SECRET123');
    expect(message).not.toContain('token=SECRET123');
  });

  it('masque aussi le token sur une erreur (404 lien invalide/expiré/révoqué)', () => {
    http
      .get('/vignettes-moto/demandes/suivi-public', { params: new HttpParams().set('token', 'SECRET456') })
      .subscribe({ error: () => undefined });
    httpMock
      .expectOne((r) => r.url === '/vignettes-moto/demandes/suivi-public')
      .flush({}, { status: 404, statusText: 'Not Found' });

    expect(logService.warn).toHaveBeenCalled();
    const message = logService.warn.mock.calls.at(-1)?.[1];
    expect(message).not.toContain('SECRET456');
  });

  it("ne modifie pas une URL sans paramètre sensible", () => {
    http
      .get('/vignettes-moto/demandes', { params: new HttpParams().set('page', 0).set('size', 20) })
      .subscribe();
    httpMock
      .expectOne((r) => r.url === '/vignettes-moto/demandes')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });

    const message = logService.debug.mock.calls.at(-1)?.[1];
    expect(message).toContain('page=0');
    expect(message).toContain('size=20');
  });
});
