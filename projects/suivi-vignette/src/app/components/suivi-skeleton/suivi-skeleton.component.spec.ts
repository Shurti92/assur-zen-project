import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuiviSkeletonComponent } from './suivi-skeleton.component';

describe('SuiviSkeletonComponent', () => {
  let fixture: ComponentFixture<SuiviSkeletonComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [SuiviSkeletonComponent] });
    fixture = TestBed.createComponent(SuiviSkeletonComponent);
    fixture.detectChanges();
  });

  it('affiche un statut de chargement annoncé aux lecteurs d’écran', () => {
    const status = fixture.nativeElement.querySelector('[role="status"]');
    expect(status).not.toBeNull();
    expect(status.getAttribute('aria-live')).toBe('polite');
  });
});
