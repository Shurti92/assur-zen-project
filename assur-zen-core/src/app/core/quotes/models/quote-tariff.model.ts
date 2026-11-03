/** MOTO / TRICYCLE pour la moto ; « VTC » (suffixe possible : « VTC-2 ») pour l'automobile VTC. */
export type VehicleCategory = string;

/** Ligne de la grille tarifaire des devis en ligne — /admin/quotes/tariffs (rôle ADMIN). */
export interface QuoteTariff {
  id?: number;
  productKey: string;
  formulaId: string;
  vehicleCategory: VehicleCategory;
  durationMonths: number;
  netPremium: number;
  taxes: number;
  accessories: number;
  fga: number;
  /** VTC uniquement (0 pour la moto). */
  cedeao?: number | null;
  attestation?: number | null;
  minFiscalPower?: number | null;
  minSeats?: number | null;
  maxSeats?: number | null;
  /** = netPremium + taxes + accessories + fga + cedeao + attestation. */
  total: number;
  active: boolean;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface QuoteTariffList {
  items: QuoteTariff[];
}

export type QuoteTariffRequestItem = Omit<QuoteTariff, 'id' | 'updatedAt' | 'updatedBy'>;
