import { VehicleCategory } from './quote-tariff.model';

export type QuoteStatus = 'CALCULE' | 'ACCEPTE' | 'EXPIRE';

export type QuoteProduct = 'moto' | 'automobile-vtc';

export type VtcEnergy = 'ESSENCE' | 'DIESEL' | 'HYBRIDE' | 'ELECTRIQUE';

export interface QuotePremium {
  netPremium: number;
  taxes: number;
  accessories: number;
  fga: number;
  /** VTC uniquement (0 pour la moto). */
  cedeao?: number;
  attestation?: number;
  discount?: number;
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

/** Corps de la simulation (public) et partie `vehicle` de la création d'un devis moto. */
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
  /** Facultatif ; envoyé seulement s'il est renseigné. */
  prospectEmail?: string;
  /** Enregistre puis envoie le devis par e-mail (nécessite prospectEmail). */
  sendEmail?: boolean;
  vehicle: QuoteVehicleRequest;
}

/** Corps de POST /admin/quotes/{reference}/send-email. */
export interface QuoteSendEmailRequest {
  email: string;
  message?: string;
  attachPdf: boolean;
  copyToMe: boolean;
}

/** Véhicule d'un devis automobile VTC (simulation publique, sans remise ; création avec remise facultative). */
export interface VtcVehicleRequest {
  fiscalPower: number;
  energy: VtcEnergy;
  brand: string;
  model: string;
  firstRegistrationYear: number;
  seats: number;
  vtcDeclared: boolean;
  platform?: string;
  durationMonths: number;
  registrationNumber?: string;
  chassisNumber?: string;
  effectiveDate: string;
  discountPercent?: number;
}

export interface VtcQuoteCreateRequest {
  prospectName: string;
  prospectPhone: string;
  prospectEmail?: string;
  sendEmail?: boolean;
  vehicle: VtcVehicleRequest;
}

/** Réponse de /public/quotes/{moto|vtc}/simulate (aucune écriture). */
export interface QuoteSimulation {
  productKey: string;
  formulaId: string;
  formulaName: string;
  vehicleType?: VehicleCategory;
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
  brands?: string[];
}

/** Réponse de /public/quotes/vtc/form. */
export interface VtcQuoteForm {
  energies: VtcEnergy[];
  durations: { months: number; total: number }[] | number[];
  brands?: string[];
  platforms?: string[];
  minFiscalPower: number;
  minSeats: number;
  maxSeats: number;
  minEffectiveDate?: string;
}

export interface QuoteView {
  reference: string;
  status: QuoteStatus;
  canAccept: boolean;
  productKey?: string;
  formulaId?: string;
  formulaName: string;
  /** Moto uniquement. */
  vehicleType?: VehicleCategory | null;
  cylinderCc?: number | null;
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
  /** Envoi par e-mail (contrat proposé, à confirmer par la gateway). */
  prospectEmail?: string | null;
  emailSentCount?: number | null;
  lastEmailSentAt?: string | null;
  lastEmailTo?: string | null;
  lastEmailSentBy?: string | null;
  /** VTC uniquement (null pour la moto). */
  fiscalPower?: number | null;
  energy?: VtcEnergy | null;
  seats?: number | null;
  firstRegistrationYear?: number | null;
  vtcDeclared?: boolean | null;
  platform?: string | null;
  discountPercent?: number | null;
}

export interface QuoteList {
  items: QuoteView[];
}
