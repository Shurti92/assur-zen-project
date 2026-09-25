import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { IconSetService } from '@coreui/icons-angular';

import { CommissionsConfigureesComponent } from './commissions-configurees.component';
import { PartnerCommissionResponse } from '../../../core/partner-commission/models/partner-commission.model';

describe('CommissionsConfigureesComponent', () => {
  let component: CommissionsConfigureesComponent;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CommissionsConfigureesComponent],
      providers: [IconSetService, provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    component = TestBed.createComponent(CommissionsConfigureesComponent).componentInstance;
  });

  function savedPayload() {
    component.save();
    const req = http.expectOne((r) => r.url === '/partner-commissions' && r.method === 'POST');
    return req.request.body;
  }

  it('partenaire précis : envoie partenaireId, jamais typePartenaire ni commercialId', () => {
    component.openCreate();
    component.form.patchValue({ produitId: 75, beneficiaire: 'PARTENAIRE', cible: 'PARTENAIRE', partenaireId: '12', montant: 2500 });

    expect(savedPayload()).toEqual(
      expect.objectContaining({ produitId: 75, beneficiaire: 'PARTENAIRE', partenaireId: 12, typePartenaire: null, commercialId: null, montant: 2500 }),
    );
  });

  it('type de partenaire : envoie typePartenaire seul', () => {
    component.openCreate();
    component.form.patchValue({ produitId: 75, beneficiaire: 'PARTENAIRE', cible: 'TYPE', typePartenaire: 'APPORTEUR', montant: 1000 });

    expect(savedPayload()).toEqual(expect.objectContaining({ partenaireId: null, typePartenaire: 'APPORTEUR', commercialId: null }));
  });

  it('commercial : envoie commercialId seul', () => {
    component.openCreate();
    component.form.patchValue({ produitId: 75, beneficiaire: 'COMMERCIAL', commercialId: '7', montant: 1500 });

    expect(savedPayload()).toEqual(expect.objectContaining({ beneficiaire: 'COMMERCIAL', partenaireId: null, typePartenaire: null, commercialId: 7 }));
  });

  it('affiche le message du backend en cas de chevauchement (409)', () => {
    component.openCreate();
    component.form.patchValue({ produitId: 75, cible: 'PARTENAIRE', partenaireId: '12', montant: 2500 });
    component.save();
    http
      .expectOne((r) => r.url === '/partner-commissions' && r.method === 'POST')
      .flush({ message: 'Chevauchement avec la configuration 3' }, { status: 409, statusText: 'Conflict' });

    expect(component.formError()).toBe('Chevauchement avec la configuration 3');
    expect(component.formVisible()).toBe(true);
  });

  it('refuse une période incohérente et un montant manquant', () => {
    component.openCreate();
    component.form.patchValue({ produitId: 75, cible: 'PARTENAIRE', partenaireId: '12' });
    component.save();
    expect(component.formError()).toContain('montant');

    component.form.patchValue({ montant: 100, dateDebut: '2026-10-01', dateFin: '2026-09-01' });
    component.save();
    expect(component.formError()).toContain('date de fin');
  });

  it('« Désactiver » fait un PUT active=false en conservant le reste', () => {
    const item = {
      id: 3, produitId: 75, beneficiaire: 'PARTENAIRE', partenaireId: 12, typePartenaire: null, commercialId: null,
      montant: 2500, active: true, dateDebut: null, dateFin: null,
    } as unknown as PartnerCommissionResponse;
    component.toggleActive(item);
    const req = http.expectOne((r) => r.url === '/partner-commissions/3' && r.method === 'PUT');
    expect(req.request.body).toEqual(expect.objectContaining({ partenaireId: 12, montant: 2500, active: false }));
  });

  it('libellé de la cible', () => {
    const base = { beneficiaire: 'PARTENAIRE', partenaireId: null, partenaireNom: null, commercialId: null } as unknown as PartnerCommissionResponse;
    expect(component.cibleLabel({ ...base, typePartenaire: 'AGENT' })).toBe('Tous les partenaires agent');
    expect(component.cibleLabel({ ...base, partenaireId: 12, partenaireNom: 'XYZ' })).toBe('XYZ');
  });
});
