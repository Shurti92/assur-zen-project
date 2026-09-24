export type EnqueteContexte = 'POST_SOUSCRIPTION' | 'POST_SINISTRE' | 'PERIODIQUE' | 'POST_RESILIATION';
/** Élargi le 2026-08-21 (voir /v3/api-docs) avec ECHEC_ENVOI — un état d'échec d'envoi qui n'existait pas avant. */
export type EnqueteStatut = 'ENVOYEE' | 'ECHEC_ENVOI' | 'REPONDUE' | 'EXPIREE';

export interface EnqueteSatisfactionResponse {
  id: number;
  code: string;
  contexte: EnqueteContexte;
  insuredId: number;
  insuredName: string;
  contratId: number | null;
  contratPolicyNumber: string | null;
  sinistreId: number | null;
  sinistreNumero: string | null;
  dateEnvoi: string;
  dateReponse: string | null;
  statut: EnqueteStatut;
  scoreNps: number | null;
  categorieNps: string | null;
  commentaire: string | null;
  createdAt: string;
}

export interface EnqueteSatisfactionRequest {
  contexte: EnqueteContexte;
  insuredId: number;
  contratId?: number;
  sinistreId?: number;
}

export interface EnqueteReponseRequest {
  scoreNps: number;
  commentaire?: string;
}

export interface EnquetePage {
  content: EnqueteSatisfactionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
