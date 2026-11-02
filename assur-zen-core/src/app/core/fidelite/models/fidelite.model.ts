export interface PalierFideliteResponse {
  id: number;
  code: string;
  libelle: string;
  seuilPoints: number;
  avantages: string | null;
  active: boolean;
  /** Ajoutés au DTO côté backend le 2026-08-21 — pas de champ de formulaire dédié pour l'instant. */
  couleur?: string | null;
  icone?: string | null;
  estVip?: boolean;
}

export interface PalierFideliteRequest {
  code: string;
  libelle: string;
  seuilPoints: number;
  avantages?: string;
  active?: boolean;
  couleur?: string;
  icone?: string;
  estVip?: boolean;
}

export interface RecompenseResponse {
  id: number;
  code: string;
  nom: string;
  description: string | null;
  coutPoints: number;
  stock: number | null;
  active: boolean;
}

export interface RecompenseRequest {
  code: string;
  nom: string;
  description?: string;
  coutPoints: number;
  stock?: number;
  active?: boolean;
}

export type EchangeStatut = 'EN_ATTENTE' | 'VALIDE' | 'LIVREE' | 'ANNULEE';

export interface RecompenseEchangeResponse {
  id: number;
  compteFideliteId: number;
  insuredId: number;
  /** Nullable depuis le 2026-09-14 : le backend renvoie désormais null (au lieu d'un 500)
   * quand l'entité référencée par la FK a été supprimée (hard-delete). */
  insuredName: string | null;
  recompenseId: number;
  recompenseNom: string;
  pointsUtilises: number;
  statut: EchangeStatut;
  dateEchange: string;
  dateTraitement: string | null;
  traiteParId: number | null;
  traitePar: string | null;
  createdAt: string;
}

export interface RecompenseEchangeRequest {
  insuredId: number;
  recompenseId: number;
}

export type StatutVip = 'NON_VIP' | 'VIP_ACTIF' | 'VIP_SUSPENDU' | 'VIP_SORTI';

export interface CompteFideliteResponse {
  id: number;
  insuredId: number;
  /** Nullable depuis le 2026-09-14 : le backend renvoie désormais null (au lieu d'un 500)
   * quand l'entité référencée par la FK a été supprimée (hard-delete). */
  insuredName: string | null;
  soldePoints: number;
  pointsCumulesTotal: number;
  palierId: number | null;
  palierLibelle: string | null;
  dateAdhesion: string;
  scoreFidelite: number | null;
  statutVip: StatutVip;
  dateEntreeVip: string | null;
  dateSortieVip: string | null;
  gestionnaireDedieId: number | null;
  gestionnaireDedieNom: string | null;
  active: boolean;
}

export interface CompteFidelitePage {
  content: CompteFideliteResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface StatutVipRequest {
  statut: StatutVip;
  motif?: string;
}

export interface GestionnaireDedieRequest {
  gestionnaireId: number;
}

/** Élargi le 2026-08-21 (voir /v3/api-docs) — les 5 nouvelles valeurs n'ont pas encore de
 * libellé/mapping dédié dans l'UI, qui retombera sur la valeur brute pour celles-ci. */
export type FidelisationMouvementType =
  | 'GAIN'
  | 'UTILISATION'
  | 'EXPIRATION'
  | 'AJUSTEMENT'
  | 'SCORE_RECALCULE'
  | 'ENTREE_VIP'
  | 'CHANGEMENT_NIVEAU'
  | 'SORTIE_VIP'
  | 'AVANTAGE_ATTRIBUE';

export interface FidelisationHistoriqueResponse {
  id: number;
  compteFideliteId: number;
  typeMouvement: FidelisationMouvementType;
  points: number;
  soldeApres: number;
  motif: string | null;
  contratId: number | null;
  contratPolicyNumber: string | null;
  auteurId: number | null;
  auteur: string | null;
  createdAt: string;
}

export interface FidelisationAjustementRequest {
  insuredId: number;
  points: number;
  motif: string;
}
