export interface GedDocumentTypeRequest {
  code: string;
  nom: string;
  description?: string;
  ordre?: number;
  actif?: boolean;
}

export interface GedDocumentTypeResponse {
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

export interface GedDocumentTypePage {
  content: GedDocumentTypeResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
