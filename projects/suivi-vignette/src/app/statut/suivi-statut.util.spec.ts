import { SUIVI_PUBLIC_ETAPES, suiviPublicEtapeState } from './suivi-statut.util';

describe('suivi-statut.util', () => {
  it('couvre les 8 statuts non-terminaux, sans ANNULEE/REJETEE', () => {
    expect(SUIVI_PUBLIC_ETAPES).toEqual([
      'BROUILLON',
      'EN_ATTENTE_PAIEMENT',
      'PAYEE',
      'EN_TRAITEMENT',
      'EN_EDITION',
      'EDITEE',
      'EN_LIVRAISON',
      'LIVREE',
    ]);
  });

  it('marque les étapes antérieures "done", la courante "current", les futures "upcoming"', () => {
    expect(suiviPublicEtapeState('BROUILLON', 'EN_EDITION')).toBe('done');
    expect(suiviPublicEtapeState('EN_EDITION', 'EN_EDITION')).toBe('current');
    expect(suiviPublicEtapeState('LIVREE', 'EN_EDITION')).toBe('upcoming');
  });
});
