/**
 * api-types.ts — Types partagés du contrat d'API « Espace Partenaire » (Assurance).
 *
 * SOURCE DE VÉRITÉ : docs/openapi.yaml. Ce fichier en est la traduction
 * TypeScript, fournie pour que le backend utilise EXACTEMENT les mêmes
 * structures (noms de champs camelCase, types, enums). Toute divergence avec
 * openapi.yaml est un bug.
 *
 * Rappels :
 *  - Montants : entiers en FCFA (number sans décimales).
 *  - Taux (…Rate, …Progress) : nombres entre 0 et 1.
 *  - Dates : chaînes ISO 8601 (date-time UTC) ou null.
 */

/* -------------------------------------------------------------------------- */
/* Énumérations                                                               */
/* -------------------------------------------------------------------------- */

export type Role = 'commercial' | 'manager';

/** signed = en vigueur · pending = en attente · draft = devis · cancelled = résilié */
export type ContractStatus = 'signed' | 'pending' | 'draft' | 'cancelled';

/** Filtre de la liste : statuts ci-dessus + 'all' (pas de filtre). */
export type ContractStatusFilter = 'all' | ContractStatus;

/** paid = commission versée · pending = à venir */
export type CommissionStatus = 'paid' | 'pending';

/** month = mois courant · all = tout le portefeuille */
export type DashboardPeriod = 'month' | 'all';

/* -------------------------------------------------------------------------- */
/* Entités                                                                    */
/* -------------------------------------------------------------------------- */

export interface User {
  id: string;
  fullName: string;
  email: string;
  role: Role;
  sector: string;
  avatarUrl?: string | null;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  /** Durée de validité de l'access token, en SECONDES. */
  expiresIn: number;
}

export interface Credentials {
  username: string;
  password: string;
}

/** Mise à jour partielle du profil (PATCH /auth/me). */
export interface ProfilePatch {
  fullName?: string;
  email?: string;
  sector?: string;
}

export interface Contract {
  id: string;
  policyNumber: string;
  clientFirstName: string;
  clientLastName: string;
  clientPhone: string;
  clientCity: string;
  /** Branche d'assurance (Auto, Habitation (MRH), Santé, …). */
  product: string;
  /** Prime annuelle (FCFA). */
  premium: number;
  /** Taux de commission (0..1), dépend de la branche. */
  commissionRate: number;
  /** INVARIANT : commissionAmount = round(premium * commissionRate). */
  commissionAmount: number;
  /** INVARIANT : partnerShare = premium - commissionAmount. */
  partnerShare: number;
  status: ContractStatus;
  commissionStatus: CommissionStatus;
  /** Date d'effet (ISO) ; null tant que le contrat n'est pas en vigueur. */
  effectiveDate: string | null;
  /** Date de signature (ISO) ; null si non signé. */
  signedAt: string | null;
  createdAt: string;
  salesRep: string;
  notes?: string | null;
}

export interface DashboardKPIs {
  totalContracts: number;
  signedContracts: number;
  pendingContracts: number;
  /** INVARIANT : Σ premium where status=signed. */
  production: number;
  /** INVARIANT : Σ commissionAmount where status=signed. */
  totalCommission: number;
  /** Part de totalCommission avec commissionStatus=paid. */
  paidCommission: number;
  /** INVARIANT : totalCommission - paidCommission. */
  pendingCommission: number;
  /** INVARIANT : Σ partnerShare where status=signed. */
  partnerShare: number;
  /** INVARIANT : signedContracts / totalContracts (0 si aucun). */
  conversionRate: number;
  /** Objectif de production (FCFA). */
  monthlyObjective: number;
  /** INVARIANT : min(production / monthlyObjective, 1). */
  objectiveProgress: number;
}

/** Enveloppe d'erreur homogène (toutes routes). */
export interface ApiError {
  /** Message lisible, affiché tel quel à l'utilisateur. */
  message: string;
  /** Code machine optionnel (ex. INVALID_CREDENTIALS). */
  code?: string;
}

/* -------------------------------------------------------------------------- */
/* Payloads & réponses par endpoint                                           */
/* -------------------------------------------------------------------------- */

/** POST /auth/login */
export type LoginRequest = Credentials;
export interface LoginResponse {
  user: User;
  tokens: AuthTokens;
}

/** POST /auth/refresh */
export interface RefreshRequest {
  refreshToken: string;
}
export type RefreshResponse = AuthTokens;

/** POST /auth/logout → 204 No Content (pas de corps) */
export type LogoutResponse = void;

/** GET /auth/me */
export type MeResponse = User;

/** PATCH /auth/me */
export type UpdateMeRequest = ProfilePatch;
export type UpdateMeResponse = User;

/** GET /contracts?search=&status= */
export interface ListContractsQuery {
  search?: string;
  status?: ContractStatusFilter;
}
export type ListContractsResponse = Contract[];

/** GET /contracts/{id} */
export type GetContractResponse = Contract;

/** GET /dashboard/kpis?period= */
export interface DashboardQuery {
  period: DashboardPeriod;
}
export type DashboardKpisResponse = DashboardKPIs;
