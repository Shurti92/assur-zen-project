export type CampagneType = 'SMS' | 'EMAIL' | 'SMS_EMAIL' | 'WHATSAPP' | 'PUSH' | 'PDF';
export type CampagneStatut = 'BROUILLON' | 'PLANIFIEE' | 'EN_COURS' | 'TERMINEE' | 'ANNULEE';
export type CampagneCible =
  | 'TOUS_CLIENTS'
  | 'CLIENTS_FIDELES'
  | 'ECHEANCE_PROCHE'
  | 'NON_RENOUVELES'
  | 'SEGMENT_PERSONNALISE'
  | 'SEGMENT_DYNAMIQUE';
/** Élargi le 2026-08-21 (voir /v3/api-docs) pour matcher les canaux déjà supportés au niveau
 * campagne (CampagneType). */
export type CampagneDestinataireCanal = 'SMS' | 'EMAIL' | 'WHATSAPP' | 'PUSH' | 'PDF';
export type CampagneDestinataireStatutEnvoi = 'EN_ATTENTE' | 'ENVOYE' | 'ECHEC';

export interface CampagneMarketingResponse {
  id: number;
  code: string;
  nom: string;
  description: string | null;
  type: CampagneType;
  statut: CampagneStatut;
  cible: CampagneCible;
  messageContenu: string;
  sujetEmail: string | null;
  dateDebut: string;
  dateFin: string;
  budgetPrevu: number | null;
  createdById: number;
  createdBy: string;
  segmentId: number | null;
  segmentNom: string | null;
  templateId: number | null;
  templateNom: string | null;
  nbDestinataires: number;
  nbEnvoyes: number;
  nbEchecs: number;
  createdAt: string;
}

export interface CampagneMarketingRequest {
  nom: string;
  description?: string;
  type: CampagneType;
  cible: CampagneCible;
  messageContenu: string;
  sujetEmail?: string;
  dateDebut: string;
  dateFin: string;
  budgetPrevu?: number;
  segmentId?: number;
  templateId?: number;
}

export interface CampagneDestinataireResponse {
  id: number;
  campagneId: number;
  insuredId: number;
  insuredName: string;
  canal: CampagneDestinataireCanal;
  statutEnvoi: CampagneDestinataireStatutEnvoi;
  dateEnvoi: string | null;
  erreurMessage: string | null;
  createdAt: string;
}

export interface CampagneLancementRequest {
  insuredIds?: number[];
}

export interface CampagnePage {
  content: CampagneMarketingResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
