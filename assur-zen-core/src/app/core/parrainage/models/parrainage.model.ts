export type ProgrammeParrainageStatut = 'BROUILLON' | 'ACTIF' | 'SUSPENDU' | 'EXPIRE' | 'CLOTURE';

export type ParrainageStatut =
  | 'CREE'
  | 'INVITE'
  | 'INSCRIT'
  | 'ELIGIBLE'
  | 'CONTRAT_CREE'
  | 'CONTRAT_ACTIF'
  | 'RECOMPENSE_EN_ATTENTE'
  | 'RECOMPENSE_ATTRIBUEE'
  | 'REJETE'
  | 'ANNULE'
  | 'EXPIRE';

export type TypeRecompenseParrainage = 'CASHBACK' | 'REMISE' | 'BONUS' | 'POINTS' | 'CADEAU' | 'COUPON' | 'REDUCTION_PRIME' | 'AUTRE';
export type BeneficiaireRecompense = 'PARRAIN' | 'FILLEUL' | 'LES_DEUX';
export type RecompenseParrainageStatut = 'EN_ATTENTE' | 'ELIGIBLE' | 'APPROUVEE' | 'ATTRIBUEE' | 'UTILISEE' | 'EXPIREE' | 'ANNULEE' | 'REVERSEE';

export type ControleAntifraudeType =
  | 'AUTO_PARRAINAGE'
  | 'TELEPHONE_DUPLIQUE'
  | 'EMAIL_DUPLIQUE'
  | 'CLIENT_EXISTANT'
  | 'PARRAINAGES_MULTIPLES'
  | 'ACTIVITE_SUSPECTE';
export type ControleAntifraudeStatut = 'REUSSI' | 'ECHEC' | 'REVUE_REQUISE';

export type HistoriqueParrainageEvenement =
  | 'CREATION'
  | 'INVITATION_ENVOYEE'
  | 'FILLEUL_INSCRIT'
  | 'CONTRAT_RATTACHE'
  | 'ELIGIBILITE_VERIFIEE'
  | 'RECOMPENSE_CREEE'
  | 'RECOMPENSE_APPROUVEE'
  | 'RECOMPENSE_ATTRIBUEE'
  | 'RECOMPENSE_ANNULEE'
  | 'CHANGEMENT_STATUT'
  | 'EXPIRATION'
  | 'REJET'
  | 'ANNULATION'
  | 'CONTROLE_ANTIFRAUDE';

export interface ProgrammeParrainageRequest {
  code: string;
  nom: string;
  description?: string;
  dateDebut?: string;
  dateFin?: string;
  maxParrainagesParClient?: number;
  maxRecompensesParClient?: number;
  delaiCarenceJours?: number;
  dureeValiditeJours?: number;
  primeMinimum?: number;
  produitsEligibles?: number[];
}

export interface ProgrammeParrainageResponse extends ProgrammeParrainageRequest {
  id: number;
  statut: ProgrammeParrainageStatut;
  createdAt: string;
  lastUpdate: string | null;
}

export interface CodeParrainageRequest {
  parrainInsuredId: number;
  programmeId: number;
  maxUtilisations?: number;
  dateExpiration?: string;
}

export interface CodeParrainageResponse {
  id: number;
  code: string;
  lien: string;
  parrainId: number;
  parrainNom: string;
  programmeId: number;
  programmeNom: string;
  nombreUtilisations: number;
  maxUtilisations: number | null;
  dateExpiration: string | null;
  actif: boolean;
  createdAt: string;
}

export interface ParrainageRequest {
  code?: string;
  parrainInsuredId?: number;
  programmeId?: number;
  filleulNom?: string;
  filleulEmail?: string;
  filleulTelephone?: string;
}

export interface ParrainageResponse {
  id: number;
  reference: string;
  programmeId: number;
  programmeNom: string;
  codeParrainageId: number | null;
  codeUtilise: string | null;
  parrainId: number;
  parrainNom: string;
  filleulId: number | null;
  filleulNom: string;
  filleulEmail: string | null;
  filleulTelephone: string | null;
  contratId: number | null;
  contratPolicyNumber: string | null;
  statut: ParrainageStatut;
  motifRejet: string | null;
  dateInvitation: string | null;
  dateInscription: string | null;
  dateEligibilite: string | null;
  dateCompletion: string | null;
  dateAnnulation: string | null;
  dateExpiration: string | null;
  createdAt: string;
  lastUpdate: string | null;
}

export interface EligibiliteVerificationItem {
  critere: string;
  satisfait: boolean;
  detail: string | null;
}

export interface ParrainageValidationResponse {
  parrainage: ParrainageResponse;
  eligible: boolean;
  verifications: EligibiliteVerificationItem[];
}

export interface ParrainagePage {
  content: ParrainageResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ParrainageInscriptionRequest {
  filleulInsuredId: number;
}

export interface ParrainageContratRequest {
  contratId: number;
}

export interface ParrainageMotifRequest {
  motif?: string;
}

export interface ControleAntifraudeParrainageResponse {
  id: number;
  parrainageId: number;
  typeControle: ControleAntifraudeType;
  statut: ControleAntifraudeStatut;
  score: number | null;
  motif: string | null;
  createdAt: string;
}

export interface HistoriqueParrainageResponse {
  id: number;
  parrainageId: number;
  typeEvenement: HistoriqueParrainageEvenement;
  ancienStatut: ParrainageStatut | null;
  nouveauStatut: ParrainageStatut | null;
  description: string | null;
  createdAt: string;
  /** Corrigé le 2026-08-21 (re-vérifié via /v3/api-docs) — le backend renvoie auteurId/auteurNom,
   * pas createdBy comme précédemment supposé (le champ était toujours undefined jusqu'ici). */
  auteurId: number | null;
  auteurNom: string | null;
}

export interface RegleRecompenseParrainageRequest {
  programmeId: number;
  nom: string;
  description?: string;
  typeRecompense: TypeRecompenseParrainage;
  beneficiaire: BeneficiaireRecompense;
  montantFixe?: number;
  pourcentage?: number;
  points?: number;
  primeMinimum?: number;
  plafondRecompense?: number;
  priorite?: number;
  actif?: boolean;
}

export interface RegleRecompenseParrainageResponse extends RegleRecompenseParrainageRequest {
  id: number;
  programmeNom: string;
}

export interface RecompenseParrainageResponse {
  id: number;
  parrainageId: number;
  parrainageReference: string;
  regleId: number | null;
  regleNom: string | null;
  beneficiaireId: number;
  beneficiaireNom: string;
  typeRecompense: TypeRecompenseParrainage;
  statut: RecompenseParrainageStatut;
  montant: number | null;
  devise: string | null;
  pourcentage: number | null;
  points: number | null;
  description: string | null;
  dateEligibilite: string | null;
  dateApprobation: string | null;
  dateAttribution: string | null;
  dateAnnulation: string | null;
  dateExpiration: string | null;
  motifAnnulation: string | null;
  createdAt: string;
}

export interface ParametreParrainageRequest {
  cle: string;
  valeur: string;
  description?: string;
}

export interface ParametreParrainageResponse extends ParametreParrainageRequest {
  id: number;
}
