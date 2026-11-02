export interface GedDocumentCategoryRequest {
  code: string;
  nom: string;
  description?: string;
  ordre?: number;
  actif?: boolean;
}

export interface GedDocumentCategoryResponse {
  id: number;
  uuid: string;
  code: string;
  nom: string;
  description: string | null;
  ordre: number | null;
  actif: boolean;
  systeme: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface GedDocumentCategoryPage {
  content: GedDocumentCategoryResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
