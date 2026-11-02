import {
  BeneficiaireRecompense,
  ControleAntifraudeStatut,
  ParrainageStatut,
  ProgrammeParrainageStatut,
  RecompenseParrainageStatut,
  TypeRecompenseParrainage,
} from '../../../core/parrainage/models/parrainage.model';

export const PARRAINAGE_STATUT_ORDER: ParrainageStatut[] = [
  'CREE',
  'INVITE',
  'INSCRIT',
  'ELIGIBLE',
  'CONTRAT_CREE',
  'CONTRAT_ACTIF',
  'RECOMPENSE_EN_ATTENTE',
  'RECOMPENSE_ATTRIBUEE',
  'REJETE',
  'ANNULE',
  'EXPIRE',
];

export const PARRAINAGE_STATUT_LABEL: Record<ParrainageStatut, string> = {
  CREE: 'Créé',
  INVITE: 'Invité',
  INSCRIT: 'Inscrit',
  ELIGIBLE: 'Éligible',
  CONTRAT_CREE: 'Contrat créé',
  CONTRAT_ACTIF: 'Contrat actif',
  RECOMPENSE_EN_ATTENTE: 'Récompense en attente',
  RECOMPENSE_ATTRIBUEE: 'Récompense attribuée',
  REJETE: 'Rejeté',
  ANNULE: 'Annulé',
  EXPIRE: 'Expiré',
};

export const PARRAINAGE_STATUT_VARIANT: Record<ParrainageStatut, string> = {
  CREE: 'neutral',
  INVITE: 'info',
  INSCRIT: 'info',
  ELIGIBLE: 'warning',
  CONTRAT_CREE: 'warning',
  CONTRAT_ACTIF: 'warning',
  RECOMPENSE_EN_ATTENTE: 'warning',
  RECOMPENSE_ATTRIBUEE: 'success',
  REJETE: 'danger',
  ANNULE: 'danger',
  EXPIRE: 'neutral',
};

export const PARRAINAGE_STATUTS_TERMINAUX: ParrainageStatut[] = ['RECOMPENSE_ATTRIBUEE', 'REJETE', 'ANNULE', 'EXPIRE'];

export const PROGRAMME_STATUT_LABEL: Record<ProgrammeParrainageStatut, string> = {
  BROUILLON: 'Brouillon',
  ACTIF: 'Actif',
  SUSPENDU: 'Suspendu',
  EXPIRE: 'Expiré',
  CLOTURE: 'Clôturé',
};

export const PROGRAMME_STATUT_VARIANT: Record<ProgrammeParrainageStatut, string> = {
  BROUILLON: 'neutral',
  ACTIF: 'success',
  SUSPENDU: 'warning',
  EXPIRE: 'neutral',
  CLOTURE: 'danger',
};

export const RECOMPENSE_STATUT_LABEL: Record<RecompenseParrainageStatut, string> = {
  EN_ATTENTE: 'En attente',
  ELIGIBLE: 'Éligible',
  APPROUVEE: 'Approuvée',
  ATTRIBUEE: 'Attribuée',
  UTILISEE: 'Utilisée',
  EXPIREE: 'Expirée',
  ANNULEE: 'Annulée',
  REVERSEE: 'Reversée',
};

export const RECOMPENSE_STATUT_VARIANT: Record<RecompenseParrainageStatut, string> = {
  EN_ATTENTE: 'neutral',
  ELIGIBLE: 'info',
  APPROUVEE: 'warning',
  ATTRIBUEE: 'success',
  UTILISEE: 'success',
  EXPIREE: 'neutral',
  ANNULEE: 'danger',
  REVERSEE: 'danger',
};

export const TYPE_RECOMPENSE_LABEL: Record<TypeRecompenseParrainage, string> = {
  CASHBACK: 'Cashback',
  REMISE: 'Remise',
  BONUS: 'Bonus',
  POINTS: 'Points',
  CADEAU: 'Cadeau',
  COUPON: 'Coupon',
  REDUCTION_PRIME: 'Réduction de prime',
  AUTRE: 'Autre',
};

export const BENEFICIAIRE_LABEL: Record<BeneficiaireRecompense, string> = {
  PARRAIN: 'Parrain',
  FILLEUL: 'Filleul',
  LES_DEUX: 'Les deux',
};

export const CONTROLE_ANTIFRAUDE_STATUT_VARIANT: Record<ControleAntifraudeStatut, string> = {
  REUSSI: 'success',
  ECHEC: 'danger',
  REVUE_REQUISE: 'warning',
};
