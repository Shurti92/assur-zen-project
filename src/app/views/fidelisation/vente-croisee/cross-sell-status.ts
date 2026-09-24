import { CrossSellPriorite, CrossSellStatut } from '../../../core/cross-sell/models/cross-sell.model';

export const CROSS_SELL_STATUT_ORDER: CrossSellStatut[] = [
  'NOUVELLE',
  'QUALIFIEE',
  'AFFECTEE',
  'EN_COURS',
  'ACCEPTEE',
  'REFUSEE',
  'CONVERTIE',
  'EXPIREE',
  'ANNULEE',
];

export const CROSS_SELL_STATUT_LABEL: Record<CrossSellStatut, string> = {
  NOUVELLE: 'Nouvelle',
  QUALIFIEE: 'Qualifiée',
  AFFECTEE: 'Affectée',
  EN_COURS: 'En cours',
  ACCEPTEE: 'Acceptée',
  REFUSEE: 'Refusée',
  CONVERTIE: 'Convertie',
  EXPIREE: 'Expirée',
  ANNULEE: 'Annulée',
};

export const CROSS_SELL_STATUT_VARIANT: Record<CrossSellStatut, string> = {
  NOUVELLE: 'info',
  QUALIFIEE: 'info',
  AFFECTEE: 'warning',
  EN_COURS: 'warning',
  ACCEPTEE: 'success',
  REFUSEE: 'danger',
  CONVERTIE: 'success',
  EXPIREE: 'neutral',
  ANNULEE: 'danger',
};

export const CROSS_SELL_STATUTS_TERMINAUX: CrossSellStatut[] = ['REFUSEE', 'CONVERTIE', 'EXPIREE', 'ANNULEE'];

export const CROSS_SELL_PRIORITE_LABEL: Record<CrossSellPriorite, string> = {
  FAIBLE: 'Faible',
  NORMALE: 'Normale',
  HAUTE: 'Haute',
  CRITIQUE: 'Critique',
};

export const CROSS_SELL_PRIORITE_VARIANT: Record<CrossSellPriorite, string> = {
  FAIBLE: 'neutral',
  NORMALE: 'info',
  HAUTE: 'warning',
  CRITIQUE: 'danger',
};
