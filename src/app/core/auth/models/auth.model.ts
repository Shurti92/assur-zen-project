export interface LoginRequest {
  username: string;
  password: string;
}

/** Corps d'erreur de POST /auth/login sur échec (401 identifiants invalides, 423 compte verrouillé).
 * Confirmé en direct le 2026-08-10 (voir docs/api-contracts/account-lockout.md §4) : le champ de
 * code d'erreur s'appelle `error` sur ces deux réponses spécifiquement — à ne pas confondre avec le
 * `ErrorResponse` générique (`code`) partagé par les autres endpoints d'erreur de l'API.
 * `remainingAttempts` est `null` pour un identifiant inexistant (le backend ne dévoile jamais si le
 * compte existe) et un nombre pour un compte réel dont le mot de passe est incorrect.
 * `lockedUntil`/`remainingSeconds` ne sont présents que sur la réponse `423`. */
export interface LoginFailureResponse {
  error: string;
  message: string;
  timestamp: string;
  remainingAttempts: number | null;
  lockedUntil?: string;
  remainingSeconds?: number;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface AuthUser {
  id: string;
  fullName: string;
  email: string;
  role: string;
  /** Présent uniquement pour un compte de partenaire (créé automatiquement, voir
   * docs/api-contracts/creation-comptes-utilisateurs.md) — absent pour un compte
   * back-office classique. Sert de repli pour résoudre le Type Profil (via
   * GET /partenaires/{id}) tant que `typeProfileId` ci-dessous n'est pas livré. */
  partenaireId?: number;
  /** Pas encore livré par le backend (voir docs/api-contracts/user-type-profile-id.md) —
   * FK vers /type-profiles de l'utilisateur connecté. Piloté NavPermissionsService pour
   * masquer les menus Paramétrage/Administration selon le Type Profil. */
  typeProfileId?: number;
  sector?: string;
  avatarUrl?: string | null;
  mustChangePassword: boolean;
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
  /** Langue préférée de l'utilisateur (ex. "fr") — pas encore consommé ailleurs dans l'app,
   * qui pilote la langue active via LanguageService (choix persisté en localStorage). */
  langueCode?: string;
}

export interface LoginSuccessResponse {
  user: AuthUser;
  tokens: AuthTokens;
}

export interface OtpChallengeResponse {
  otpRequired: true;
  challengeToken: string;
  maskedPhone: string;
  expiresInSeconds: number;
}

/** /auth/login is polymorphic: tokens directly if no phone is on file, otherwise an OTP challenge. */
export type LoginResponse = LoginSuccessResponse | OtpChallengeResponse;

export function isOtpChallenge(response: LoginResponse): response is OtpChallengeResponse {
  return 'otpRequired' in response;
}

export interface LoginOtpVerifyRequest {
  challengeToken: string;
  code: string;
}

export interface LoginOtpResendRequest {
  challengeToken: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ForgotPasswordRequest {
  username: string;
}

export interface ResetPasswordRequest {
  username: string;
  code: string;
  newPassword: string;
  confirmPassword: string;
  /** Ajouté au DTO côté backend le 2026-08-21, usage non documenté — jamais envoyé pour l'instant. */
  confirmationValid?: boolean;
}

export interface JwtPayload {
  sub: string;
  username: string;
  roles: string;
  typ: 'access' | 'refresh';
  iat: number;
  exp: number;
  jti: string;
}
