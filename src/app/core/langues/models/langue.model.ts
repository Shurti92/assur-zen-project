export interface LangueResponse {
  id: number;
  code: string;
  libelle: string;
  active: boolean;
}

export interface LangueRequest {
  code: string;
  libelle: string;
  active?: boolean;
}
