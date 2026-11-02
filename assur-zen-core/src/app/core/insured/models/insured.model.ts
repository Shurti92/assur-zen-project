import { ContractResponse } from '../../contracts/models/contract.model';
import { SinistreResponse } from '../../sinistres/models/sinistre.model';

export type TypeClient = 'PARTICULIER' | 'ENTREPRISE';

export interface InsuredRequest {
  nom: string;
  prenoms: string;
  clientPhone?: string;
  /** Second numero de telephone, optionnel - recoit aussi les SMS de suivi de contrat (avis
   * d'echeance, fin de contrat, relance echeance paiement) que clientPhone. */
  clientPhone2?: string;
  clientCity?: string;
  clientEmail?: string;
  typeClient?: TypeClient;
}

export interface InsuredResponse {
  id: number;
  nom: string;
  prenoms: string;
  clientPhone: string;
  clientPhone2?: string;
  clientCity: string;
  clientEmail?: string;
  typeClient?: TypeClient;
}

export interface InsuredPage {
  content: InsuredResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface InsuredFicheStats {
  totalContrats: number;
  contratsSignes: number;
  totalSinistres: number;
  sinistresEnCours: number;
  sinistresClotures: number;
  totalPrimes: number;
  totalMontantEstime: number;
  totalMontantIndemnise: number;
  tauxSinistralite: number;
}

/** Fiche de sinistralité consolidée d'un assuré — GET /insureds/{id}/fiche-sinistralite. */
export interface InsuredFicheResponse {
  insuredId: number;
  nom: string;
  prenoms: string;
  clientPhone: string;
  clientPhone2?: string | null;
  clientCity?: string | null;
  exerciceComptableId?: number | null;
  exerciceComptableLibelle?: string | null;
  historiqueContrats: ContractResponse[];
  sinistres: SinistreResponse[];
  stats: InsuredFicheStats;
}
