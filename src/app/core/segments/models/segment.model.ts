export type SegmentType = 'STATIC' | 'DYNAMIC';

export type SegmentRuleChamp =
  | 'PRODUIT'
  | 'COMPAGNIE'
  | 'PRIME'
  | 'DATE_ECHEANCE'
  | 'DATE_EFFET'
  | 'STATUT_CONTRAT'
  | 'MODE_PAIEMENT'
  | 'VILLE_CLIENT'
  | 'TYPE_CLIENT'
  | 'NB_SINISTRES'
  | 'NB_CONTRATS'
  | 'PALIER_FIDELITE';

export type SegmentRuleOperateur =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'GREATER_THAN'
  | 'GREATER_THAN_OR_EQUAL'
  | 'LESS_THAN'
  | 'LESS_THAN_OR_EQUAL'
  | 'CONTAINS'
  | 'IS_NULL'
  | 'IS_NOT_NULL'
  | 'EXPIRE_SOUS_N_JOURS';

export type SegmentRuleOperateurLogique = 'AND' | 'OR';

export interface SegmentResponse {
  id: number;
  uuid: string;
  code: string;
  nom: string;
  description: string | null;
  type: SegmentType;
  priorite: number | null;
  couleur: string | null;
  active: boolean;
  nbRegles: number;
  nbMembres: number;
  createdAt: string;
  updatedAt: string | null;
  createdBy: string;
  updatedBy: string | null;
}

export interface SegmentRequest {
  code: string;
  nom: string;
  description?: string;
  type: SegmentType;
  priorite?: number;
  couleur?: string;
  active?: boolean;
}

export interface SegmentPatchRequest {
  nom?: string;
  description?: string;
  type?: SegmentType;
  priorite?: number;
  couleur?: string;
  active?: boolean;
}

export interface SegmentRuleResponse {
  id: number;
  segmentId: number;
  champ: SegmentRuleChamp;
  operateur: SegmentRuleOperateur;
  valeur: string | null;
  ordre: number | null;
  groupe: number | null;
  operateurLogique: SegmentRuleOperateurLogique | null;
}

export interface SegmentRuleRequest {
  champ: SegmentRuleChamp;
  operateur: SegmentRuleOperateur;
  valeur?: string;
  ordre?: number;
  groupe?: number;
  operateurLogique?: SegmentRuleOperateurLogique;
}

export interface SegmentMembreResponse {
  id: number;
  segmentId: number;
  insuredId: number;
  insuredName: string;
  createdAt: string;
}

export interface SegmentMembresRequest {
  insuredIds: number[];
}

export interface SegmentPage {
  content: SegmentResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
