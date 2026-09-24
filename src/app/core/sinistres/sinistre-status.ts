import { SinistreStatus } from './models/sinistre.model';

export const SINISTRE_STATUS_ORDER: SinistreStatus[] = [
  'declare',
  'en_cours_analyse',
  'pieces_complementaires_requises',
  'expertise_en_cours',
  'accepte',
  'rejete',
  'indemnise',
  'cloture',
];

export const SINISTRE_STATUS_LABEL: Record<SinistreStatus, string> = {
  declare: 'Déclaré',
  en_cours_analyse: "En cours d'analyse",
  pieces_complementaires_requises: 'Pièces complémentaires requises',
  expertise_en_cours: 'Expertise en cours',
  accepte: 'Accepté',
  rejete: 'Rejeté',
  indemnise: 'Indemnisé',
  cloture: 'Clôturé',
};

export const SINISTRE_STATUS_VARIANT: Record<SinistreStatus, string> = {
  declare: 'neutral',
  en_cours_analyse: 'info',
  pieces_complementaires_requises: 'warning',
  expertise_en_cours: 'info',
  accepte: 'success',
  rejete: 'danger',
  indemnise: 'success',
  cloture: 'neutral',
};

/** Statuses still considered "open" / needing follow-up — used to default the
 * "Suivi sinistre" page to an actionable subset rather than the full archive. */
export const SINISTRE_OPEN_STATUSES: SinistreStatus[] = [
  'declare',
  'en_cours_analyse',
  'pieces_complementaires_requises',
  'expertise_en_cours',
];
