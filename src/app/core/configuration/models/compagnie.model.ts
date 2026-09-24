export type CompagnieType = 'VIE' | 'NON_VIE';

export interface CompagnieRequest {
  codeCompagnie: string;
  nom: string;
  active: boolean;
  type?: CompagnieType | null;
}

export interface CompagnieResponse extends CompagnieRequest {
  id: number;
  creation: string;
  lastUpdate: string;
}

export interface CompagniePage {
  content: CompagnieResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
