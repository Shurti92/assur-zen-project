import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import { FormControlDirective, FormDirective, FormLabelDirective, SpinnerComponent } from '@coreui/angular';

import { AuthService } from '../../../core/auth/auth.service';
import { LoginFailureResponse, isOtpChallenge } from '../../../core/auth/models/auth.model';
import { AlertMessageVariant } from '../../../shared/alert-message/alert-message.component';
import { TokenStorageService } from '../../../core/auth/token-storage.service';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { AuthBrandPanelComponent } from '../../../shared/auth-brand-panel/auth-brand-panel.component';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  imports: [
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    IconDirective,
    ReactiveFormsModule,
    RouterLink,
    SpinnerComponent,
    AlertMessageComponent,
    AuthBrandPanelComponent,
  ],
})
export class LoginComponent {
  readonly form = this.fb.group({
    // maxLength matches the backend's actual cap on usernames (UserProfileRequest/
    // CommercialRequest.username, both maxLength 100) — a tighter 10 here used to lock out
    // any account whose username isn't a bare 10-digit phone number (e.g. commercial
    // accounts, which use a free-form identifier), since the login form rejected it before
    // ever calling the API.
    username: ['', [Validators.required, Validators.maxLength(100)]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    rememberDevice: [true],
  });

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly errorVariant = signal<AlertMessageVariant>('danger');
  readonly passwordVisible = signal(false);

  /** Compte à rebours du verrouillage — alimenté par `remainingSeconds` (423), confirmé
   * fiable en direct le 2026-08-10 (voir docs/api-contracts/account-lockout.md §4). */
  readonly lockedRemainingSeconds = signal<number | null>(null);
  readonly isLocked = computed(() => (this.lockedRemainingSeconds() ?? 0) > 0);
  readonly lockedCountdownLabel = computed(() => {
    const total = this.lockedRemainingSeconds();
    if (total === null || total <= 0) return null;
    const minutes = Math.floor(total / 60).toString().padStart(2, '0');
    const seconds = (total % 60).toString().padStart(2, '0');
    return `${minutes}:${seconds}`;
  });

  private readonly destroyRef = inject(DestroyRef);
  private lockCountdownIntervalId?: ReturnType<typeof setInterval>;

  togglePasswordVisibility(): void {
    this.passwordVisible.update((v) => !v);
  }

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly tokenStorage: TokenStorageService
  ) {
    // Reduces friction when the user is bounced back here after an OTP challenge
    // expired (see OtpComponent) — they only have to retype the password.
    const rememberedUsername = this.tokenStorage.getUsername();
    if (rememberedUsername) {
      this.form.controls.username.setValue(rememberedUsername);
    }
    this.destroyRef.onDestroy(() => this.clearLockCountdown());
  }

  login(): void {
    if (this.form.invalid || this.isLocked()) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.clearLockCountdown();

    const { username, password } = this.form.getRawValue();
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';
    this.authService.setReturnUrl(returnUrl);

    this.authService.login({ username, password } as { username: string; password: string }).subscribe({
      next: (response) => {
        this.loading.set(false);
        if (isOtpChallenge(response)) {
          void this.router.navigate(['/otp']);
        } else if (response.user.mustChangePassword) {
          void this.router.navigate(['/change-password']);
        } else {
          void this.router.navigateByUrl(returnUrl);
        }
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.handleLoginError(err);
      },
    });
  }

  /** POST /auth/login échoue soit par 401 (identifiants invalides, avec tentatives restantes s'il
   * s'agit d'un compte réel), soit par 423 (compte temporairement verrouillé — voir
   * docs/api-contracts/account-lockout.md), soit par 429 (limitation de débit côté gateway).
   * Tout autre statut retombe sur le message générique d'origine. */
  private handleLoginError(err: unknown): void {
    if (!(err instanceof HttpErrorResponse)) {
      this.errorVariant.set('danger');
      this.errorMessage.set('Identifiant ou mot de passe incorrect.');
      return;
    }

    const body = err.error as LoginFailureResponse | null;

    if (err.status === 423) {
      this.errorVariant.set('warning');
      this.errorMessage.set(
        body?.message ?? 'Compte temporairement bloqué suite à trop de tentatives de connexion échouées.'
      );
      if (typeof body?.remainingSeconds === 'number' && body.remainingSeconds > 0) {
        this.startLockCountdown(body.remainingSeconds);
      }
      return;
    }

    if (err.status === 401) {
      this.errorVariant.set('danger');
      const base = body?.message ?? 'Identifiant ou mot de passe incorrect.';
      const remaining = body?.remainingAttempts;
      this.errorMessage.set(
        typeof remaining === 'number'
          ? `${base} (il reste ${remaining} tentative${remaining > 1 ? 's' : ''} avant blocage du compte)`
          : base
      );
      return;
    }

    if (err.status === 429) {
      this.errorVariant.set('warning');
      this.errorMessage.set(body?.message ?? 'Trop de tentatives de connexion. Veuillez réessayer dans quelques instants.');
      return;
    }

    this.errorVariant.set('danger');
    this.errorMessage.set('Identifiant ou mot de passe incorrect.');
  }

  private startLockCountdown(seconds: number): void {
    this.lockedRemainingSeconds.set(seconds);
    this.clearLockCountdown();
    this.lockCountdownIntervalId = setInterval(() => {
      const remaining = (this.lockedRemainingSeconds() ?? 0) - 1;
      if (remaining <= 0) {
        this.lockedRemainingSeconds.set(null);
        this.errorMessage.set(null);
        this.clearLockCountdown();
      } else {
        this.lockedRemainingSeconds.set(remaining);
      }
    }, 1000);
  }

  private clearLockCountdown(): void {
    if (this.lockCountdownIntervalId !== undefined) {
      clearInterval(this.lockCountdownIntervalId);
      this.lockCountdownIntervalId = undefined;
    }
  }
}
