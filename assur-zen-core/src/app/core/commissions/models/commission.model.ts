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

/** Depuis le 2026-09-26 (backend 761d872) : VIREMENT_SYCAPAY si SycaPay est activé (virement Wave
 * réel, contrat par contrat), ENREGISTREMENT_MANUEL sinon (paiement fait hors application).
 * AVANCE_IMPUTEE (backend 381e49f, partenaires uniquement) : contrat entièrement couvert par les
 * avances déjà versées (demandes de paiement partiel payées), aucun virement. */
export type CommissionModePaiement = 'VIREMENT_SYCAPAY' | 'ENREGISTREMENT_MANUEL' | 'AVANCE_IMPUTEE';

export interface CommissionPaymentResponse {
  /** Null (comme paidAt, paidBy*, reference, notificationEnvoyee) si le virement n'est pas SUCCESS. */
  id: number | null;
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
  paidAt: string | null;
  /** Résultat réel du SMS de confirmation (backend du 2026-09-24) : true envoyé, false échec,
   * null aucune trace (paiements antérieurs). Aussi renseigné dans l'historique depuis cette date. */
  notificationEnvoyee: boolean | null;
  /** Paiement déclenché automatiquement (fréquence IMMEDIAT, ou réconciliation d'une transaction
   * partenaire passée à SUCCESS) — backend du 2026-09-23. Seuls les virements RÉUSSIS créent une
   * ligne ici ; les tentatives FAILED/UNKNOWN/PROCESSING sont dans /sycapay/transactions. */
  automatic: boolean;
  /** Champs sycapay* renseignés dès que modePaiement = VIREMENT_SYCAPAY (automatique ou manuel
   * depuis le 2026-09-26), null pour un enregistrement hors application. */
  sycapayStatus: SycaPayStatus | null;
  sycapayTransactionReference: string | null;
  sycapayTransactionPk: number | null;
  sycapayMessage: string | null;
  modePaiement: CommissionModePaiement;
  /** Part du contrat déjà versée par des avances (null si aucune) — amount reste la part entière. */
  avanceImputee: number | null;
  /** amount − avanceImputee : réellement viré ou enregistré ; null si le paiement n'est pas enregistré. */
  montantVerse: number | null;
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
/** processing / failed depuis le virement SycaPay à la validation (backend 619d5a1, 2026-09-26) :
 * processing = virement en cours ou incertain (à réconcilier), failed = virement échoué, montant
 * toujours réservé, relance par la même route /valider. */
export type PartialPaymentRequestStatus = 'pending' | 'paid' | 'processing' | 'failed';

/** Statut du virement d'une demande — inclut PENDING, contrairement à SycaPayStatus (/commissions/pay). */
export type PartialPaymentSycaPayStatus = 'SUCCESS' | 'PENDING' | 'PROCESSING' | 'FAILED' | 'UNKNOWN' | 'REJECTED';

export interface PartialPaymentRequestResponse {
  id: number;
  partenaireId: number;
  requestedAmount: number;
  pendingBalanceBefore: number;
  pendingBalanceAfter: number;
  requestedAt: string;
  status: PartialPaymentRequestStatus;
  numeroWaveCommission: string | null;
  /** Null sans virement (SycaPay désactivé ou demande pas encore validée). REJECTED : aucune
   * tentative, la demande garde son statut, voir sycapayMessage. */
  sycapayStatus: PartialPaymentSycaPayStatus | null;
  sycapayTransactionReference: string | null;
  sycapayTransactionPk: number | null;
  sycapayMessage: string | null;
  /** Null tant que la demande n'est pas payée. */
  modePaiement: Exclude<CommissionModePaiement, 'AVANCE_IMPUTEE'> | null;
}

/** Liste commune de validation (GET /commissions/partial-payment-requests, backend du 2026-09-25) :
 * demandes des partenaires ET des commerciaux. type : TOTAL | PARTIAL pour un commercial, null pour
 * un partenaire ; telephoneVersement = numéro figé au moment de la demande. */
export interface PaymentRequestValidationItem {
  id: number;
  beneficiaryType: 'PARTENAIRE' | 'COMMERCIAL';
  partenaireId: number | null;
  commercialId: number | null;
  beneficiaireNom: string | null;
  telephoneVersement: string | null;
  type: 'TOTAL' | 'PARTIAL' | null;
  requestedAmount: number;
  requestedAt: string;
  status: PartialPaymentRequestStatus;
  validatedAt: string | null;
  /** Champs SycaPay (backend e173b9b, 2026-09-27) — mêmes règles que PartialPaymentRequestResponse ;
   * null sans virement. sycapayTransactionPk sert au « Réconcilier » d'une demande processing. */
  sycapayStatus: PartialPaymentSycaPayStatus | null;
  sycapayTransactionPk: number | null;
  sycapayMessage: string | null;
  modePaiement: Exclude<CommissionModePaiement, 'AVANCE_IMPUTEE'> | null;
}

export interface PartialPaymentStatusResponse {
  pendingBalance: number;
  requestsToday: number;
  maxRequestsPerDay: number;
  nextRequestAvailableAt: string | null;
  history: PartialPaymentRequestResponse[];
}
