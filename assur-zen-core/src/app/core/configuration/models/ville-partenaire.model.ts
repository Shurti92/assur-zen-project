export interface VillePartenaireRequest {
  nomVille: string;
  regions: string;
  active: boolean;
}

export interface VillePartenaireResponse extends VillePartenaireRequest {
  id: number;
  creation: string;
  lastUpdate: string;
}
