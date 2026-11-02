export interface ProduitRequest {
  nomProduit: string;
  active: boolean;
  /** Ajouté au DTO côté backend le 2026-08-21 — référence /configuration/type-produit. */
  productTypeId?: number;
}

export interface ProduitResponse extends ProduitRequest {
  id: number;
  creation: string;
  lastUpdate: string;
  productTypeCode?: string;
  productTypeNom?: string;
}

export interface ProduitPage {
  content: ProduitResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
