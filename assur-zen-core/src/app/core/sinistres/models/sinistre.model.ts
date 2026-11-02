export type SinistreStatus =
  | 'declare'
  | 'en_cours_analyse'
  | 'pieces_complementaires_requises'
  | 'expertise_en_cours'
  | 'accepte'
  | 'rejete'
  | 'indemnise'
  | 'cloture';

export interface SinistreRequest {
  contractId: number;
  nature: string;
  dateSurvenance: string;
  montantEstime?: number;
  assigneeId?: number;
  declarantId?: number;
  description?: string;
  notes?: string;
  /** Ajoutés au DTO côté backend le 2026-08-21 — pas de champ de formulaire dédié pour l'instant. */
  compagnieId?: number;
  gestionnaireNom?: string;
}

export interface SinistreResponse {
  id: number;
  numero: string;
  contractId: number;
  /** Nullable depuis le 2026-09-14 : le backend renvoie désormais null (au lieu d'un 500)
   * quand l'entité référencée par la FK a été supprimée (hard-delete). */
  contractPolicyNumber: string | null;
  insuredId: number;
  insuredName: string | null;
  nature: string;
  dateSurvenance: string;
  dateDeclaration: string;
  montantEstime: number | null;
  montantIndemnise: number | null;
  status: SinistreStatus;
  declarantId: number | null;
  declarant: string | null;
  assigneeId: number | null;
  assignee: string | null;
  description: string | null;
  notes: string | null;
  createdAt: string;
  compagnieId?: number | null;
  compagnieNom?: string | null;
  gestionnaireNom?: string | null;
}

export interface SinistrePage {
  content: SinistreResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SinistreSuiviRequest {
  statut: SinistreStatus;
  commentaire?: string;
  montantIndemnise?: number;
}

export interface SinistreSuiviResponse {
  id: number;
  sinistreId: number;
  statut: SinistreStatus;
  commentaire: string | null;
  auteurId: number | null;
  auteur: string | null;
  notificationEnvoyee: boolean;
  createdAt: string;
}

export interface SinistrePhotoResponse {
  id: number;
  sinistreId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string;
  createdAt: string;
}
