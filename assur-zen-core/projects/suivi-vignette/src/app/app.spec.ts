import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { App } from './app';
import { SuiviPublicVignetteResponse } from './models/suivi-vignette.model';

function suiviResponse(overrides: Partial<SuiviPublicVignetteResponse> = {}): SuiviPublicVignetteResponse {
  return {
    reference: 'VGN2026000001',
    statut: 'EN_EDITION',
    libelleStatut: "Vignette en cours d'édition",
    motifRejet: null,
    dateDerniereMiseAJour: '2026-09-17T09:30:00Z',
    historique: [
      { statut: 'BROUILLON', libelle: 'Demande enregistrée', date: '2026-09-10T08:00:00Z' },
      { statut: 'EN_EDITION', libelle: "Vignette en cours d'édition", date: '2026-09-17T09:30:00Z' },
    ],
    ...overrides,
  };
}

function createComponent(search: string): { fixture: ComponentFixture<App>; httpMock: HttpTestingController } {
  history.pushState(null, '', `/${search}`);

  TestBed.configureTestingModule({
    imports: [App],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  }).compileComponents();

  const httpMock = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(App);
  fixture.detectChanges();
  return { fixture, httpMock };
}

describe('App (mini-app suivi-vignette — déploiement séparé, aucune authentification)', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    history.pushState(null, '', '/');
  });

  it("état 'tokenAbsent' — n'appelle pas le backend si aucun token n'est présent dans l'URL", () => {
    const { fixture, httpMock } = createComponent('');
    httpMock.expectNone(() => true);
    expect(fixture.componentInstance.etat()).toBe('tokenAbsent');
    httpMock.verify();
  });

  it("état 'success' — lit le token depuis window.location.search et affiche la timeline", () => {
    const { fixture, httpMock } = createComponent('?token=tok-123');
    const req = httpMock.expectOne((r) => r.url.endsWith('/vignettes-moto/demandes/suivi-public'));
    expect(req.request.params.get('token')).toBe('tok-123');

    req.flush(suiviResponse());
    fixture.detectChanges();

    expect(fixture.componentInstance.etat()).toBe('success');
    expect(fixture.nativeElement.textContent).toContain('VGN2026000001');
    httpMock.verify();
  });

  it("état 'tokenInvalid' — un 404 (token inconnu, expiré ou révoqué) affiche l'écran unique 'lien invalide'", () => {
    const { fixture, httpMock } = createComponent('?token=tok-invalide');
    httpMock
      .expectOne((r) => r.url.endsWith('/vignettes-moto/demandes/suivi-public'))
      .flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.componentInstance.etat()).toBe('tokenInvalid');
    httpMock.verify();
  });

  it("état 'serverError' — toute erreur ≠ 404 affiche l'écran générique, distinct de 'lien invalide'", () => {
    const { fixture, httpMock } = createComponent('?token=tok-123');
    httpMock
      .expectOne((r) => r.url.endsWith('/vignettes-moto/demandes/suivi-public'))
      .flush({}, { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(fixture.componentInstance.etat()).toBe('serverError');
    httpMock.verify();
  });

  it('le token ne doit jamais apparaître dans le texte affiché à l’écran', () => {
    const { fixture, httpMock } = createComponent('?token=tok-super-secret-999');
    httpMock.expectOne((r) => r.url.endsWith('/vignettes-moto/demandes/suivi-public')).flush(suiviResponse());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('tok-super-secret-999');
    httpMock.verify();
  });
});
