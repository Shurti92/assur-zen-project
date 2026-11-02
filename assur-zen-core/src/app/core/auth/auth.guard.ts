import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AppAccessService } from './app-access.service';
import { AuthService } from './auth.service';
import { LockService } from './lock.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const lockService = inject(LockService);
  const router = inject(Router);

  if (!authService.currentUser()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  if (lockService.isLocked()) {
    authService.setReturnUrl(state.url);
    return router.createUrlTree(['/lock']);
  }

  if (authService.mustChangePassword()) {
    authService.setReturnUrl(state.url);
    return router.createUrlTree(['/change-password']);
  }

  return true;
};

/** Bloque l'accès à ce back-office pour un compte de Type Profil REPRESENTANT — cette
 * application est réservée à l'app mobile "Force de Vente". Placé sur la même route parente
 * qu'authGuard (une seule résolution par session, pas sur chaque route enfant). Attend
 * explicitement la résolution (pas de "accès par défaut pendant le chargement") : c'est un
 * vrai contrôle d'accès, contrairement au masquage de menu de NavPermissionsService. */
export const representantAccessGuard: CanActivateFn = () => {
  const appAccessService = inject(AppAccessService);
  const authService = inject(AuthService);
  const router = inject(Router);

  return appAccessService.checkAccess().pipe(
    map((allowed) => {
      if (allowed) return true;
      authService.clearSessionSilently();
      return router.createUrlTree(['/404'], { queryParams: { reason: 'representant-denied' } });
    }),
  );
};

/** Réserve une route au rôle ADMIN (ex. /sycapay, règle métier du 2026-09-23). À placer sur la
 * route enfant, sous authGuard : currentUser() est donc toujours résolu ici. Le masquage du menu
 * (_nav.ts ADMIN_ONLY_NAV_NAMES) ne suffit pas contre une URL saisie à la main ; le 403 backend
 * reste le vrai rempart. */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.currentUser()?.role === 'ADMIN') return true;
  return router.createUrlTree(['/404'], { queryParams: { reason: 'admin-only' } });
};

export const lockGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const lockService = inject(LockService);
  const router = inject(Router);

  if (!authService.currentUser()) {
    return router.createUrlTree(['/login']);
  }

  if (!lockService.isLocked()) {
    return router.createUrlTree([authService.returnUrl()]);
  }

  return true;
};

export const otpGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.currentUser()) {
    return router.createUrlTree(['/dashboard']);
  }

  if (!authService.hasOtpChallenge()) {
    // No pending OTP challenge in this page session (direct link or reload): start over.
    return router.createUrlTree(['/login']);
  }

  return true;
};

// Reachable both when a password change is forced (mustChangePassword) and when a
// signed-in user opts to change their password voluntarily (e.g. from their profile).
export const changePasswordGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.currentUser()) {
    return router.createUrlTree(['/login']);
  }

  return true;
};

// Catches any URL that doesn't match a known route — this sits outside the authenticated
// layout (sibling of it in app.routes.ts), so it's reached both by a genuinely fabricated
// address bar entry AND by a signed-in user clicking a broken in-app link (a typo'd
// routerLink, a stale sidebar entry pointing at a route that was since moved). Those two
// cases can't be told apart here, and only the second one is actually common — wiping
// local/session storage used to log the signed-in user out over what was really just a
// broken link, with no security benefit for the fabricated-URL case (an anonymous visitor
// has no session worth protecting either way). See page500.component.ts for the message
// shown, which now checks auth state to tell "reconnectez-vous" from "page introuvable".
export const invalidUrlGuard: CanActivateFn = () => {
  const router = inject(Router);

  return router.createUrlTree(['/500'], { queryParams: { reason: 'invalid-url' } });
};
