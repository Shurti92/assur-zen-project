import { AuditAction, AuditCanal, AuditStatut } from '../../core/audit-trail/models/audit-trail.model';

export const AUDIT_ACTION_ORDER: AuditAction[] = [
  'CREATE', 'UPDATE', 'DELETE', 'ACTIVATE', 'DEACTIVATE', 'VALIDATE', 'APPROVE', 'REJECT',
  'CANCEL', 'SUSPEND', 'RESTORE', 'EXPORT', 'DOWNLOAD', 'LOGIN', 'LOGOUT',
  'PASSWORD_CHANGE', 'PERMISSION_CHANGE',
  'CLOSE', 'ADD', 'REMOVE', 'REPLACE', 'TRANSFER', 'ARCHIVE', 'SUBMIT', 'REVIEW', 'AUTHORIZE', 'DISBURSE', 'REPAY',
  'OTHER',
];

export const AUDIT_ACTION_LABEL: Record<AuditAction, string> = {
  CREATE: 'Création',
  UPDATE: 'Modification',
  DELETE: 'Suppression',
  ACTIVATE: 'Activation',
  DEACTIVATE: 'Désactivation',
  VALIDATE: 'Validation',
  APPROVE: 'Approbation',
  REJECT: 'Rejet',
  CANCEL: 'Annulation',
  SUSPEND: 'Suspension',
  RESTORE: 'Restauration',
  EXPORT: 'Export',
  DOWNLOAD: 'Téléchargement',
  LOGIN: 'Connexion',
  LOGOUT: 'Déconnexion',
  PASSWORD_CHANGE: 'Changement de mot de passe',
  PERMISSION_CHANGE: 'Changement de permission',
  CLOSE: 'Clôture',
  ADD: 'Ajout',
  REMOVE: 'Retrait',
  REPLACE: 'Remplacement',
  TRANSFER: 'Transfert',
  ARCHIVE: 'Archivage',
  SUBMIT: 'Soumission',
  REVIEW: 'Examen',
  AUTHORIZE: 'Autorisation',
  DISBURSE: 'Décaissement',
  REPAY: 'Remboursement',
  OTHER: 'Autre',
};

// Convention couleur (spec §17), étendue aux actions supplémentaires exposées par le backend réel.
export const AUDIT_ACTION_VARIANT: Record<AuditAction, string> = {
  CREATE: 'success',
  UPDATE: 'info',
  DELETE: 'danger',
  ACTIVATE: 'success',
  DEACTIVATE: 'warning',
  VALIDATE: 'success',
  APPROVE: 'success',
  REJECT: 'danger',
  CANCEL: 'warning',
  SUSPEND: 'warning',
  RESTORE: 'info',
  EXPORT: 'info',
  DOWNLOAD: 'info',
  LOGIN: 'neutral',
  LOGOUT: 'neutral',
  PASSWORD_CHANGE: 'warning',
  PERMISSION_CHANGE: 'warning',
  CLOSE: 'neutral',
  ADD: 'success',
  REMOVE: 'warning',
  REPLACE: 'info',
  TRANSFER: 'info',
  ARCHIVE: 'neutral',
  SUBMIT: 'info',
  REVIEW: 'info',
  AUTHORIZE: 'success',
  DISBURSE: 'success',
  REPAY: 'success',
  OTHER: 'neutral',
};

export const AUDIT_SENSITIVE_ACTIONS: AuditAction[] = ['DELETE', 'APPROVE', 'REJECT', 'EXPORT', 'PERMISSION_CHANGE', 'RESTORE'];

export const AUDIT_STATUT_LABEL: Record<AuditStatut, string> = {
  SUCCES: 'Succès',
  ECHEC: 'Échec',
};

export const AUDIT_STATUT_VARIANT: Record<AuditStatut, string> = {
  SUCCES: 'success',
  ECHEC: 'danger',
};

export const AUDIT_CANAL_LABEL: Record<AuditCanal, string> = {
  WEB: 'Web',
  API: 'API',
  BACKOFFICE: 'Back-office',
  BATCH: 'Batch',
  SCHEDULER: 'Planificateur',
  SYSTEM: 'Système',
  INTEGRATION: 'Intégration',
};

export const AUDIT_CANAL_ORDER: AuditCanal[] = ['WEB', 'API', 'BACKOFFICE', 'BATCH', 'SCHEDULER', 'SYSTEM', 'INTEGRATION'];
