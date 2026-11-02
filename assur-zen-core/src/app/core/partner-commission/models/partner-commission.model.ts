import { TypePartenaire } from '../../configuration/models/produit-commission-taux.model';

/** Bénéficiaire d'une commission configurée (backend du 2026-09-25, commit c0e3013). */
export type CommissionBeneficiaire = 'PARTENAIRE' | 'COMMERCIAL';

/** POST/PUT /partner-commissions (écritures ADMIN) — montant en FCFA entiers.
 * PARTENAIRE : exactement un de partenaireId / typePartenaire (400 sinon), commercialId interdit.
 * COMMERCIAL : commercialId (id User) obligatoire, partenaireId/typePartenaire interdits.
 * 409 si une configuration active de même clé chevauche la période. */
export interface PartnerCommissionRequest {
  produitId: number;
  beneficiaire: CommissionBeneficiaire;
  partenaireId?: number | null;
  typePartenaire?: TypePartenaire | null;
  commercialId?: number | null;
  montant: number;
  /** null = true. */
  active?: boolean | null;
  /** yyyy-MM-dd, facultatives. */
  dateDebut?: string | null;
  dateFin?: string | null;
}

export interface PartnerCommissionResponse {
  id: number;
  produitId: number;
  produitName: string | null;
  beneficiaire: CommissionBeneficiaire;
  partenaireId: number | null;
  partenaireNom: string | null;
  typePartenaire: TypePartenaire | null;
  commercialId: number | null;
  commercialUsername: string | null;
  montant: number;
  active: boolean;
  dateDebut: string | null;
  dateFin: string | null;
  creation: string;
  lastUpdate: string;
}

export interface PartnerCommissionFilter {
  produitId?: number;
  beneficiaire?: CommissionBeneficiaire;
  partenaireId?: number;
  commercialId?: number;
}
