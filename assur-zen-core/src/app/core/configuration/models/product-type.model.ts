export interface ProductTypeRequest {
  code: string;
  nom: string;
  description?: string;
  icone?: string;
  couleur?: string;
  ordre?: number;
  actif?: boolean;
}

export interface ProductTypeResponse {
  id: number;
  uuid: string;
  code: string;
  nom: string;
  description: string | null;
  icone: string | null;
  couleur: string | null;
  ordre: number | null;
  actif: boolean;
  systeme: boolean;
  nombreProduits: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductTypeSummaryResponse {
  id: number;
  code: string;
  nom: string;
  icone: string | null;
  couleur: string | null;
}

export interface ProductTypePage {
  content: ProductTypeResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
