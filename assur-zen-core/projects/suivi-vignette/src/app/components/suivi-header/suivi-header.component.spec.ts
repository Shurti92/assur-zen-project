import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuiviHeaderComponent } from './suivi-header.component';

describe('SuiviHeaderComponent', () => {
  let fixture: ComponentFixture<SuiviHeaderComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [SuiviHeaderComponent] });
    fixture = TestBed.createComponent(SuiviHeaderComponent);
    fixture.detectChanges();
  });

  it('affiche le nom AssurZen et la mention "Service de suivi"', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('AssurZen');
    expect(text).toContain('Service de suivi');
  });
});
