import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuiviStatusBadgeComponent } from './suivi-status-badge.component';

describe('SuiviStatusBadgeComponent', () => {
  let fixture: ComponentFixture<SuiviStatusBadgeComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [SuiviStatusBadgeComponent] });
    fixture = TestBed.createComponent(SuiviStatusBadgeComponent);
  });

  it('applique la variante sémantique correspondant au statut (ex. LIVREE -> success)', () => {
    fixture.componentRef.setInput('statut', 'LIVREE');
    fixture.componentRef.setInput('label', 'Livrée');
    fixture.detectChanges();

    const chip = fixture.nativeElement.querySelector('.dl-chip');
    expect(chip.className).toContain('dl-chip--success');
    expect(chip.textContent).toContain('Livrée');
  });
});
