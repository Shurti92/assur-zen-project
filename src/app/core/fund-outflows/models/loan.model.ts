import { DisbursementMethod, FundOutflowStatus, LoanRepaymentFrequency } from './fund-outflow.model';

export type LoanInstallmentStatus = 'PENDING' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED';

export interface LoanResponse {
  id: number;
  uuid: string;
  fundOutflowId: number;
  fundOutflowReference: string;
  status: FundOutflowStatus;
  loanReference: string;
  partnerId: number;
  partnerCode: string;
  partnerName: string;
  principalAmount: number;
  interestRate: number;
  totalAmount: number;
  currency: string | null;
  startDate: string | null;
  expectedEndDate: string | null;
  repaymentFrequency: LoanRepaymentFrequency;
  numberOfInstallments: number;
  remainingBalance: number;
  overdue: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface LoanPage {
  content: LoanResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LoanSearchFilters {
  partnerId?: number;
  reference?: string;
  dateFrom?: string;
  dateTo?: string;
  amountMin?: number;
  amountMax?: number;
  overdue?: boolean;
}

export interface LoanInstallmentResponse {
  id: number;
  installmentNumber: number;
  dueDate: string;
  principalAmount: number;
  interestAmount: number;
  totalDue: number;
  paidAmount: number;
  remainingAmount: number;
  status: LoanInstallmentStatus;
  daysOverdue: number;
  paidAt: string | null;
}

export interface LoanBalanceResponse {
  loanId: number;
  principalAmount: number;
  totalAmount: number;
  totalRepaid: number;
  remainingBalance: number;
}

export interface CreateLoanRepaymentRequest {
  amount: number;
  paymentDate: string;
  paymentMethod: DisbursementMethod;
  paymentReference?: string;
  comment?: string;
}

export interface EarlyRepaymentRequest {
  paymentDate: string;
  paymentMethod: DisbursementMethod;
  paymentReference?: string;
  comment?: string;
}

export interface LoanRepaymentAllocationResponse {
  installmentId: number;
  installmentNumber: number;
  allocatedAmount: number;
}

export interface LoanRepaymentResponse {
  id: number;
  reference: string;
  amount: number;
  currency: string | null;
  paymentMethod: DisbursementMethod;
  paymentReference: string | null;
  paymentDate: string;
  receivedBy: string | null;
  comment: string | null;
  allocations: LoanRepaymentAllocationResponse[];
  createdAt: string;
}
