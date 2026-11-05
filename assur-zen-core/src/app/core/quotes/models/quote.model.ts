import { VehicleCategory } from './quote-tariff.model';

export type QuoteStatus = 'CALCULE' | 'ACCEPTE' | 'PAYE' | 'CONVERTI' | 'EXPIRE';

/** Filtre `payment` de la liste (devis acceptés). */
export type QuotePaymentFilter = 'AUCUN' | 'EN_ATTENTE' | 'ECHOUE';

export type QuoteProduct = 'moto' | 'automobile-vtc' | 'voyage';

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
  /** Adresse géographique, facultative. */
  prospectAddress?: string;
  /** Enregistre puis envoie le lien du devis par SMS (mobile ivoirien valide). */
  sendSms?: boolean;
  vehicle: QuoteVehicleRequest;
}

/** Un envoi de GET /admin/quotes/{reference}/sms (plus récent d'abord). */
export interface QuoteSmsHistoryItem {
  sentAt: string;
  to: string;
  sentBy?: string | null;
  linkExpiresAt?: string | null;
}

/** Corps de POST /admin/quotes/{reference}/send-sms (téléphone du devis par défaut). */
export interface QuoteSendSmsRequest {
  phone?: string;
}

/** Un envoi de GET /admin/quotes/{reference}/emails (plus récent d'abord). */
export interface QuoteEmailHistoryItem {
  sentAt: string;
  to: string;
  sentBy?: string | null;
  withPdf?: boolean;
  /** Adresse de la copie « M'envoyer une copie », le cas échéant. */
  copyTo?: string | null;
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
  prospectAddress?: string;
  sendSms?: boolean;
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
  /** Adresse géographique et envoi du lien par SMS (contrat proposé, à confirmer par la gateway). */
  prospectAddress?: string | null;
  prospectPhoneSmsCapable?: boolean | null;
  prospectPhoneOperator?: 'ORANGE' | 'MTN' | 'MOOV' | null;
  smsSentCount?: number | null;
  lastSmsSentAt?: string | null;
  lastSmsTo?: string | null;
  lastSmsSentBy?: string | null;
  publicLinkExpiresAt?: string | null;
  /** VTC uniquement (null pour la moto). */
  fiscalPower?: number | null;
  energy?: VtcEnergy | null;
  seats?: number | null;
  firstRegistrationYear?: number | null;
  vtcDeclared?: boolean | null;
  platform?: string | null;
  discountPercent?: number | null;
  /** Voyage uniquement (null pour la moto et le VTC). */
  voyage?: QuoteVoyage | null;
  /** Paiement et conversion en contrat (absent sur une gateway plus ancienne). */
  conversion?: QuoteConversion | null;
  contractId?: number | string | null;
  policyNumber?: string | null;
}

export interface QuoteVoyage {
  /** `countryName` sur les gateways récentes ; `country` en repli. */
  countryName?: string | null;
  country?: string | null;
  space?: string | null;
  spaceLabel?: string | null;
  travelerBirthDate?: string | null;
  travelerAge?: number | null;
  passportNumber?: string | null;
  departureDate?: string | null;
  returnDate?: string | null;
  durationDays?: number | null;
}

/** Corps de POST /admin/quotes/voyage (devis voyage saisi au back-office). */
export interface VoyageQuoteCreateRequest {
  /** Nom du voyageur. */
  prospectName: string;
  prospectPhone: string;
  prospectEmail?: string;
  sendEmail?: boolean;
  prospectAddress?: string;
  sendSms?: boolean;
  voyage: {
    countryId: number;
    birthDate: string;
    departureDate: string;
    returnDate: string;
    travelerName?: string;
    passportNumber?: string;
  };
}

export interface QuoteConversion {
  status: 'NONE' | 'PENDING' | 'CONFIRMED' | 'FAILED';
  paymentReference?: string | null;
  paidAt?: string | null;
  reminderCount?: number | null;
  lastReminderAt?: string | null;
}

export type QuoteDocumentType = 'ATTESTATION' | 'CONDITIONS_PARTICULIERES' | 'CARTE_GRISE' | 'CONTRAT' | 'PIECE_IDENTITE' | 'AUTRE';

/** Un document joint à un devis payé (GET /admin/quotes/{reference}/documents). */
export interface QuoteDocument {
  id: number | string;
  type: QuoteDocumentType;
  fileName: string;
  contentType?: string | null;
  sizeBytes: number;
  uploadedBy?: string | null;
  uploadedAt: string;
}

/** Corps de POST /admin/quotes/{reference}/convert. */
export interface QuoteConvertRequest {
  policyNumber?: string;
  companyId: number;
  partnerId?: number;
  commissionRate?: number;
  insuredId?: number;
  createInsured?: boolean;
  productId?: number;
}

/** Entrée des listes légères du formulaire de finalisation. */
export interface QuoteLookup {
  id: number;
  code?: string | null;
  name: string;
}

export interface QuoteInsuredCandidate {
  id: number;
  nom: string;
  prenoms: string;
  clientPhone?: string | null;
  clientCity?: string | null;
  source?: 'LIE_AU_COMPTE' | 'TELEPHONE_DU_COMPTE' | 'RECHERCHE';
}

/** GET /admin/quotes/{reference}/contract : contrat créé à partir du devis (409 QUOTE_NOT_CONVERTED sinon). */
export interface QuoteContract {
  contractId: number | string;
  policyNumber: string;
  provisionalPolicyNumber?: boolean | null;
  status?: string | null;
  productId?: number | null;
  productName?: string | null;
  companyId?: number | null;
  companyName?: string | null;
  partnerId?: number | null;
  partnerName?: string | null;
  commissionRate?: number | null;
  commissionAmount?: number | null;
  insuredId?: number | null;
  insuredName?: string | null;
  dateEffet?: string | null;
  dateEcheance?: string | null;
  premium?: number | null;
}

export interface QuotePaymentReminderItem {
  sentAt: string;
  to: string;
  sentBy?: string | null;
}

export interface QuoteList {
  items: QuoteView[];
}
