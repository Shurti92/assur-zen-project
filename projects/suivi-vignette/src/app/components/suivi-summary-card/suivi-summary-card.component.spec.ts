import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuiviSummaryCardComponent } from './suivi-summary-card.component';
import { SuiviPublicVignetteResponse } from '../../models/suivi-vignette.model';

function suiviResponse(overrides: Partial<SuiviPublicVignetteResponse> = {}): SuiviPublicVignetteResponse {
  return {
    reference: 'VGN2026000001',
    statut: 'EN_EDITION',
    libelleStatut: "Vignette en cours d'édition",
    motifRejet: null,
    dateDerniereMiseAJour: '2026-09-17T09:30:00Z',
    historique: [],
    ...overrides,
  };
}

describe('SuiviSummaryCardComponent', () => {
  let fixture: ComponentFixture<SuiviSummaryCardComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [SuiviSummaryCardComponent] });
    fixture = TestBed.createComponent(SuiviSummaryCardComponent);
    fixture.componentRef.setInput('suivi', suiviResponse());
    fixture.detectChanges();
  });

  it('affiche la référence et le statut', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('VGN2026000001');
    expect(text).toContain("Vignette en cours d'édition");
  });

  it('copie la référence dans le presse-papiers au clic sur le bouton copier', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

    await fixture.componentInstance.copyReference();

    expect(writeText).toHaveBeenCalledWith('VGN2026000001');
    expect(fixture.componentInstance.copied()).toBe(true);
  });
});
