export type CrossSellPriorite = 'FAIBLE' | 'NORMALE' | 'HAUTE' | 'CRITIQUE';

export type CrossSellStatut =
  | 'NOUVELLE'
  | 'QUALIFIEE'
  | 'AFFECTEE'
  | 'EN_COURS'
  | 'ACCEPTEE'
  | 'REFUSEE'
  | 'CONVERTIE'
  | 'EXPIREE'
  | 'ANNULEE';

export interface CrossSellOpportunityRequest {
  insuredId: number;
  contratId?: number;
  produitActuelId?: number;
  produitRecommandeId: number;
  score: number;
  priorite?: CrossSellPriorite;
  commentaire?: string;
}

export interface CrossSellOpportunityResponse {
  id: number;
  code: string;
  insuredId: number;
  insuredName: string;
  contratId: number | null;
  contratPolicyNumber: string | null;
  produitActuelId: number | null;
  produitActuelNom: string | null;
  produitRecommandeId: number;
  produitRecommandeNom: string;
  score: number;
  regleId: number | null;
  regleNom: string | null;
  commercialId: number | null;
  commercialNom: string | null;
  campagneId: number | null;
  campagneNom: string | null;
  priorite: CrossSellPriorite;
  statut: CrossSellStatut;
  motif: string | null;
  commentaire: string | null;
  dateAffectation: string | null;
  dateTraitement: string | null;
  dateCloture: string | null;
  createdAt: string;
  lastUpdate: string;
}

export interface CrossSellOpportunityPage {
  content: CrossSellOpportunityResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CrossSellStatutRequest {
  statut: CrossSellStatut;
  motif?: string;
}

export interface CrossSellAffectationRequest {
  commercialId: number;
}

export type CrossSellHistoryEventType =
  | 'CREATION'
  | 'MODIFICATION'
  | 'AFFECTATION'
  | 'CHANGEMENT_STATUT'
  | 'ACCEPTATION'
  | 'REFUS'
  | 'CONVERSION'
  | 'ANNULATION';

export interface CrossSellHistoryResponse {
  id: number;
  opportuniteId: number;
  typeEvenement: CrossSellHistoryEventType;
  description: string | null;
  auteurId: number | null;
  auteurNom: string | null;
  createdAt: string;
}

export type CrossSellConditionChamp =
  | 'ANCIENNETE_ANNEES'
  | 'PRIME_ANNUELLE'
  | 'NOMBRE_CONTRATS'
  | 'NOMBRE_PRODUITS_DISTINCTS'
  | 'ECHEANCES_IMPAYEES'
  | 'NPS'
  | 'RECLAMATIONS'
  | 'EST_VIP'
  | 'TYPE_CLIENT'
  | 'CANAL';

export type CrossSellConditionOperateur =
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

export interface CrossSellRuleRequest {
  code: string;
  nom: string;
  description?: string;
  produitDeclencheurId?: number;
  produitRecommandeId: number;
  priorite?: CrossSellPriorite;
  poidsScore: number;
  ordre?: number;
  dateDebut?: string;
  dateFin?: string;
  actif?: boolean;
}

export interface CrossSellRuleResponse {
  id: number;
  code: string;
  nom: string;
  description: string | null;
  produitDeclencheurId: number | null;
  produitDeclencheurNom: string | null;
  produitRecommandeId: number;
  produitRecommandeNom: string;
  priorite: CrossSellPriorite;
  poidsScore: number;
  ordre: number | null;
  dateDebut: string | null;
  dateFin: string | null;
  actif: boolean;
}

export interface CrossSellRuleConditionRequest {
  champ: CrossSellConditionChamp;
  operateur: CrossSellConditionOperateur;
  valeur: string;
  ordre?: number;
}

export interface CrossSellRuleConditionResponse {
  id: number;
  regleId: number;
  champ: CrossSellConditionChamp;
  operateur: CrossSellConditionOperateur;
  valeur: string;
  ordre: number | null;
}

export interface CrossSellParameterRequest {
  cle: string;
  valeur: string;
  description?: string;
}

export interface CrossSellParameterResponse {
  id: number;
  cle: string;
  valeur: string;
  description: string | null;
}
