import { apiRateToPercent, configuredCommissionAmount, percentToApiRate } from './commission-rate.util';

describe('commission-rate.util', () => {
  it('convertit un pourcentage en fraction pour l’API', () => {
    expect(percentToApiRate(10)).toBe(0.1);
    expect(percentToApiRate(12.5)).toBe(0.125);
  });

  it('affiche une fraction en pourcentage, et laisse tels quels les anciens contrats déjà en %', () => {
    expect(apiRateToPercent(0.1)).toBe(10);
    expect(apiRateToPercent(10)).toBe(10);
    expect(apiRateToPercent(null)).toBe(0);
  });

  it('configuration : pourcentage si libellé "%", montant fixe sinon', () => {
    expect(configuredCommissionAmount({ tauxCommission: 10, libTauxCommission: '%' }, 25000)).toBe(2500);
    expect(configuredCommissionAmount({ tauxCommission: 2000, libTauxCommission: 'FIXE' }, 25000)).toBe(2000);
  });
});
