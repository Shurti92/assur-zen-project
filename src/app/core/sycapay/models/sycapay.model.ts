/** Statut d'une transaction SycaPay telle que stockée/suivie côté backend (endpoints
 * balance/dashboard/transactions/reconcile) — DISTINCT de CommercialCommissionPaymentResponse
 * .sycapayStatus (core/commercial/models/commercial-commission.model.ts) qui n'a que 5 valeurs
 * et pas de PENDING/CANCELLED. Confirmé par le backend le 2026-09-22 : ne pas fusionner ces deux
 * enums, même s'ils partagent SUCCESS/PROCESSING/FAILED/UNKNOWN. */
export type SycaPayTransactionStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'UNKNOWN';

/** Reflet brut de la réponse SycaPay elle-même — champs volontairement en Capitalized/String
 * (contrat SycaPay tel quel, pas camelCase côté backend non plus). "solde" est une String
 * numérique à parser si besoin d'un calcul. */
export interface SycaPayBalanceResult {
  identifiant: string;
  solde: string;
  operateur: string;
  isopays: string;
  marchandid: string;
}

export interface SycaPayBalanceResponse {
  Apiname: string;
  Description: string;
  Resultats: SycaPayBalanceResult[];
}

export interface SycaPayDashboard {
  paiementsDuJour: number;
  montantPayeAujourdhui: number;
  paiementsSuccess: number;
  paiementsFailed: number;
  /** Regroupe PENDING + PROCESSING côté backend. */
  paiementsPending: number;
  paiementsUnknown: number;
  /** Mode test (2026-09-24) : activé par variables d'environnement hors prod uniquement — tout
   * virement est alors redirigé vers testPhone pour testAmount XOF. Null hors test. */
  testMode: boolean;
  testPhone: string | null;
  testAmount: number | null;
}

/** Bénéficiaire d'un virement (backend du 2026-09-23) : un seul de commercialId / partenaireId
 * est renseigné selon ce type. */
export type SycaPayBeneficiaryType = 'COMMERCIAL' | 'PARTENAIRE';

export interface SycaPayTransaction {
  id: number;
  transactionReference: string;
  /** Null pour le virement d'une demande de paiement anticipé (voir partialPaymentRequestId). */
  contractId: number | null;
  /** Renseigné (et contractId null) pour le virement d'une demande de paiement anticipé (2026-09-26). */
  partialPaymentRequestId?: number | null;
  /** Renseigné (et contractId null) pour le virement du bonus mensuel d'un commercial (2026-10-02). */
  commercialPerformanceId?: number | null;
  beneficiaryType: SycaPayBeneficiaryType;
  partenaireId: number | null;
  /** Nullable depuis le paiement des partenaires (2026-09-23). */
  commercialId: number | null;
  /** Long côté backend : unité entière XOF, pas de décimales. */
  amount: number;
  currency: string;
  beneficiaryName: string | null;
  beneficiaryPhone: string | null;
  status: SycaPayTransactionStatus;
  sycapayReference: string | null;
  /** Reste null tant qu'aucune réconciliation (getStatusByOrderID) n'a eu lieu. */
  sycapayTransactionId: string | null;
  sycapayResponseCode: number | null;
  sycapayResponseMessage: string | null;
  /** Instant ISO-8601 UTC. */
  requestDate: string;
  processingDate: string | null;
  completedDate: string | null;
  attemptCount: number;
  /** Renseignés sur FAILED, à null sinon (tout à null sauf status sur UNKNOWN). */
  errorCode: string | null;
  errorMessage: string | null;
  /** Virement redirigé par le mode test : amount / beneficiaryPhone sont alors ceux réellement
   * envoyés (montant et numéro de test), la commission garde son montant réel. */
  testMode: boolean;
}

export interface SycaPayTransactionPage {
  content: SycaPayTransaction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** GET /sycapay/transactions/status-counts (2026-09-23) : les 6 clés sont toujours présentes (0 si
 * aucune), total = leur somme. Mêmes filtres que /transactions, SANS status (répartition complète). */
export type SycaPayStatusCounts = Record<SycaPayTransactionStatus, number> & { total: number };

export interface SycaPayTransactionFilter {
  reference?: string;
  beneficiaryType?: SycaPayBeneficiaryType;
  commercialId?: number;
  partenaireId?: number;
  /** Au plus une transaction par contrat (contrainte d'unicité backend, 2026-09-25). */
  contractId?: number;
  status?: SycaPayTransactionStatus;
  /** Instant ISO-8601, ex. 2026-09-01T00:00:00Z — filtrent sur requestDate. */
  dateStart?: string;
  dateEnd?: string;
}
