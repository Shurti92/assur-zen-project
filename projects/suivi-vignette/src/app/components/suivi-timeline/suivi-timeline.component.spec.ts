import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuiviTimelineComponent } from './suivi-timeline.component';
import { SuiviPublicVignetteResponse } from '../../models/suivi-vignette.model';

function suiviResponse(overrides: Partial<SuiviPublicVignetteResponse> = {}): SuiviPublicVignetteResponse {
  return {
    reference: 'VGN2026000001',
    statut: 'EN_TRAITEMENT',
    libelleStatut: 'Demande en cours de traitement',
    motifRejet: null,
    dateDerniereMiseAJour: '2026-09-17T09:30:00Z',
    historique: [
      { statut: 'BROUILLON', libelle: 'Demande enregistrée', date: '2026-09-10T08:00:00Z' },
      { statut: 'EN_ATTENTE_PAIEMENT', libelle: 'En attente de paiement', date: '2026-09-10T08:05:00Z' },
      { statut: 'PAYEE', libelle: 'Paiement confirmé', date: '2026-09-12T14:20:00Z' },
      { statut: 'EN_TRAITEMENT', libelle: 'Demande en cours de traitement', date: '2026-09-14T10:00:00Z' },
    ],
    ...overrides,
  };
}

describe('SuiviTimelineComponent', () => {
  let fixture: ComponentFixture<SuiviTimelineComponent>;

  const render = (suivi: SuiviPublicVignetteResponse) => {
    fixture = TestBed.createComponent(SuiviTimelineComponent);
    fixture.componentRef.setInput('suivi', suivi);
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [SuiviTimelineComponent] });
  });

  it('produit les 8 étapes non-terminales', () => {
    render(suiviResponse());
    expect(fixture.componentInstance.rows().length).toBe(8);
  });

  it("marque l'étape du statut courant 'current'", () => {
    render(suiviResponse());
    expect(fixture.componentInstance.rows()[3].state).toBe('current');
  });

  it('marque les étapes futures "upcoming" avec le libellé de secours, sans date', () => {
    render(suiviResponse());
    const editee = fixture.componentInstance.rows()[5];
    expect(editee.state).toBe('upcoming');
    expect(editee.libelle).toBe('Vignette éditée');
    expect(editee.date).toBeNull();
  });
});
