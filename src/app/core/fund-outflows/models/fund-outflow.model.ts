/** Statuts partagés par la sortie de fonds et le prêt qu'elle porte le cas échéant. Flux normal :
 * DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED → AUTHORIZED → (PROCESSING) → DISBURSED →
 * PARTIALLY_REPAID → REPAID → CLOSED (terminal). Branches : UNDER_REVIEW → REJECTED (terminal),
 * DRAFT/SUBMITTED/UNDER_REVIEW → CANCELLED (terminal). PROCESSING existe dans le schéma réel mais
 * n'est pas mentionné dans le flux annoncé par le backend — état transitoire probable pendant le
 * décaissement, à confirmer si observé en usage réel. */
export type FundOutflowStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'AUTHORIZED'
  | 'PROCESSING'
  | 'DISBURSED'
  | 'PARTIALLY_REPAID'
  | 'REPAID'
  | 'CLOSED';

export type FundOutflowPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

export type DisbursementMethod =
  | 'BANK_TRANSFER'
  | 'ORANGE_MONEY'
  | 'MTN_MONEY'
  | 'MOOV_MONEY'
  | 'WAVE'
  | 'CASH'
  | 'CHEQUE'
  | 'OTHER';

export type DisbursementStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';

export type LoanRepaymentFrequency = 'MONTHLY' | 'WEEKLY' | 'BIWEEKLY' | 'QUARTERLY' | 'CUSTOM';

export interface CreateFundOutflowRequest {
  outflowTypeId: number;
  amount: number;
  reason: string;
  beneficiaryType?: string;
  beneficiaryId?: number;
  beneficiaryLabel?: string;
  currency?: string;
  description?: string;
  priority?: FundOutflowPriority;
  dueDate?: string;
  /** Obligatoires côté backend uniquement si outflowTypeId correspond au type "LOAN". */
  loanInterestRate?: number;
  loanStartDate?: string;
  loanExpectedEndDate?: string;
  loanRepaymentFrequency?: LoanRepaymentFrequency;
  loanNumberOfInstallments?: number;
}

/** Modification possible uniquement à l'état DRAFT. Les champs loan* sont désormais optionnels à
 * la création (confirmé en direct le 2026-09-03) mais deviennent obligatoires au décaissement
 * (400 explicite sinon) — c'est ici, via PUT tant que le statut est DRAFT, qu'on les complète.
 * FundOutflowResponse n'expose jamais ces champs en retour (schéma vérifié) : impossible de
 * pré-remplir le formulaire d'édition avec les valeurs déjà saisies. */
export interface UpdateFundOutflowRequest {
  outflowTypeId: number;
  amount: number;
  reason: string;
  beneficiaryType?: string;
  beneficiaryId?: number;
  beneficiaryLabel?: string;
  currency?: string;
  description?: string;
  priority?: FundOutflowPriority;
  dueDate?: string;
  loanInterestRate?: number;
  loanStartDate?: string;
  loanExpectedEndDate?: string;
  loanRepaymentFrequency?: LoanRepaymentFrequency;
  loanNumberOfInstallments?: number;
}

export interface FundOutflowResponse {
  id: number;
  uuid: string;
  reference: string;
  outflowTypeId: number;
  outflowTypeCode: string;
  outflowTypeNom: string;
  beneficiaryType: string | null;
  beneficiaryId: number | null;
  beneficiaryLabel: string | null;
  amount: number;
  currency: string | null;
  description: string | null;
  reason: string;
  status: FundOutflowStatus;
  priority: FundOutflowPriority | null;
  dueDate: string | null;
  rejectionReason: string | null;
  loanId: number | null;
  submittedAt: string | null;
  submittedBy: string | null;
  approvedAt: string | null;
  approvedBy: string | null;
  authorizedAt: string | null;
  authorizedBy: string | null;
  disbursedAt: string | null;
  disbursedBy: string | null;
  closedAt: string | null;
  closedBy: string | null;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface FundOutflowPage {
  content: FundOutflowResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FundOutflowSearchFilters {
  reference?: string;
  status?: FundOutflowStatus;
  outflowTypeId?: number;
  beneficiaryType?: string;
  beneficiaryId?: number;
  amountMin?: number;
  amountMax?: number;
  dateFrom?: string;
  dateTo?: string;
  priority?: FundOutflowPriority;
}

export interface RejectFundOutflowRequest {
  reason: string;
}

export interface CancelFundOutflowRequest {
  reason: string;
}

export interface DisburseFundOutflowRequest {
  method: DisbursementMethod;
  paymentReference?: string;
}

export interface FundOutflowDisbursementResponse {
  id: number;
  fundOutflowId: number;
  reference: string;
  amount: number;
  currency: string | null;
  method: DisbursementMethod;
  paymentReference: string | null;
  status: DisbursementStatus;
  processedBy: string | null;
  disbursementDate: string | null;
  createdAt: string;
}
