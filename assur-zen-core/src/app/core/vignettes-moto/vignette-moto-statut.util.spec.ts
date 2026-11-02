import { ALL_STATUTS, STATUT_VARIANT } from './vignette-moto-statut.util';

describe('vignette-moto-statut.util', () => {
  it('STATUT_VARIANT couvre les 10 statuts', () => {
    expect(Object.keys(STATUT_VARIANT).sort()).toEqual([...ALL_STATUTS].sort());
  });
});
