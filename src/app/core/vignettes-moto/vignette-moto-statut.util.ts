import { VignetteMotoStatut } from './models/vignette-moto.model';

/** Variante sémantique par statut, utilisée par l'écran admin (`vignettes-moto.component.ts`,
 * badges `.dl-chip--*`). Anciennement partagée aussi avec la page publique de suivi, qui vit
 * désormais dans un projet Angular séparé (`projects/suivi-vignette/`, déploiement indépendant
 * confirmé par le backend le 2026-09-18) — elle a sa propre copie de cette logique, voir
 * projects/suivi-vignette/src/app/statut/suivi-statut.util.ts. */
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

export const ALL_STATUTS: VignetteMotoStatut[] = [
  'BROUILLON',
  'EN_ATTENTE_PAIEMENT',
  'PAYEE',
  'EN_TRAITEMENT',
  'EN_EDITION',
  'EDITEE',
  'EN_LIVRAISON',
  'LIVREE',
  'ANNULEE',
  'REJETEE',
];
