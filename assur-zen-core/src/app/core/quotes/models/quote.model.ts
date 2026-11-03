import { VehicleCategory } from './quote-tariff.model';

export type QuoteStatus = 'CALCULE' | 'ACCEPTE' | 'EXPIRE';

export interface QuotePremium {
  netPremium: number;
  taxes: number;
  accessories: number;
  fga: number;
  total: number;
  currency: string;
}

export interface QuoteGuaranteeDetail {
  label: string;
  /** Montant déjà formaté par la gateway. */
  amount: string;
}

export interface QuoteGuarantee {
  label: string;
  details?: QuoteGuaranteeDetail[];
}

/** Corps de la simulation (public) et partie `vehicle` de la création d'un devis. */
export interface QuoteVehicleRequest {
  vehicleType: VehicleCategory;
  cylinderCc: number;
  brand: string;
  model: string;
  durationMonths: number;
  registrationNumber?: string;
  chassisNumber?: string;
  effectiveDate: string;
}

export interface QuoteCreateRequest {
  prospectName: string;
  prospectPhone: string;
  vehicle: QuoteVehicleRequest;
}

/** Réponse de /public/quotes/moto/simulate (aucune écriture). */
export interface QuoteSimulation {
  productKey: string;
  formulaId: string;
  formulaName: string;
  vehicleType: VehicleCategory;
  durationMonths: number;
  effectiveDate: string;
  expiryDate: string;
  premium: QuotePremium;
  guarantees: QuoteGuarantee[];
}

export interface QuoteForm {
  productKey: string;
  vehicleTypes: VehicleCategory[];
  durations: { months: number; total: number }[];
  formulaId: string;
  formulaName: string;
  minEffectiveDate: string;
}

export interface QuoteView {
  reference: string;
  status: QuoteStatus;
  canAccept: boolean;
  productKey?: string;
  formulaId?: string;
  formulaName: string;
  vehicleType: VehicleCategory;
  cylinderCc: number;
  brand: string;
  model: string;
  registrationNumber?: string | null;
  chassisNumber?: string | null;
  durationMonths: number;
  effectiveDate: string;
  expiryDate: string;
  validUntil: string;
  premium: QuotePremium;
  guarantees: QuoteGuarantee[];
  createdAt: string;
  acceptedAt?: string | null;
  channel?: string | null;
  prospectName?: string | null;
  prospectPhone?: string | null;
  createdByUserId?: number | null;
}

export interface QuoteList {
  items: QuoteView[];
}
