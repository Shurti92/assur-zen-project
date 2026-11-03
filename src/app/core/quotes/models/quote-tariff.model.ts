export type VehicleCategory = 'MOTO' | 'TRICYCLE';

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
  total: number;
  active: boolean;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface QuoteTariffList {
  items: QuoteTariff[];
}

export type QuoteTariffRequestItem = Omit<QuoteTariff, 'id' | 'updatedAt' | 'updatedBy'>;
