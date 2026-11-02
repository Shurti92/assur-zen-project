export type SatisfactionImportTargetField =
  | 'RESPONSE_DATE'
  | 'CUSTOMER_NAME'
  | 'PHONE'
  | 'EMAIL'
  | 'POLICY_NUMBER'
  | 'SCORE'
  | 'COMMENT';

export type SatisfactionImportStatut = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED' | 'CANCELLED';

export type SatisfactionImportDuplicateStrategy = 'IGNORER' | 'ERREUR' | 'ECRASER';

export type SatisfactionImportErrorType =
  | 'CONTRAT_INTROUVABLE'
  | 'CLIENT_INTROUVABLE'
  | 'TELEPHONE_INVALIDE'
  | 'EMAIL_INVALIDE'
  | 'NOTE_INVALIDE'
  | 'COLONNE_OBLIGATOIRE_ABSENTE'
  | 'ENCODAGE_INVALIDE'
  | 'FORMAT_DATE_INVALIDE'
  | 'DOUBLON'
  | 'ERREUR_INCONNUE';

export interface SatisfactionImportMappingProfileResponse {
  id: number;
  uuid: string;
  code: string;
  nom: string;
  description: string | null;
  active: boolean;
  nbMappings: number;
  createdAt: string;
  updatedAt: string | null;
  createdBy: string;
  updatedBy: string | null;
}

export interface SatisfactionImportMappingProfileRequest {
  code: string;
  nom: string;
  description?: string;
  active?: boolean;
}

export interface SatisfactionImportMappingResponse {
  id: number;
  profileId: number;
  csvColumnName: string;
  targetField: SatisfactionImportTargetField;
  obligatoire: boolean | null;
  ordre: number | null;
}

export interface SatisfactionImportMappingRequest {
  csvColumnName: string;
  targetField: SatisfactionImportTargetField;
  obligatoire?: boolean;
  ordre?: number;
}

export interface SatisfactionImportMappingValueResponse {
  id: number;
  mappingId: number;
  valeurSource: string;
  valeurCible: string;
}

export interface SatisfactionImportMappingValueRequest {
  valeurSource: string;
  valeurCible: string;
}

export interface SatisfactionImportResponse {
  id: number;
  uuid: string;
  code: string;
  mappingProfileId: number;
  mappingProfileNom: string;
  source: string | null;
  surveyId: string | null;
  fileName: string;
  delimiter: string;
  encoding: string;
  hasHeader: boolean;
  duplicateStrategy: SatisfactionImportDuplicateStrategy;
  statut: SatisfactionImportStatut;
  totalLignes: number;
  lignesIgnorees: number;
  lignesReussies: number;
  lignesEnErreur: number;
  dateDebutTraitement: string | null;
  dateFinTraitement: string | null;
  executeParId: number | null;
  executePar: string | null;
  createdAt: string;
}

export interface SatisfactionImportUploadOptions {
  mappingProfileId: number;
  surveyId?: string;
  source?: string;
  delimiter?: string;
  encoding?: string;
  hasHeader?: boolean;
  duplicateStrategy?: SatisfactionImportDuplicateStrategy;
  executeImmediately?: boolean;
}

export interface SatisfactionImportReportResponse {
  importId: number;
  code: string;
  statut: SatisfactionImportStatut;
  totalLignes: number;
  lignesIgnorees: number;
  lignesReussies: number;
  lignesEnErreur: number;
  tauxReussite: number;
  promoteurs: number;
  passifs: number;
  detracteurs: number;
  scoreNpsMoyen: number | null;
  erreursParType: Record<string, number>;
}

export interface SatisfactionImportErrorResponse {
  id: number;
  importId: number;
  ligneNumero: number;
  colonneEnErreur: string | null;
  valeurBrute: string | null;
  typeErreur: SatisfactionImportErrorType;
  message: string;
  createdAt: string;
}

export interface SatisfactionImportPage {
  content: SatisfactionImportResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
