import { HttpErrorResponse } from '@angular/common/http';

/** Corps d'erreur commun du backend : { code, message, timestamp }. */
interface ApiErrorBody {
  code?: string;
  message?: string;
}

export const SYCAPAY_UNAVAILABLE_MESSAGE = 'Service de paiement indisponible, réessayez plus tard.';

/** Traduit une erreur HTTP SycaPay en message affichable (contrat backend du 2026-09-23) :
 * - 502 SYCAPAY_UNAVAILABLE : SycaPay injoignable, désactivé (SYCAPAY_ENABLED=false) ou sans
 *   identifiants — message générique imposé, jamais le détail technique ;
 * - 409 SYCAPAY_DUPLICATE_PAYMENT : un paiement est déjà en cours/effectué pour ce contrat ;
 * - 409 sur reconcile : la transaction n'est plus PROCESSING/UNKNOWN (statut déjà définitif) ;
 * - sinon le message du backend s'il existe, à défaut `fallback`. */
export function sycapayErrorMessage(err: unknown, fallback: string, context: 'pay' | 'reconcile' | 'read' = 'read'): string {
  if (!(err instanceof HttpErrorResponse)) return fallback;
  const body = (err.error ?? null) as ApiErrorBody | null;

  if (err.status === 502 || body?.code === 'SYCAPAY_UNAVAILABLE') return SYCAPAY_UNAVAILABLE_MESSAGE;
  if (body?.code === 'SYCAPAY_DUPLICATE_PAYMENT') {
    return 'Un paiement est déjà en cours ou effectué pour ce contrat. Vérifiez l\'historique SycaPay avant toute nouvelle tentative.';
  }
  if (err.status === 409 && context === 'reconcile') {
    return 'Cette transaction n\'est plus en attente de confirmation : son statut est déjà définitif. Rechargez la liste.';
  }
  return body?.message || fallback;
}
