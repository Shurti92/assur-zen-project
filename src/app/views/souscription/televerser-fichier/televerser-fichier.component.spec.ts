import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { IconSetService } from '@coreui/icons-angular';
import { of } from 'rxjs';
import type { MockInstance } from 'vitest';

import { TeleverserFichierComponent } from './televerser-fichier.component';
import { SouscriptionService } from '../../../core/souscription/souscription.service';
import {
  InsuredRegistrationResponse,
} from '../../../core/souscription/models/souscription.model';
import { ProduitResponse } from '../../../core/configuration/models/produit.model';
import { CompagnieResponse } from '../../../core/configuration/models/compagnie.model';
import { ProduitCommissionTauxResponse } from '../../../core/configuration/models/produit-commission-taux.model';
import { PartenaireResponse } from '../../../core/partenaire/models/partenaire.model';

/** Import en masse — colonnes montant_commission / part_partenaire (2026-09-25). */
describe('TeleverserFichierComponent — commission partenaire', () => {
  let component: TeleverserFichierComponent;
  let submitSpy: MockInstance<SouscriptionService['submit']>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TeleverserFichierComponent],
      providers: [IconSetService, provideHttpClient(), provideHttpClientTesting()],
    });
    submitSpy = vi
      .spyOn(TestBed.inject(SouscriptionService), 'submit')
      .mockReturnValue(of({ contracts: [] } as unknown as InsuredRegistrationResponse));

    component = TestBed.createComponent(TeleverserFichierComponent).componentInstance;
    component.produits.set([{ id: 75, nomProduit: 'Assurance Auto' } as unknown as ProduitResponse]);
    component.compagnies.set([{ id: 1, nom: 'AXA' } as unknown as CompagnieResponse]);
    component.partenaires.set([{ id: 12, type: 'AGENT', nom: 'XYZ', prenoms: '' } as unknown as PartenaireResponse]);
    component.commissionTaux.set([
      { produitId: 75, typePartenaire: 'AGENT', tauxCommission: 10, libTauxCommission: '%', active: true } as unknown as ProduitCommissionTauxResponse,
    ]);
  });

  function row(overrides: Record<string, string>, line = 2) {
    const raw = {
      nom: 'Kone',
      prenoms: 'Awa',
      telephone: `07000000${String(line).padStart(2, '0')}`,
      ville: 'Abidjan',
      produit: 'Assurance Auto',
      compagnie: 'AXA',
      partenaire: 'XYZ',
      prime: '30000',
      ...overrides,
    };
    return component['resolveRow'](raw, line);
  }

  it('sans colonne de montant : commission = prime × taux, part laissée à la configuration backend', () => {
    const parsed = row({});
    expect(parsed.errors).toEqual([]);
    expect(parsed.commissionAmount).toBe(3000);
    expect(parsed.partnerShare).toBeUndefined();
  });

  it('configuration en montant fixe : montant appliqué tel quel', () => {
    component.commissionTaux.set([
      { produitId: 75, typePartenaire: 'AGENT', tauxCommission: 5000, libTauxCommission: 'FIXE', active: true } as unknown as ProduitCommissionTauxResponse,
    ]);
    const parsed = row({});
    expect(parsed.commissionAmount).toBe(5000);
    expect(parsed.commissionRate).toBeCloseTo(16.67);
  });

  it('montant_commission et part_partenaire remplis : valeurs conservées telles quelles', () => {
    const parsed = row({ montant_commission: '2 000', part_partenaire: '2 500' });
    expect(parsed.errors).toEqual([]);
    expect(parsed.commissionAmount).toBe(2000);
    expect(parsed.partnerShare).toBe(2500);
  });

  it('refuse un montant décimal ou une part partenaire sans partenaire', () => {
    expect(row({ part_partenaire: '2500,50' }).errors[0]).toContain('Part partenaire invalide');
    expect(row({ partenaire: '', part_partenaire: '2500' }).errors).toContain('Part partenaire renseignée sans partenaire');
  });

  it('vente directe : part partenaire à 0', () => {
    component.commissionTaux.set([
      { produitId: 75, typePartenaire: 'DIRECT', tauxCommission: 10, libTauxCommission: '%', active: true } as unknown as ProduitCommissionTauxResponse,
    ]);
    expect(row({ partenaire: '' }).partnerShare).toBe(0);
  });

  it('cas 5 — 500 contrats avec part_partenaire = 2500 : chaque payload porte 2500', () => {
    const rows = Array.from({ length: 500 }, (_, i) => row({ part_partenaire: '2500' }, i + 2));
    component.clientGroups.set(component['groupRows'](rows));

    component.startImport();

    expect(submitSpy).toHaveBeenCalledTimes(500);
    const shares = submitSpy.mock.calls.flatMap(
      ([payload]) => payload.contracts.map((c) => c.partnerShare),
    );
    expect(shares).toHaveLength(500);
    expect(shares.every((s) => s === 2500)).toBe(true);
    // Taux envoyé en fraction (10 % → 0.1).
    expect(submitSpy.mock.calls[0][0].contracts[0].commissionRate).toBe(0.1);
  });
});
