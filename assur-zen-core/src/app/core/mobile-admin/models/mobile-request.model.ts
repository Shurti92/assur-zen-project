/** Contrat : docs/api-contracts/admin-mobile.md du gateway (§2 à §4). */

export type CallbackSlot = 'ASAP' | 'MORNING' | 'AFTERNOON' | 'EVENING';
export type CallbackStatus = 'EN_ATTENTE' | 'RAPPELE' | 'ANNULE';
export type ContractRequestStatus = 'EN_ATTENTE' | 'TRAITEE' | 'REFUSEE';
export type DeletionRequestStatus = 'EN_ATTENTE' | 'TRAITEE' | 'REFUSEE';
export type ContractRequestType = 'MODIFICATION' | 'TERMINATION';

export interface CallbackRequest {
  id: number;
  reference: string;
  phone: string;
  slot: CallbackSlot;
  productId?: number | null;
  /** Libellé du produit, si le gateway le fournit. */
  productName?: string | null;
  userId?: number | null;
  /** Nom du titulaire du compte, si le gateway le fournit. */
  fullName?: string | null;
  statut: CallbackStatus;
  createdAt: string;
  processedAt?: string | null;
  processedBy?: string | null;
  comment?: string | null;
}

export interface ContractRequest {
  id: number;
  reference: string;
  contractId: number;
  policyNumber: string;
  insuredName: string;
  userId?: number | null;
  phone?: string | null;
  type: ContractRequestType;
  message?: string | null;
  statut: ContractRequestStatus;
  createdAt: string;
  processedAt?: string | null;
  processedBy?: string | null;
  comment?: string | null;
}

export interface DeletionRequest {
  id: number;
  reference: string;
  userId: number;
  phone?: string | null;
  fullName?: string | null;
  statut: DeletionRequestStatus;
  requestedAt: string;
  processedAt?: string | null;
  processedBy?: string | null;
  comment?: string | null;
}

export type SupportStatus = 'EN_ATTENTE' | 'EN_COURS' | 'REPONDU' | 'CLOTURE';

/** Demande d'aide d'un client (§9 du contrat). `topic` est un code issu de la configuration SUPPORT_TOPICS. */
export interface SupportRequest {
  id: number;
  reference: string;
  userId?: number | null;
  phone?: string | null;
  topic?: string | null;
  message: string;
  status: SupportStatus;
  response?: string | null;
  respondedAt?: string | null;
  respondedBy?: string | null;
  createdAt: string;
}

export interface PendingCounts {
  callbacks: number;
  contractRequests: number;
  deletionRequests: number;
  supportRequests: number;
}
