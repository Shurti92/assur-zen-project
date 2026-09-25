import {
  ContractCommissionStatus,
  ContractResponse,
  ContractStatus,
  ModePaiement,
  PeriodiciteEcheance,
  StatutReversementPrime,
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
  /** FRACTION (0.10 = 10 %) — voir percentToApiRate(). */
  commissionRate: number;
  commissionAmount: number;
  /** Commission partenaire en FCFA entiers (backend du 2026-09-25) : fournie → conservée ;
   * absente → commission configurée (/partner-commissions), sinon 400. 0 sans partenaire.
   * Bornes : 0 ≤ montant ≤ premium. */
  partnerShare?: number;
  /** Commission du commercial (salesRepId) en FCFA entiers, optionnelle : fournie → conservée ;
   * absente → configuration du commercial, puis du produit, sinon 0. Doit être nulle/absente
   * quand un partenaire est lié au contrat (400 sinon). */
  commercialCommissionAmount?: number;
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
  /** Prime déjà versée à la compagnie par l'intermédiaire (partenaire ou commercial) qui l'a
   * encaissée — voir docs/api-contracts/souscription-prime-versee.md (pas encore relayé par
   * POST /souscriptions au moment de l'écriture ; ignoré sans erreur d'ici là). */
  primeReversementStatus?: StatutReversementPrime;
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
