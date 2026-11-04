export type CiOperator = 'ORANGE' | 'MTN' | 'MOOV';

const OPERATOR_BY_PREFIX: Record<string, CiOperator> = { '07': 'ORANGE', '05': 'MTN', '01': 'MOOV' };

export const CI_OPERATOR_LABEL: Record<CiOperator, string> = { ORANGE: 'Orange', MTN: 'MTN', MOOV: 'Moov' };

/**
 * Opérateur d'un MOBILE ivoirien valide (10 chiffres commençant par 01 Moov, 05 MTN ou 07 Orange),
 * sinon null (numéro fixe 21/25/27, incomplet ou invalide : pas de SMS). Même règle que la gateway,
 * qui fait foi à l'envoi.
 */
export function ciMobileOperator(phone: string | null | undefined): CiOperator | null {
  const digits = (phone ?? '').replace(/\D/g, '');
  return /^(01|05|07)\d{8}$/.test(digits) ? OPERATOR_BY_PREFIX[digits.slice(0, 2)] : null;
}

/** « 0701020304 » → « 07 01 02 03 04 » (affichage). */
export function formatCiPhone(phone: string | null | undefined): string {
  const digits = (phone ?? '').replace(/\D/g, '');
  return digits.length === 10 ? digits.replace(/(\d{2})(?=\d)/g, '$1 ') : (phone ?? '');
}
