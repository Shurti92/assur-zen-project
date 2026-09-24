import { VignetteMotoStatut } from '../models/suivi-vignette.model';

/** Copie allégée de assur-zen-core/src/app/core/vignettes-moto/vignette-moto-statut.util.ts —
 * uniquement la partie utile à cette page publique (voir la remarque de duplication dans
 * models/suivi-vignette.model.ts). */

export const STATUT_VARIANT: Record<VignetteMotoStatut, string> = {
  BROUILLON: 'neutral',
  EN_ATTENTE_PAIEMENT: 'warning',
  PAYEE: 'info',
  EN_TRAITEMENT: 'info',
  EN_EDITION: 'info',
  EDITEE: 'info',
  EN_LIVRAISON: 'info',
  LIVREE: 'success',
  ANNULEE: 'danger',
  REJETEE: 'danger',
};

export type SuiviPublicEtapeStatut = Exclude<VignetteMotoStatut, 'ANNULEE' | 'REJETEE'>;

/** Les 8 statuts non-terminaux, dans l'ordre chronologique — une étape par statut réel (pas le
 * regroupement à 6 jalons de l'écran admin de assur-zen-core, qui ne concerne pas cette page). */
export const SUIVI_PUBLIC_ETAPES: SuiviPublicEtapeStatut[] = [
  'BROUILLON',
  'EN_ATTENTE_PAIEMENT',
  'PAYEE',
  'EN_TRAITEMENT',
  'EN_EDITION',
  'EDITEE',
  'EN_LIVRAISON',
  'LIVREE',
];

/** Libellé de secours si une étape n'apparaît pas encore dans `historique[]` — le libellé réel
 * d'une étape déjà franchie vient toujours de `historique[].libelle` (texte backend). */
export const SUIVI_PUBLIC_ETAPE_LIBELLE_SECOURS: Record<SuiviPublicEtapeStatut, string> = {
  BROUILLON: 'Demande créée',
  EN_ATTENTE_PAIEMENT: 'Paiement en attente',
  PAYEE: 'Paiement confirmé',
  EN_TRAITEMENT: 'Demande en cours de traitement',
  EN_EDITION: "Vignette en cours d'édition",
  EDITEE: 'Vignette éditée',
  EN_LIVRAISON: 'En attente de livraison',
  LIVREE: 'Vignette remise',
};

export type SuiviPublicEtapeState = 'done' | 'current' | 'upcoming';

export function suiviPublicEtapeState(etape: SuiviPublicEtapeStatut, statutCourant: VignetteMotoStatut): SuiviPublicEtapeState {
  const etapes: readonly VignetteMotoStatut[] = SUIVI_PUBLIC_ETAPES;
  const etapeIndex = etapes.indexOf(etape);
  const courantIndex = etapes.indexOf(statutCourant);
  if (etapeIndex === -1 || courantIndex === -1 || etapeIndex < courantIndex) return 'done';
  if (etapeIndex === courantIndex) return 'current';
  return 'upcoming';
}
