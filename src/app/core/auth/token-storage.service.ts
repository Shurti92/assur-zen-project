import { Injectable } from '@angular/core';

import { AuthTokens, AuthUser, LoginSuccessResponse } from './models/auth.model';

const ACCESS_TOKEN_KEY = 'auth_access_token';
const USER_KEY = 'auth_user';
const USERNAME_KEY = 'auth_username';
/** Plus jamais écrite (voir setSession/setTokens) — conservée uniquement pour purger, chez tout
 * utilisateur déjà connecté avant la migration, le vrai refresh token qu'elle contenait. Sans ce
 * nettoyage explicite, ce jeton valide restait indéfiniment lisible en JS dans localStorage : ni
 * login() (n'écrit plus dessus) ni l'ancien clearSession() (ne la supprimait déjà plus) ne
 * l'auraient jamais effacée. Confirmé en direct le 2026-08-25 : un access token ADMIN valide
 * dormait toujours dans ce champ après une déconnexion/reconnexion post-migration. */
const LEGACY_REFRESH_TOKEN_KEY = 'auth_refresh_token';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  /** Le refresh token n'est plus stocke ici (audit de securite pre-production, 2026-08-25) : le
   * backend le pose desormais en cookie httpOnly (voir LoginController.poserCookieRefreshToken),
   * inaccessible en JS. Le garder en localStorage rendait un vol de session exploitable jusqu'a
   * 7 jours par la moindre XSS ailleurs dans l'app. */
  setSession(response: LoginSuccessResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, response.tokens.accessToken);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
    localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY);
  }

  setTokens(tokens: AuthTokens): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY);
  }

  updateUser(user: AuthUser): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  setUsername(username: string): void {
    localStorage.setItem(USERNAME_KEY, username);
  }

  getUsername(): string | null {
    return localStorage.getItem(USERNAME_KEY);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getUser(): AuthUser | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  }

  /** Clears tokens and user but keeps the remembered username (e.g. for a pending OTP challenge). */
  clearSession(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY);
  }

  clear(): void {
    this.clearSession();
    localStorage.removeItem(USERNAME_KEY);
  }
}
