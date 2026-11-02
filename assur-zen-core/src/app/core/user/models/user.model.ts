/**
 * Comptes utilisateur back-office ("Créer un utilisateur et son profil en une seule opération").
 * Distinct de `core/profile/` — `ProfileService` (`GET/PUT /profiles`) ne peut qu'enrichir la
 * fiche d'un compte déjà existant (`userId` requis), il ne crée jamais de compte.
 * Voir docs/api-contracts/creation-comptes-utilisateurs.md §4bis.
 */
export interface UserProfileRequest {
  username: string;
  /** Optionnel — généré aléatoirement côté backend si absent (voir `temporaryPassword` en réponse). */
  password?: string;
  /** Rôle(s) affecté(s) au compte — valeurs valides via `UserService.getRoles()`, jamais codées en dur. */
  roles: string;
  typeProfileId?: number | null;
  firstname: string;
  lastname: string;
  phone?: string;
  email?: string;
  villePartenaireId?: number | null;
  active?: boolean;
  /** Ajoutés au DTO côté backend le 2026-08-21, aucun champ de formulaire dédié pour l'instant —
   * disponibles pour un enrichissement ultérieur de la fiche compte si besoin. */
  code?: string;
  role?: string;
  sector?: string;
  bio?: string;
  facebookUrl?: string;
  twitterUrl?: string;
  linkedinUrl?: string;
  instagramUrl?: string;
  country?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  taxId?: string;
  twoFactorEnabled?: boolean;
}

export interface UserProfileResponse {
  userId: number;
  username: string;
  roles: string;
  userActive: boolean;
  profileId: number;
  code: string | null;
  firstname: string;
  lastname: string;
  phone: string | null;
  email: string | null;
  profileActive: boolean;
  villePartenaireId: number | null;
  /** Présent uniquement sur la réponse de création — mot de passe généré, affiché une seule fois. */
  temporaryPassword?: string | null;
  /** Confirme si le SMS de bienvenue (identifiant + mot de passe) a bien été envoyé. */
  notificationEnvoyee?: boolean;
  /** Ajoutés au DTO côté backend le 2026-08-21, non affichés pour l'instant. */
  role?: string | null;
  sector?: string | null;
  bio?: string | null;
  facebookUrl?: string | null;
  twitterUrl?: string | null;
  linkedinUrl?: string | null;
  instagramUrl?: string | null;
  country?: string | null;
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
  taxId?: string | null;
  twoFactorEnabled?: boolean;
}

export interface UserPage {
  content: UserProfileResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserFilter {
  q?: string;
  active?: boolean;
  roles?: string;
  typeProfileId?: number;
  villePartenaireId?: number;
}

/** Schéma corrigé le 2026-08-21 (re-vérifié via /v3/api-docs) — le backend renvoie createdAt/revoked,
 * pas dateConnexion/ipAddress/userAgent comme précédemment supposé. Aucune vue ne consomme encore
 * getLoginHistory(), donc pas d'affichage à corriger, seulement le modèle. */
export interface UserSessionResponse {
  id: number;
  createdAt: string;
  revoked: boolean;
}

export interface UserSessionPage {
  content: UserSessionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
