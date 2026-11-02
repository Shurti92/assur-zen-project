import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { TokenStorageService } from '../auth/token-storage.service';

const AUTH_FREE_PATHS = ['/auth/login', '/auth/refresh', '/auth/forgot-password', '/auth/reset-password'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStorage = inject(TokenStorageService);
  const authService = inject(AuthService);

  const isAuthFree = AUTH_FREE_PATHS.some((path) => req.url.includes(path));
  const accessToken = tokenStorage.getAccessToken();

  const authReq =
    !isAuthFree && accessToken
      ? req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })
      : req;

  return next(authReq).pipe(
    catchError((error: unknown) => {
      if (isAuthFree || !(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }

      // The backend rejects aged/refreshed access tokens on some routes with a bare
      // 403 (no refresh hint) instead of 401 — in practice the only fix that has ever
      // worked is a fresh login, so treat it the same as an expired session.
      if (error.status === 403 && accessToken) {
        authService.sessionExpired();
        return throwError(() => error);
      }

      // Le refresh token vit dans un cookie httpOnly (invisible en JS) : impossible de savoir a
      // l'avance s'il est present, donc on tente systematiquement — authService.refreshAccessToken()
      // echoue proprement (catchError ci-dessous -> sessionExpired()) si le backend n'a aucun cookie
      // valide a offrir.
      if (error.status === 401) {
        return authService.refreshAccessToken().pipe(
          switchMap((tokens) =>
            next(req.clone({ setHeaders: { Authorization: `Bearer ${tokens.accessToken}` } }))
          ),
          catchError((refreshError: unknown) => {
            authService.sessionExpired();
            return throwError(() => refreshError);
          })
        );
      }

      return throwError(() => error);
    })
  );
};
