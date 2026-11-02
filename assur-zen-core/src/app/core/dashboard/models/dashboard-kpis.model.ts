import { TypePartenaire } from '../../partenaire/models/partenaire.model';

export type DashboardPeriod = 'month' | 'all';

export interface DashboardVolumes {
  totalContracts: number;
  signedContracts: number;
  pendingContracts: number;
  /** Fraction 0–1, not a percentage. */
  conversionRate: number;
}

export interface DashboardFinance {
  production: number;
  totalCommission: number;
  paidCommission: number;
  pendingCommission: number;
  partnerShare: number;
  /** Ajoutés au DTO le 2026-08-25. */
  primeNonReverseeCount: number;
  primeNonReverseeMontant: number;
  avenantsCount: number;
}

export interface DashboardObjectifGlobal {
  montant: number;
  /** Fraction 0–1, not a percentage. 0 when montant is 0. */
  progression: number;
}

export interface DashboardCycleDeVie {
  realises: number;
  echeanceProche: number;
  nonRenouveles: number;
}

export interface ProduitRepartition {
  productId: number;
  productName: string;
  count: number;
}

export interface PartenaireObjectif {
  partenaireId: number;
  nom: string;
  type: TypePartenaire;
  objectif: number;
  realise: number;
  /** Percentage already (e.g. 6.5 = 6.5 %), not a fraction. */
  taux: number;
}

export interface DashboardObjectifParApporteur {
  totalObjectif: number;
  totalRealise: number;
  /** Percentage already, not a fraction. */
  tauxGlobal: number;
  partenaires: PartenaireObjectif[];
}

export interface DashboardBackOfficeKpis {
  volumes: DashboardVolumes;
  finance: DashboardFinance;
  objectifGlobal: DashboardObjectifGlobal;
  cycleDeVie: DashboardCycleDeVie;
  repartitionParProduit: ProduitRepartition[];
  objectifParApporteur: DashboardObjectifParApporteur;
}
