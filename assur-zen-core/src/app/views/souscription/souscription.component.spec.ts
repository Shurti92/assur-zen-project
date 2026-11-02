import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { IconSetService } from '@coreui/icons-angular';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import type { MockInstance } from 'vitest';

import { SouscriptionComponent } from './souscription.component';
import { SouscriptionService } from '../../core/souscription/souscription.service';
import { InsuredRegistrationResponse } from '../../core/souscription/models/souscription.model';
import { ProduitCommissionTauxResponse } from '../../core/configuration/models/produit-commission-taux.model';
import { PartenaireResponse } from '../../core/partenaire/models/partenaire.model';
import { ProduitResponse } from '../../core/configuration/models/produit.model';

/** Parcours « commission » de l'assistant (2026-09-25) : la valeur saisie est celle envoyée,
 * les recalculs automatiques n'écrasent jamais une saisie manuelle, les commissions partenaire
 * et commerciale sont préremplies depuis /partner-commissions. */
describe('SouscriptionComponent — commissions', () => {
  let component: SouscriptionComponent;
  let http: HttpTestingController;
  let submitSpy: MockInstance<SouscriptionService['submit']>;

  const taux = (typePartenaire: 'DIRECT' | 'AGENT', tauxCommission: number, libTauxCommission = '%') =>
    ({ id: 1, produitId: 75, typePartenaire, tauxCommission, libTauxCommission, active: true }) as unknown as ProduitCommissionTauxResponse;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SouscriptionComponent],
      providers: [IconSetService, provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    submitSpy = vi
      .spyOn(TestBed.inject(SouscriptionService), 'submit')
      .mockReturnValue(of({ contracts: [] } as unknown as InsuredRegistrationResponse));

    component = TestBed.createComponent(SouscriptionComponent).componentInstance;
    component.produits.set([{ id: 75, nomProduit: 'Assurance Auto' } as unknown as ProduitResponse]);
    component.partenaires.set([{ id: 12, type: 'AGENT', nom: 'XYZ', prenoms: '' } as unknown as PartenaireResponse]);
    component.commissionTaux.set([taux('AGENT', 10), taux('DIRECT', 10)]);
    component.form.controls.insured.patchValue({
      nom: 'Kone',
      prenoms: 'Awa',
      clientPhone: '0700000001',
      clientCity: 'Abidjan',
    });
  });

  const row = () => component.contractsArray.at(0);

  /** Réponse de GET /partner-commissions/by-product/75/partner/12 (null = 404). */
  function answerPartnerConfig(montant: number | null): void {
    const req = http.expectOne((r) => r.url === '/partner-commissions/by-product/75/partner/12');
    if (montant === null) req.flush({ message: 'Aucune commission' }, { status: 404, statusText: 'Not Found' });
    else req.flush({ montant });
  }

  /** Sélection produit + partenaire + prime, comme dans l'écran. */
  function fillRow(partenaireId: string, premium: number, partnerConfig?: number | null): void {
    row().patchValue({ productId: 75, compagnieId: 1, partenaireId });
    component.recomputeCommission(0);
    if (partenaireId) answerPartnerConfig(partnerConfig ?? null);
    row().controls.premium.setValue(premium);
  }

  function submittedContract() {
    component.submit();
    expect(submitSpy).toHaveBeenCalledTimes(1);
    return submitSpy.mock.calls[0][0].contracts[0];
  }

  it('cas 1 — une part partenaire saisie (2500) est envoyée telle quelle, en nombre', () => {
    fillRow('12', 45000, 2000);
    row().controls.partnerShare.setValue(2500);

    const contract = submittedContract();
    expect(contract.partnerShare).toBe(2500);
    expect(typeof contract.partnerShare).toBe('number');
  });

  it('cas 2 — commission configurée (3000), non modifiée : 3000 envoyé', () => {
    fillRow('12', 30000, 3000);

    expect(component.commissionHint(0).share).toBe(3000);
    const contract = submittedContract();
    expect(contract.partnerShare).toBe(3000);
    expect(contract.commissionAmount).toBe(3000);
    // Taux saisi en % (10), envoyé en fraction.
    expect(contract.commissionRate).toBe(0.1);
  });

  it('cas 3 — configurée 3000, modifiée à 2500 : 2500 envoyé, même après changement de prime', () => {
    fillRow('12', 30000, 3000);
    row().controls.partnerShare.setValue(2500);
    row().controls.premium.setValue(40000);

    expect(component.commissionHint(0).shareModified).toBe(true);
    expect(row().controls.commissionAmount.value).toBe(4000);
    expect(submittedContract().partnerShare).toBe(2500);
  });

  it('cas 4 — aucune commission partenaire configurée : saisie obligatoire', () => {
    fillRow('12', 30000, null);

    expect(component.commissionHint(0).share).toBeNull();
    component.submit();
    expect(submitSpy).not.toHaveBeenCalled();
    expect(component.error()).toContain('Aucune commission partenaire');

    row().controls.partnerShare.setValue(1800);
    expect(submittedContract().partnerShare).toBe(1800);
  });

  it('un montant de commission saisi est conservé quand la prime change', () => {
    fillRow('12', 30000, 3000);
    row().controls.commissionAmount.setValue(3500);
    row().controls.premium.setValue(32000);

    expect(submittedContract().commissionAmount).toBe(3500);
  });

  it('configuration en montant fixe (libellé ≠ "%") : montant appliqué tel quel', () => {
    component.commissionTaux.set([taux('AGENT', 5000, 'FIXE')]);
    fillRow('12', 40000, 3000);

    expect(row().controls.commissionAmount.value).toBe(5000);
    expect(row().controls.commissionRate.value).toBe(12.5);
  });

  it('« Recalculer » rétablit les valeurs configurées', () => {
    fillRow('12', 30000, 3000);
    row().controls.partnerShare.setValue(2500);

    component.recomputeCommission(0, true);
    answerPartnerConfig(3000);

    expect(row().controls.partnerShare.value).toBe(3000);
    expect(component.commissionHint(0).shareModified).toBe(false);
  });

  it('vente directe : part partenaire à 0, pas de commission commerciale sans commercial', () => {
    fillRow('', 30000);
    const contract = submittedContract();
    expect(contract.partnerShare).toBe(0);
    expect(contract.commercialCommissionAmount).toBeUndefined();
  });

  it('commercial en vente directe : commission préremplie depuis sa configuration puis modifiable', () => {
    fillRow('', 30000);
    row().controls.salesRepId.setValue('7');
    component.onCommercialChange(0);
    http.expectOne((r) => r.url === '/partner-commissions/by-product/75/commercial/7').flush({ montant: 1500 });

    expect(component.isCommercialCommissionVisible(0)).toBe(true);
    expect(row().controls.commercialCommissionAmount.value).toBe(1500);

    row().controls.commercialCommissionAmount.setValue(1750);
    expect(submittedContract().commercialCommissionAmount).toBe(1750);
  });

  it('commercial sans configuration propre : repli sur la commission commerciale du produit', () => {
    fillRow('', 30000);
    row().controls.salesRepId.setValue('7');
    component.onCommercialChange(0);
    http
      .expectOne((r) => r.url === '/partner-commissions/by-product/75/commercial/7')
      .flush({}, { status: 404, statusText: 'Not Found' });
    http
      .expectOne((r) => r.url === '/produits-commission-commercial' && r.params.get('produitId') === '75')
      .flush([{ produitId: 75, tauxCommission: 2, libTauxCommission: '%', active: true }]);

    expect(row().controls.commercialCommissionAmount.value).toBe(600);
  });

  it('avec un partenaire : champ commercial masqué et rien envoyé', () => {
    fillRow('12', 30000, 3000);
    row().controls.salesRepId.setValue('7');
    component.onCommercialChange(0);

    expect(component.isCommercialCommissionVisible(0)).toBe(false);
    expect(submittedContract().commercialCommissionAmount).toBeUndefined();
  });

  it('refuse une part partenaire supérieure à la commission, même inférieure à la prime', () => {
    fillRow('12', 30000, 1000);
    row().controls.partnerShare.setValue(3500);

    component.submit();

    expect(submitSpy).not.toHaveBeenCalled();
    expect(component.error()).toContain('ne peut pas dépasser le montant de la commission');
  });

  it('un refus 400 du backend affiche son motif', () => {
    const motif = 'partnerShare (2 500 FCFA) ne peut pas depasser la commission du contrat (2 000 FCFA)';
    submitSpy.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 400, error: { code: '400 BAD_REQUEST', message: motif } })),
    );
    fillRow('12', 30000, 1000);

    component.submit();

    expect(component.error()).toBe(motif);
  });

  it('accepte une part partenaire égale à la commission', () => {
    fillRow('12', 30000, 1000);
    row().controls.partnerShare.setValue(3000);

    expect(submittedContract().partnerShare).toBe(3000);
  });
});
