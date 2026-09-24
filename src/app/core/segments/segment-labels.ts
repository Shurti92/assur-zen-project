import { SegmentRuleChamp, SegmentRuleOperateur, SegmentType } from './models/segment.model';

export const SEGMENT_TYPE_LABEL: Record<SegmentType, string> = { STATIC: 'Statique', DYNAMIC: 'Dynamique' };

export const SEGMENT_TYPE_ORDER: SegmentType[] = ['STATIC', 'DYNAMIC'];

export const SEGMENT_CHAMP_LABEL: Record<SegmentRuleChamp, string> = {
  PRODUIT: 'Produit',
  COMPAGNIE: 'Compagnie',
  PRIME: 'Prime',
  DATE_ECHEANCE: "Date d'échéance",
  DATE_EFFET: "Date d'effet",
  STATUT_CONTRAT: 'Statut du contrat',
  MODE_PAIEMENT: 'Mode de paiement',
  VILLE_CLIENT: 'Ville du client',
  TYPE_CLIENT: 'Type de client',
  NB_SINISTRES: 'Nombre de sinistres',
  NB_CONTRATS: 'Nombre de contrats',
  PALIER_FIDELITE: 'Palier de fidélité',
};

export const SEGMENT_CHAMP_ORDER: SegmentRuleChamp[] = [
  'PRODUIT',
  'COMPAGNIE',
  'PRIME',
  'DATE_ECHEANCE',
  'DATE_EFFET',
  'STATUT_CONTRAT',
  'MODE_PAIEMENT',
  'VILLE_CLIENT',
  'TYPE_CLIENT',
  'NB_SINISTRES',
  'NB_CONTRATS',
  'PALIER_FIDELITE',
];

export const SEGMENT_OPERATEUR_LABEL: Record<SegmentRuleOperateur, string> = {
  EQUALS: 'Égal à',
  NOT_EQUALS: 'Différent de',
  GREATER_THAN: 'Supérieur à',
  GREATER_THAN_OR_EQUAL: 'Supérieur ou égal à',
  LESS_THAN: 'Inférieur à',
  LESS_THAN_OR_EQUAL: 'Inférieur ou égal à',
  CONTAINS: 'Contient',
  IS_NULL: 'Est vide',
  IS_NOT_NULL: "N'est pas vide",
  EXPIRE_SOUS_N_JOURS: 'Expire sous N jours',
};

export const SEGMENT_OPERATEUR_ORDER: SegmentRuleOperateur[] = [
  'EQUALS',
  'NOT_EQUALS',
  'GREATER_THAN',
  'GREATER_THAN_OR_EQUAL',
  'LESS_THAN',
  'LESS_THAN_OR_EQUAL',
  'CONTAINS',
  'IS_NULL',
  'IS_NOT_NULL',
  'EXPIRE_SOUS_N_JOURS',
];
