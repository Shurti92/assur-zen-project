/** ContractRequest.commissionRate est une FRACTION côté backend (0.10 = 10 %), confirmé le
 * 2026-09-25. Les écrans saisissent et affichent un pourcentage : on convertit à la frontière.
 * À ne pas confondre avec tauxCommission des configurations (produits-commission-taux,
 * produits-commission-commercial), qui est un pourcentage si libTauxCommission = "%", un
 * montant fixe en FCFA sinon — voir configuredCommissionAmount(). */
export function percentToApiRate(percent: number): number {
  return Math.round(percent * 100) / 10000;
}

/** Taux d'un contrat renvoyé par l'API, en pourcentage pour l'affichage. Les contrats créés
 * avant le 2026-09-25 ont été enregistrés en pourcentage (10.0000 pour 10 %) et sont laissés
 * tels quels côté base : une valeur > 1 est donc déjà un pourcentage. */
export function apiRateToPercent(rate: number | null | undefined): number {
  if (rate === null || rate === undefined) return 0;
  return rate > 1 ? rate : Math.round(rate * 10000) / 100;
}

/** Montant d'une commission configurée (taux produit ou commission commerciale) :
 * pourcentage de la prime si libTauxCommission = "%", sinon montant fixe en FCFA. */
export function configuredCommissionAmount(
  config: { tauxCommission: number; libTauxCommission: string },
  premium: number,
): number {
  return isPercentConfig(config) ? Math.round((premium * config.tauxCommission) / 100) : Math.round(config.tauxCommission);
}

/** Même règle stricte que le backend ("%".equals(lib)) : « % » avec espace, « 10% » ou « Taux »
 * sont lus comme un MONTANT FIXE. */
export function isPercentConfig(config: { libTauxCommission: string }): boolean {
  return config.libTauxCommission === '%';
}

/** Unités acceptées pour une configuration de commission (validées côté backend depuis le
 * 2026-09-25) : pourcentage de la prime ou montant fixe en FCFA. */
export const COMMISSION_UNITS = [
  { value: '%', label: 'Pourcentage (%)' },
  { value: 'FCFA', label: 'Montant fixe (FCFA)' },
] as const;

export function isStandardCommissionUnit(lib: string): boolean {
  return COMMISSION_UNITS.some((unit) => unit.value === lib);
}

/** « 15,00 % » ou « 2 000 FCFA » selon l'unité de la configuration. */
export function formatCommissionValue(config: { tauxCommission: number; libTauxCommission: string }): string {
  return isPercentConfig(config)
    ? `${new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(config.tauxCommission)} %`
    : `${new Intl.NumberFormat('fr-FR').format(config.tauxCommission)} FCFA`;
}
