export interface ProfileRecord extends ProfileDetails {
  id: number;
  /** Optimistic-locking token — returned by the backend, not accepted on write. */
  version?: number;
  /** Lecture seule, ajoutés au DTO le 2026-08-21. */
  creation?: string;
  lastUpdate?: string;
}

export interface ProfileDetails {
  code: string;
  firstname: string;
  lastname: string;
  phone: string;
  role: string;
  userId: number;
  villePartenaireId: number;
  active: boolean;
  expiration: boolean;
  bio: string;
  facebookUrl: string;
  twitterUrl: string;
  linkedinUrl: string;
  instagramUrl: string;
  country: string;
  city: string;
  state: string;
  postalCode: string;
  taxId: string;
  twoFactorEnabled: boolean;
  /** Ajoutés au DTO côté backend le 2026-08-21 (voir /v3/api-docs) — pas encore de champ de
   * formulaire dédié dans l'écran Profil. */
  email?: string;
  sector?: string;
  avatarUrl?: string;
  langueCode?: string;
}
