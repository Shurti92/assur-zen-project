import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, tap, throwError } from 'rxjs';

import {
  AuthTokens,
  AuthUser,
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  LoginSuccessResponse,
  ResetPasswordRequest,
  isOtpChallenge
} from './models/auth.model';
import { LockService } from './lock.service';
import { TokenStorageService } from './token-storage.service';
import { LogService } from '../logging/log.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUserSignal = signal<AuthUser | null>(this.tokenStorage.getUser());
  /** In-memory only: an OTP challenge only ever exists between login() and a successful verifyLoginOtp(). No tokens exist yet at this stage, so a page reload naturally loses it. */
  private readonly challengeTokenSignal = signal<string | null>(null);
  private readonly maskedPhoneSignal = signal<string | null>(null);
  private readonly otpExpiresInSecondsSignal = signal<number | null>(null);
  private readonly returnUrlSignal = signal<string>('/dashboard');
  private refreshInProgress$: Observable<AuthTokens> | null = null;
  private sessionExpiredHandled = false;

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly maskedPhone = this.maskedPhoneSignal.asReadonly();
  readonly otpExpiresInSeconds = this.otpExpiresInSecondsSignal.asReadonly();
  readonly returnUrl = this.returnUrlSignal.asReadonly();

  readonly mustChangePassword = computed(() => this.currentUserSignal()?.mustChangePassword ?? false);
  readonly hasOtpChallenge = computed(() => !!this.challengeTokenSignal());

  /** Fully signed in: tokens issued (any required OTP already passed) and no forced password change pending. */
  readonly isAuthenticated = computed(() => !!this.currentUserSignal() && !this.mustChangePassword());

  constructor(
    private readonly http: HttpClient,
    private readonly tokenStorage: TokenStorageService,
    private readonly lockService: LockService,
    private readonly router: Router,
    private readonly logService: LogService
  ) {}

  login(credentials: LoginRequest): Observable<LoginResponse> {
    // withCredentials : necessaire pour que le navigateur accepte le Set-Cookie du refresh token
    // (httpOnly) sur un appel cross-origin (l'API prod est sur un port distinct du frontend).
    return this.http.post<LoginResponse>('/auth/login', credentials, { withCredentials: true }).pipe(
      tap((response) => {
        this.tokenStorage.setUsername(credentials.username);
        if (isOtpChallenge(response)) {
          // A fresh OTP challenge supersedes any leftover session from a prior login:
          // otherwise a stale currentUser would let otpGuard/authGuard skip the OTP step entirely.
          this.tokenStorage.clearSession();
          this.currentUserSignal.set(null);
          this.challengeTokenSignal.set(response.challengeToken);
          this.maskedPhoneSignal.set(response.maskedPhone);
          this.otpExpiresInSecondsSignal.set(response.expiresInSeconds);
          this.logService.info('auth', `Challenge OTP envoyé pour "${credentials.username}"`);
        } else {
          this.establishSession(response);
          this.logService.info('auth', `Connexion réussie pour "${credentials.username}"`);
        }
      }),
      catchError((error: unknown) => {
        this.logService.warn('auth', `Échec de connexion pour "${credentials.username}"`);
        return throwError(() => error);
      })
    );
  }

  verifyLoginOtp(code: string): Observable<LoginSuccessResponse> {
    const challengeToken = this.challengeTokenSignal();
    return this.http.post<LoginSuccessResponse>('/auth/login/otp-verify', { challengeToken, code }, { withCredentials: true }).pipe(
      tap((response) => {
        this.establishSession(response);
        this.logService.info('auth', 'Code OTP vérifié, session établie');
      }),
      catchError((error: unknown) => {
        this.logService.warn('auth', 'Échec de vérification du code OTP');
        return throwError(() => error);
      })
    );
  }

  /** Re-authenticates the current session's username against the given password, to leave the lock screen. */
  unlock(password: string): Observable<LoginResponse> {
    const username = this.tokenStorage.getUsername();
    if (!username) {
      return throwError(() => new Error('No active session to unlock'));
    }
    return this.login({ username, password });
  }

  lock(): void {
    this.lockService.lock();
    this.logService.info('auth', 'Session verrouillée');
    void this.router.navigate(['/lock']);
  }

  resendLoginOtp(): Observable<unknown> {
    const challengeToken = this.challengeTokenSignal();
    return this.http.post('/auth/login/otp-resend', { challengeToken });
  }

  setReturnUrl(url: string): void {
    this.returnUrlSignal.set(url);
  }

  /** Fetches the full profile (bio, address, social links, 2FA state…) beyond what
   * the login response includes, and merges it into the current-user state. */
  getMeData(): Observable<AuthUser> {
    return this.http.get<AuthUser>('/auth/get-me-data').pipe(
      tap((user) => {
        this.currentUserSignal.set(user);
        this.tokenStorage.updateUser(user);
      })
    );
  }

  forgotPassword(username: string): Observable<unknown> {
    return this.http.post('/auth/forgot-password', { username });
  }

  /** Déverrouillage manuel (admin) d'un compte bloqué par le mécanisme de verrouillage
   * après échecs de connexion — voir docs/api-contracts/account-lockout.md §4.2. */
  unlockAccount(username: string): Observable<unknown> {
    return this.http.post(`/auth/accounts/${encodeURIComponent(username)}/unlock`, {});
  }

  resetPassword(payload: ResetPasswordRequest): Observable<unknown> {
    return this.http.post('/auth/reset-password', payload);
  }

  changePassword(payload: ChangePasswordRequest): Observable<unknown> {
    return this.http.patch('/auth/change-password', payload).pipe(
      tap(() => {
        const user = this.currentUserSignal();
        if (user) {
          const updated: AuthUser = { ...user, mustChangePassword: false };
          this.currentUserSignal.set(updated);
          this.tokenStorage.updateUser(updated);
        }
        this.logService.info('auth', 'Mot de passe modifié');
      })
    );
  }

  /** Le refresh token n'est plus lu depuis le storage local : il vit uniquement dans le cookie
   * httpOnly posé par le backend (invisible en JS par construction, impossible à vérifier ici).
   * On tente donc l'appel sans condition préalable — si aucun cookie valide n'est présent, le
   * backend répond 400/401 et le catchError de authInterceptor retombe sur sessionExpired(). */
  refreshAccessToken(): Observable<AuthTokens> {
    if (this.refreshInProgress$) {
      return this.refreshInProgress$;
    }

    this.refreshInProgress$ = this.http.post<AuthTokens>('/auth/refresh', {}, { withCredentials: true }).pipe(
      tap((tokens) => this.tokenStorage.setTokens(tokens)),
      finalize(() => {
        this.refreshInProgress$ = null;
      }),
      shareReplay(1)
    );

    return this.refreshInProgress$;
  }

  logout(): void {
    // Best-effort : révoque l'access token courant et efface le cookie refresh_token côté serveur
    // (audit de securite pre-production, 2026-08-25 — jusqu'ici jamais appelé, donc les jetons
    // restaient valides côté backend même après une "déconnexion"). Ne bloque pas la navigation :
    // l'état local est toujours effacé immédiatement, comme avant.
    this.http.post('/auth/logout', {}, { withCredentials: true }).subscribe({ error: () => {} });
    this.clearSessionState();
    this.logService.info('auth', 'Déconnexion');
    void this.router.navigate(['/login']);
  }

  /** Clears local session state only — no navigation side effect, unlike logout()/
   * sessionExpired(). For a guard that already returns its own redirect UrlTree (e.g.
   * representantAccessGuard): calling logout() there would race two navigations against
   * each other (its own router.navigate() vs. the guard's returned UrlTree). */
  clearSessionSilently(): void {
    this.clearSessionState();
    this.logService.info('auth', 'Session effacée (accès refusé à cette application)');
  }

  /** Called by the auth interceptor when the backend rejects an authenticated
   * request as if the session no longer exists (see authInterceptor for the
   * exact triggers). Distinct from logout(): this is not a user action, so we
   * land on an explanatory page instead of silently bouncing to /login. */
  sessionExpired(): void {
    if (this.sessionExpiredHandled) {
      return;
    }
    this.sessionExpiredHandled = true;
    this.clearSessionState();
    this.logService.warn('auth', 'Session expirée — redirection vers la reconnexion');
    void this.router.navigate(['/404'], { queryParams: { reason: 'session-expired' } });
  }

  private establishSession(response: LoginSuccessResponse): void {
    this.tokenStorage.setSession(response);
    this.currentUserSignal.set(response.user);
    this.challengeTokenSignal.set(null);
    this.maskedPhoneSignal.set(null);
    this.otpExpiresInSecondsSignal.set(null);
    this.sessionExpiredHandled = false;
  }

  private clearSessionState(): void {
    this.tokenStorage.clear();
    this.lockService.unlock();
    this.currentUserSignal.set(null);
    this.challengeTokenSignal.set(null);
    this.maskedPhoneSignal.set(null);
    this.otpExpiresInSecondsSignal.set(null);
    this.returnUrlSignal.set('/dashboard');
  }
}
