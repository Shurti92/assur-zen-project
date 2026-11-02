import { ReclamationCanal, ReclamationPriorite, ReclamationStatut, ReclamationType } from '../../../../core/reclamations/models/reclamation.model';

export const RECLAMATION_STATUT_ORDER: ReclamationStatut[] = [
  'NOUVELLE',
  'EN_ANALYSE',
  'AFFECTEE',
  'EN_COURS',
  'EN_ATTENTE_CLIENT',
  'RESOLUE',
  'FERMEE',
  'ANNULEE',
];

export const RECLAMATION_STATUT_LABEL: Record<ReclamationStatut, string> = {
  NOUVELLE: 'Nouvelle',
  EN_ANALYSE: 'En analyse',
  AFFECTEE: 'Affectée',
  EN_COURS: 'En cours',
  EN_ATTENTE_CLIENT: 'En attente client',
  RESOLUE: 'Résolue',
  FERMEE: 'Fermée',
  ANNULEE: 'Annulée',
};

export const RECLAMATION_STATUT_VARIANT: Record<ReclamationStatut, string> = {
  NOUVELLE: 'info',
  EN_ANALYSE: 'warning',
  AFFECTEE: 'warning',
  EN_COURS: 'warning',
  EN_ATTENTE_CLIENT: 'neutral',
  RESOLUE: 'success',
  FERMEE: 'success',
  ANNULEE: 'danger',
};

export const RECLAMATION_STATUTS_TERMINAUX: ReclamationStatut[] = ['FERMEE', 'ANNULEE'];

export const RECLAMATION_TYPE_LABEL: Record<ReclamationType, string> = {
  SERVICE: 'Service',
  PAIEMENT: 'Paiement',
  SINISTRE: 'Sinistre',
  SOUSCRIPTION: 'Souscription',
  DOCUMENT: 'Document',
  RELATION_CLIENT: 'Relation client',
};

export const RECLAMATION_PRIORITE_LABEL: Record<ReclamationPriorite, string> = {
  FAIBLE: 'Faible',
  NORMALE: 'Normale',
  HAUTE: 'Haute',
  CRITIQUE: 'Critique',
};

export const RECLAMATION_PRIORITE_VARIANT: Record<ReclamationPriorite, string> = {
  FAIBLE: 'neutral',
  NORMALE: 'info',
  HAUTE: 'warning',
  CRITIQUE: 'danger',
};

export const RECLAMATION_CANAL_LABEL: Record<ReclamationCanal, string> = {
  ENQUETE_SATISFACTION: 'Enquête de satisfaction',
  APPEL_TELEPHONIQUE: 'Appel téléphonique',
  EMAIL: 'Email',
  WHATSAPP: 'WhatsApp',
  AGENT: 'Agent',
  COURTIER: 'Courtier',
  PORTAIL_CLIENT: 'Portail client',
};
