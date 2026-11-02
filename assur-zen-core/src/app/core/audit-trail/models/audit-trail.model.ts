// Contrat réel confirmé via /v3/api-docs le 2026-08-09 (le backend a implémenté l'Audit Trail
// Engine proposé dans docs/api-contracts/audit-trail.md, avec des noms de schéma différents —
// HistoriqueModificationResponse, pas AuditLogResponse). Vérifier ce fichier avant toute
// nouvelle intégration si le backend évolue encore.

export type AuditAction =
  | 'CREATE'
  | 'UPDATE'
  | 'DELETE'
  | 'ACTIVATE'
  | 'DEACTIVATE'
  | 'VALIDATE'
  | 'REJECT'
  | 'APPROVE'
  | 'CANCEL'
  | 'SUSPEND'
  | 'RESTORE'
  | 'EXPORT'
  | 'DOWNLOAD'
  | 'LOGIN'
  | 'LOGOUT'
  | 'PASSWORD_CHANGE'
  | 'PERMISSION_CHANGE'
  /** Valeurs confirmées via /v3/api-docs le 2026-09-03, ajoutées avec le module Sorties de
   * fonds/Prêts (SUBMIT/REVIEW/AUTHORIZE/DISBURSE/REPAY) et déjà présentes côté schéma pour
   * d'autres modules (CLOSE/ADD/REMOVE/REPLACE/TRANSFER/ARCHIVE — Flotte notamment). */
  | 'CLOSE'
  | 'ADD'
  | 'REMOVE'
  | 'REPLACE'
  | 'TRANSFER'
  | 'ARCHIVE'
  | 'SUBMIT'
  | 'REVIEW'
  | 'AUTHORIZE'
  | 'DISBURSE'
  | 'REPAY'
  | 'OTHER';

export type AuditStatut = 'SUCCES' | 'ECHEC';

export type AuditCanal = 'WEB' | 'API' | 'BACKOFFICE' | 'BATCH' | 'SCHEDULER' | 'SYSTEM' | 'INTEGRATION';

export interface HistoriqueModificationResponse {
  id: number;
  reference: string | null;
  correlationId: string | null;
  module: string | null;
  entityName: string;
  entityId: string | null;
  action: AuditAction;
  description: string | null;
  motif: string | null;
  ancienneValeur: string | null;
  nouvelleValeur: string | null;
  champsModifies: string | null;
  userId: number | null;
  username: string | null;
  userRole: string | null;
  ipAddress: string | null;
  canal: AuditCanal | null;
  statut: AuditStatut;
  errorMessage: string | null;
  createdAt: string;
}

export interface AuditChangesResponse {
  id: number;
  entityName: string;
  entityId: string | null;
  ancienneValeur: string | null;
  nouvelleValeur: string | null;
  champsModifies: string | null;
}

export interface HistoriqueModificationPage {
  content: HistoriqueModificationResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditCompteItem {
  libelle: string;
  nombre: number;
}

export interface AuditJourItem {
  jour: string;
  nombre: number;
}

export interface HistoriqueModificationStatistiquesResponse {
  total: number;
  echecs: number;
  exports: number;
  connexions: number;
  parAction: AuditCompteItem[];
  parModule: AuditCompteItem[];
  parJour: AuditJourItem[];
  parUtilisateur: AuditCompteItem[];
}

export interface AuditLogFilter {
  module?: string;
  entityName?: string;
  entityId?: string;
  action?: AuditAction;
  userId?: number;
  statut?: AuditStatut;
  dateDebut?: string;
  dateFin?: string;
}
