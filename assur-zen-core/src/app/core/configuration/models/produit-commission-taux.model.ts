export type TypePartenaire = 'DIRECT' | 'AGENT' | 'APPORTEUR';

export interface ProduitCommissionTauxRequest {
  produitId: number;
  typePartenaire: TypePartenaire;
  tauxCommission: number;
  libTauxCommission: string;
  active: boolean;
}

export interface ProduitCommissionTauxResponse extends ProduitCommissionTauxRequest {
  id: number;
  produitName: string;
  creation: string;
  lastUpdate: string;
}
