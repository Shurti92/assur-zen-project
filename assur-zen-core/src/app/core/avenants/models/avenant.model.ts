/** 36 valeurs confirmées via /v3/api-docs (schéma AvenantRequest/AvenantResponse, vérifié
 * le 2026-08-25) — liste fermée côté backend, ne pas raccourcir. */
export type AvenantType =
  | 'MODIFICATION_ASSURE'
  | 'MODIFICATION_SOUSCRIPTEUR'
  | 'MODIFICATION_COORDONNEES'
  | 'MODIFICATION_ADRESSE'
  | 'MODIFICATION_BENEFICIAIRE'
  | 'CORRECTION_INFORMATION'
  | 'CHANGEMENT_INTERMEDIAIRE'
  | 'CHANGEMENT_MODE_PAIEMENT'
  | 'CHANGEMENT_FREQUENCE_PAIEMENT'
  | 'MODIFICATION_RISQUE'
  | 'MODIFICATION_BIEN_ASSURE'
  | 'REMPLACEMENT_BIEN'
  | 'AJOUT_BIEN'
  | 'RETRAIT_BIEN'
  | 'AJOUT_GARANTIE'
  | 'SUPPRESSION_GARANTIE'
  | 'MODIFICATION_GARANTIE'
  | 'MODIFICATION_FRANCHISE'
  | 'MODIFICATION_PLAFOND'
  | 'MODIFICATION_USAGE'
  | 'MODIFICATION_ZONE'
  | 'MODIFICATION_CONDUCTEUR'
  | 'MODIFICATION_CARACTERISTIQUE'
  | 'EXTENSION_TERRITORIALE'
  | 'REDUCTION_TERRITORIALE'
  | 'MODIFICATION_PRIME'
  | 'SURPRIME'
  | 'REMISE'
  | 'ANNULATION_REMISE'
  | 'PRIME_COMPLEMENTAIRE'
  | 'REDUCTION_PRIME'
  | 'REMBOURSEMENT_PRIME'
  | 'REGULARISATION_PRIME'
  | 'MODIFICATION_TAXE'
  | 'REGULARISATION_FINANCIERE'
  | 'ANNULATION_OPERATION_FINANCIERE'
  | 'TRANSFERT_CREDIT';

/** Calculée par le backend selon le type d'avenant — jamais envoyée par le front. */
export type AvenantCategorie = 'ADMINISTRATIF' | 'TECHNIQUE' | 'FINANCIER';

export interface AvenantRequest {
  type: AvenantType;
  dateEffet: string;
  motif: string;
  description?: string;
  premium?: number;
  commissionRate?: number;
  commissionAmount?: number;
  partenaireId?: number;
  compagnieId?: number;
}

/** Tous les champs "ancien*"/"nouveau*" au-delà de ceux effectivement modifiés par cet
 * avenant précis sont `null` — n'afficher que les paires non-null. */
export interface AvenantResponse {
  id: number;
  contractId: number;
  contractPolicyNumber: string;
  numero: number;
  type: AvenantType;
  categorie: AvenantCategorie;
  dateEffet: string;
  motif: string;
  description: string | null;
  ancienPremium: number | null;
  nouveauPremium: number | null;
  ancienneCommissionRate: number | null;
  nouvelleCommissionRate: number | null;
  ancienCommissionAmount: number | null;
  nouveauCommissionAmount: number | null;
  ancienPartenaireId: number | null;
  ancienPartenaireNom: string | null;
  nouveauPartenaireId: number | null;
  nouveauPartenaireNom: string | null;
  ancienneCompagnieId: number | null;
  ancienneCompagnieNom: string | null;
  nouvelleCompagnieId: number | null;
  nouvelleCompagnieNom: string | null;
  createdById: number;
  createdByUsername: string;
  createdAt: string;
}
