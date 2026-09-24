import {
  ContractCommissionStatus,
  ContractResponse,
  ContractStatus,
  ModePaiement,
  PeriodiciteEcheance,
} from '../../contracts/models/contract.model';
import { InsuredResponse, TypeClient } from '../../insured/models/insured.model';

export interface InsuredSubscriptionRequest {
  /** Assuré existant à rattacher — si présent, le backend rattache les contrats
   * à cet assuré (au lieu d'en créer un nouveau) et met à jour sa fiche avec les
   * autres champs de cet objet (404 si l'id ne correspond à rien). */
  insuredId?: number;
  nom: string;
  prenoms: string;
  clientPhone?: string;
  /** Second numero de telephone, optionnel - recoit aussi les SMS de suivi de contrat. */
  clientPhone2?: string;
  clientCity?: string;
  clientEmail?: string;
  typeClient?: TypeClient;
}

export interface ContractSubscriptionRequest {
  policyNumber?: string;
  dateEffet?: string;
  duree: string;
  dateEcheance?: string;
  productId: number;
  partenaireId?: number;
  compagnieId: number;
  premium: number;
  commissionRate: number;
  commissionAmount: number;
  partnerShare: number;
  status?: ContractStatus;
  commissionStatus?: ContractCommissionStatus;
  signedAt?: string;
  salesRepId?: number;
  notes?: string;
  modePaiement?: ModePaiement;
  nombreEcheances?: number;
  periodiciteEcheances?: PeriodiciteEcheance;
  /** Déduit de premium avant génération de l'échéancier côté backend — absent/0 = comportement inchangé. */
  montantDejaVerse?: number;
  contratPrecedentId?: number;
}

export interface InsuredRegistrationRequest {
  insured: InsuredSubscriptionRequest;
  contracts: ContractSubscriptionRequest[];
}

export interface InsuredRegistrationResponse {
  insured: InsuredResponse;
  contracts: ContractResponse[];
  welcomeSmsSent: boolean;
  reminderSmsScheduled: number;
}
