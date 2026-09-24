import { SycaPayStatus } from '../../commercial/models/commercial-commission.model';

export type TypePartenaire = 'DIRECT' | 'AGENT' | 'APPORTEUR';
export type CommissionStatus = 'paid' | 'pending';

export interface PartnerCommissionSummary {
  partenaireId: number;
  codePartenaire: string;
  nom: string;
  prenoms: string;
  type: TypePartenaire;
  pendingContractsCount: number;
  paidContractsCount: number;
  totalPending: number;
  totalPaid: number;
}

export interface ContractCommission {
  contractId: number;
  policyNumber: string;
  signedAt: string;
  partnerShare: number;
  commissionStatus: CommissionStatus;
}

export interface CommissionPaymentRequest {
  contractIds: number[];
  reference?: string;
}

export interface CommissionPaymentResponse {
  id: number;
  contractId: number;
  /** Nullable depuis le 2026-09-14 : le backend renvoie désormais null (au lieu d'un 500)
   * quand l'entité référencée par la FK a été supprimée (hard-delete). */
  policyNumber: string | null;
  partenaireId: number;
  partenaireNom: string | null;
  amount: number;
  /** Null pour un paiement automatique (aucun admin à l'origine). */
  paidByUserId: number | null;
  paidByUsername: string | null;
  reference: string | null;
  paidAt: string;
  /** Résultat réel du SMS de confirmation (backend du 2026-09-24) : true envoyé, false échec,
   * null aucune trace (paiements antérieurs). Aussi renseigné dans l'historique depuis cette date. */
  notificationEnvoyee: boolean | null;
  /** Paiement déclenché automatiquement (fréquence IMMEDIAT, ou réconciliation d'une transaction
   * partenaire passée à SUCCESS) — backend du 2026-09-23. Seuls les virements RÉUSSIS créent une
   * ligne ici ; les tentatives FAILED/UNKNOWN/PROCESSING sont dans /sycapay/transactions. */
  automatic: boolean;
  /** Champs sycapay* renseignés uniquement si automatic=true, null pour un paiement manuel. */
  sycapayStatus: SycaPayStatus | null;
  sycapayTransactionReference: string | null;
  sycapayTransactionPk: number | null;
  sycapayMessage: string | null;
}

export interface CommissionPaymentPage {
  content: CommissionPaymentResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Demande de versement anticipé d'une partie du solde de commissions en attente — créée côté
 * app partenaire ("Force de Vente"), validée ici côté back-office (2026-08-22). */
export type PartialPaymentRequestStatus = 'pending' | 'paid';

export interface PartialPaymentRequestResponse {
  id: number;
  partenaireId: number;
  requestedAmount: number;
  pendingBalanceBefore: number;
  pendingBalanceAfter: number;
  requestedAt: string;
  status: PartialPaymentRequestStatus;
  numeroWaveCommission: string | null;
}

export interface PartialPaymentStatusResponse {
  pendingBalance: number;
  requestsToday: number;
  maxRequestsPerDay: number;
  nextRequestAvailableAt: string | null;
  history: PartialPaymentRequestResponse[];
}
