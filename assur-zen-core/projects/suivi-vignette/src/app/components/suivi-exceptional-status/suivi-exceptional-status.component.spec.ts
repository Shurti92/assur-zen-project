import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuiviExceptionalStatusComponent } from './suivi-exceptional-status.component';
import { SuiviPublicVignetteResponse } from '../../models/suivi-vignette.model';

function suiviResponse(overrides: Partial<SuiviPublicVignetteResponse> = {}): SuiviPublicVignetteResponse {
  return {
    reference: 'VGN2026000001',
    statut: 'REJETEE',
    libelleStatut: 'Demande rejetée',
    motifRejet: null,
    dateDerniereMiseAJour: '2026-09-17T09:30:00Z',
    historique: [],
    ...overrides,
  };
}

describe('SuiviExceptionalStatusComponent', () => {
  let fixture: ComponentFixture<SuiviExceptionalStatusComponent>;

  const render = (suivi: SuiviPublicVignetteResponse) => {
    fixture = TestBed.createComponent(SuiviExceptionalStatusComponent);
    fixture.componentRef.setInput('suivi', suivi);
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [SuiviExceptionalStatusComponent] });
  });

  it('affiche le motif quand il est présent', () => {
    render(suiviResponse({ motifRejet: 'Châssis illisible' }));
    expect(fixture.nativeElement.textContent).toContain('Châssis illisible');
  });

  it('ne montre aucun encart motif quand motifRejet est null', () => {
    render(suiviResponse({ motifRejet: null }));
    expect(fixture.nativeElement.querySelector('.es-motif')).toBeNull();
  });
});
