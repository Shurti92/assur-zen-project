export type ContractStatus = 'signed' | 'pending' | 'draft' | 'cancelled';
export type ContractCommissionStatus = 'paid' | 'pending';
export type ModePaiement = 'COMPTANT' | 'FRACTIONNE';
export type PeriodiciteEcheance = 'MENSUELLE' | 'TRIMESTRIELLE' | 'SEMESTRIELLE' | 'ANNUELLE';
/** Suivi du reversement de la prime encaissée par le partenaire à la compagnie — non pertinent
 * si le contrat n'a pas de partenaire (ajouté au DTO le 2026-08-22). */
export type StatutReversementPrime = 'non_reverse' | 'reverse';

/** POST /contracts (endpoint générique, confirmé le 2026-09-13 utilisable directement pour
 * lier un contrat à un véhicule de flotte via vehiculeId — pas d'endpoint dédié flotte).
 * Champs obligatoires côté backend : duree, productId, insuredId, compagnieId, premium,
 * partnerShare. commissionRate/commissionAmount deviennent obligatoires (l'un des deux
 * suffit) dès que partenaireId est absent. */
export interface ContractRequest {
  productId: number;
  insuredId: number;
  compagnieId: number;
  duree: string;
  premium: number;
  /** Optionnel depuis le 2026-09-25 — même règle que ContractSubscriptionRequest.partnerShare. */
  partnerShare?: number;
  vehiculeId?: number;
  partenaireId?: number;
  dateEffet?: string;
  dateEcheance?: string;
  status?: ContractStatus;
  signedAt?: string;
  policyNumber?: string;
  salesRepId?: number;
  notes?: string;
  /** FRACTION (0.10 = 10 %) — voir percentToApiRate(). */
  commissionRate?: number;
  commissionAmount?: number;
  /** Voir ContractSubscriptionRequest.commercialCommissionAmount. */
  commercialCommissionAmount?: number;
  modePaiement?: ModePaiement;
  nombreEcheances?: number;
  periodiciteEcheances?: PeriodiciteEcheance;
  montantDejaVerse?: number;
}

export interface ContractResponse {
  id: number;
  policyNumber: string;
  dateEffet: string | null;
  duree: string;
  dateEcheance: string | null;
  productId: number;
  /** Nullable depuis le 2026-09-14 : le backend renvoie désormais null (au lieu d'un 500)
   * quand l'entité référencée par la FK a été supprimée (hard-delete). */
  productName: string | null;
  insuredId: number;
  insuredName: string | null;
  /** Nullable — confirmé présent le 2026-09-13, renseigné quand le contrat est lié à un
   * véhicule (flotte) via ContractRequest.vehiculeId. */
  vehiculeId?: number | null;
  vehiculeImmatriculation?: string | null;
  partenaireId: number;
  partenaireName: string | null;
  compagnieId: number;
  compagnieName: string | null;
  premium: number;
  commissionRate: number;
  commissionAmount: number;
  partnerShare: number;
  status: ContractStatus;
  commissionStatus: ContractCommissionStatus;
  signedAt: string | null;
  createdAt: string;
  salesRepId: number | null;
  salesRep: string | null;
  notes: string | null;
  exerciceComptableId: number | null;
  exerciceComptableLibelle: string | null;
  modePaiement: ModePaiement;
  nombreEcheances: number | null;
  periodiciteEcheances: PeriodiciteEcheance | null;
  montantDejaVerse: number | null;
  contratPrecedentId: number | null;
  contratPrecedentPolicyNumber: string | null;
  contratRenouvellementId: number | null;
  contratRenouvellementPolicyNumber: string | null;
  /** Champs client "à plat", ajoutés au DTO le 2026-08-22 en plus de insuredName/insuredId. */
  clientFirstName?: string | null;
  clientLastName?: string | null;
  clientPhone?: string | null;
  clientCity?: string | null;
  /** Non pertinent si partenaireId est null. */
  primeReversementStatus?: StatutReversementPrime | null;
  /** Commission du commercial interne ayant traité le contrat — non nuls/positifs uniquement
   * pour une vente directe (partenaireId null) ; une vente via partenaire reste rémunérée au
   * partenaire, pas au commercial (correction business rule du 2026-09-08). */
  commercialCommissionRate: number | null;
  commercialCommissionAmount: number;
  commercialCommissionStatus: ContractCommissionStatus;
}

export interface ContractPage {
  content: ContractResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type StatutEcheance = 'EN_ATTENTE' | 'PAYEE';

export interface ContractFractionneSummaryResponse {
  contractId: number;
  policyNumber: string;
  /** Nullable depuis le 2026-09-14 : le backend renvoie désormais null (au lieu d'un 500)
   * quand l'entité référencée par la FK a été supprimée (hard-delete). */
  insuredName: string | null;
  premium: number;
  nombreEcheances: number;
  periodiciteEcheances: PeriodiciteEcheance;
  echeancesPayees: number;
  echeancesEnAttente: number;
  echeancesEnRetard: number;
  montantPaye: number;
  montantRestantDu: number;
  prochaineEcheanceDate: string | null;
}

export interface EcheanceResponse {
  id: number;
  contractId: number;
  policyNumber: string;
  numeroEcheance: number;
  montant: number;
  dateEcheance: string;
  statut: StatutEcheance;
  enRetard: boolean;
  paiementId: number | null;
}

export interface PaiementRequest {
  montant: number;
  datePaiement: string;
  moyenPaiement: string;
  reference?: string;
  echeanceId?: number;
}

export interface PaiementResponse {
  id: number;
  contractId: number;
  policyNumber: string;
  montant: number;
  datePaiement: string;
  moyenPaiement: string;
  reference: string | null;
  recordedByUserId: number;
  recordedByUsername: string;
  creation: string;
  echeanceId: number | null;
}

/** Justificatif joint à un paiement d'échéance (reçu, capture de virement...) — voir
 * docs/api-contracts/paiement-piece-jointe.md. Même forme que SinistrePhotoResponse/
 * ReclamationPieceJointeResponse, sans champ "type" (pas de catégorie à distinguer ici). */
export interface PaiementPieceJointeResponse {
  id: number;
  paiementId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string;
  createdAt: string;
}

/** typeMessage/statutEnvoi values are backend-defined and not exhaustively documented —
 * kept as plain strings so unrecognized values still render instead of breaking. */
export interface ContractMessageResponse {
  id: number;
  contractId: number;
  telephoneAssure: string;
  message: string;
  typeMessage: string;
  dateEnvoi: string;
  statutEnvoi: string;
  responseProvider: string | null;
}
