export interface FundOutflowTypeRequest {
  code: string;
  nom: string;
  description?: string;
  ordre?: number;
  actif?: boolean;
}

export interface FundOutflowTypeResponse {
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

export interface FundOutflowTypePage {
  content: FundOutflowTypeResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
