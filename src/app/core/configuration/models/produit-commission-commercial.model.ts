export interface ProduitCommissionCommercialRequest {
  produitId: number;
  tauxCommission: number;
  libTauxCommission: string;
  active: boolean;
}

export interface ProduitCommissionCommercialResponse extends ProduitCommissionCommercialRequest {
  id: number;
  produitName: string;
  creation: string;
  lastUpdate: string;
}
