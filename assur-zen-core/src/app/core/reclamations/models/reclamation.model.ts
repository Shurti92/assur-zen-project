export type ReclamationCanal =
  | 'ENQUETE_SATISFACTION'
  | 'APPEL_TELEPHONIQUE'
  | 'EMAIL'
  | 'WHATSAPP'
  | 'AGENT'
  | 'COURTIER'
  | 'PORTAIL_CLIENT';

export type ReclamationType = 'SERVICE' | 'PAIEMENT' | 'SINISTRE' | 'SOUSCRIPTION' | 'DOCUMENT' | 'RELATION_CLIENT';

export type ReclamationPriorite = 'FAIBLE' | 'NORMALE' | 'HAUTE' | 'CRITIQUE';

export type ReclamationStatut =
  | 'NOUVELLE'
  | 'EN_ANALYSE'
  | 'AFFECTEE'
  | 'EN_COURS'
  | 'EN_ATTENTE_CLIENT'
  | 'RESOLUE'
  | 'FERMEE'
  | 'ANNULEE';

export interface ReclamationRequest {
  insuredId: number;
  contratId?: number;
  produitId?: number;
  sinistreId?: number;
  canal: ReclamationCanal;
  type: ReclamationType;
  priorite: ReclamationPriorite;
  description: string;
}

export interface ReclamationResponse {
  id: number;
  numero: string;
  insuredId: number;
  /** Nullable depuis le 2026-09-14 : le backend renvoie désormais null (au lieu d'un 500)
   * quand l'entité référencée par la FK a été supprimée (hard-delete). */
  insuredName: string | null;
  contratId: number | null;
  contratPolicyNumber: string | null;
  produitId: number | null;
  produitNom: string | null;
  sinistreId: number | null;
  sinistreNumero: string | null;
  enqueteId: number | null;
  enqueteCode: string | null;
  canal: ReclamationCanal;
  type: ReclamationType;
  priorite: ReclamationPriorite;
  statut: ReclamationStatut;
  description: string;
  gestionnaireId: number | null;
  gestionnaireNom: string | null;
  service: string | null;
  dateAffectation: string | null;
  dateResolution: string | null;
  dateCloture: string | null;
  motifCloture: string | null;
  creeeAutomatiquement: boolean;
  /** Ajouté au DTO côté backend le 2026-08-21. */
  notificationEnvoyee?: boolean;
  createdAt: string;
  lastUpdate: string;
}

export interface ReclamationPage {
  content: ReclamationResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ReclamationCommentaireRequest {
  contenu: string;
}

export interface ReclamationCommentaireResponse {
  id: number;
  reclamationId: number;
  contenu: string;
  auteurId: number | null;
  auteurNom: string | null;
  createdAt: string;
}

export interface ReclamationClotureRequest {
  motif: string;
}

export interface ReclamationStatutRequest {
  statut: ReclamationStatut;
  commentaire?: string;
}

export interface ReclamationAffectationRequest {
  gestionnaireId: number;
  service?: string;
}

export interface ReclamationPieceJointeResponse {
  id: number;
  reclamationId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string;
  createdAt: string;
}

export interface ReclamationStatistiquesResponse {
  total: number;
  ouvertes: number;
  resolues: number;
  fermees: number;
  creeesAutomatiquement: number;
  delaiMoyenResolutionHeures: number | null;
  parStatut: Record<string, number>;
  parPriorite: Record<string, number>;
  parType: Record<string, number>;
  parCanal: Record<string, number>;
}
