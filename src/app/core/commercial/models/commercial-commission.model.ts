import type { CommissionModePaiement } from '../../commissions/models/commission.model';

export type CommercialCommissionStatus = 'paid' | 'pending';

/** Statut du versement SycaPay (Wave) associé à une entrée de paiement de commission
 * commerciale, depuis l'intégration du 2026-09-22. */
export type SycaPayStatus = 'SUCCESS' | 'PROCESSING' | 'FAILED' | 'UNKNOWN' | 'REJECTED';

export interface CommercialCommissionSummary {
  commercialId: number;
  employeeCode: string;
  nomComplet: string;
  pendingContractsCount: number;
  paidContractsCount: number;
  totalPending: number;
  totalPaid: number;
}

export interface ContractCommercialCommission {
  contractId: number;
  policyNumber: string;
  signedAt: string;
  commercialCommissionAmount: number;
  commercialCommissionStatus: CommercialCommissionStatus;
}

export interface CommercialCommissionPaymentRequest {
  contractIds: number[];
  reference?: string;
}

export interface CommercialCommissionPaymentResponse {
  /** Nullable depuis l'intégration SycaPay (2026-09-22) : un paiement peut échouer
   * individuellement, auquel cas aucune écriture n'est créée — voir sycapayStatus. */
  id: number | null;
  contractId: number;
  /** Nullable depuis le 2026-09-14 : le backend renvoie désormais null (au lieu d'un 500)
   * quand l'entité référencée par la FK a été supprimée (hard-delete) — même correctif que
   * CommissionPaymentResponse (core/commissions/models/commission.model.ts). */
  policyNumber: string | null;
  commercialId: number;
  commercialNom: string | null;
  amount: number;
  paidByUserId: number;
  paidByUsername: string | null;
  reference: string | null;
  /** Nullable depuis l'intégration SycaPay (2026-09-22), même raison que id. */
  paidAt: string | null;
  /** Résultat réel du SMS de confirmation (backend du 2026-09-24) : true envoyé, false échec,
   * null aucune trace (paiements antérieurs). Aussi renseigné dans l'historique depuis cette date. */
  notificationEnvoyee: boolean | null;
  /** Statut du virement SycaPay pour ce contrat — ne pas supposer "demandé = payé", lire ce
   * champ par entrée (2026-09-22). */
  sycapayStatus: SycaPayStatus | null;
  /** Toujours renseignée sauf sur REJECTED (bloqué avant création de la transaction). */
  sycapayTransactionReference: string | null;
  /** Id technique de la transaction SycaPay (≠ SycaPayTransaction.sycapayTransactionId, qui est
   * l'identifiant côté SycaPay) — ajouté par le backend le 2026-09-23 : renseigné sauf sur REJECTED,
   * aussi renseigné dans l'historique GET /commercial-commissions/payments depuis le 2026-09-24
   * (null pour les paiements antérieurs ou non liés à un virement). Reste optionnel pour
   * un backend antérieur : repli sur GET /sycapay/transactions?reference=… (voir SycaPayComponent). */
  sycapayTransactionPk?: number | null;
  /** Message lisible à afficher à l'admin quand sycapayStatus n'est pas SUCCESS. */
  sycapayMessage: string | null;
  /** Depuis le 2026-09-26 (backend 4264f74, mode hybride) : ENREGISTREMENT_MANUEL quand SycaPay est
   * désactivé (paiement fait hors application, sycapayStatus null) au lieu d'un 502. */
  modePaiement: CommissionModePaiement;
}

export interface CommercialCommissionPaymentPage {
  content: CommercialCommissionPaymentResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
