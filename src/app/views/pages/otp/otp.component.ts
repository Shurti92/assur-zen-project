import { AfterViewInit, DestroyRef, Component, ElementRef, QueryList, ViewChildren, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import { FormControlDirective, FormDirective, FormLabelDirective, SpinnerComponent } from '@coreui/angular';

import { AuthService } from '../../../core/auth/auth.service';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { AuthBrandPanelComponent } from '../../../shared/auth-brand-panel/auth-brand-panel.component';

const CODE_LENGTH = 6;
const RESEND_COOLDOWN_SECONDS = 30;
/** The backend rate-limits /auth/login/otp-resend (HTTP 429) independently of the
 * OTP's own expiry — this is the cooldown applied client-side when that happens,
 * since the response carries no Retry-After value to size it precisely. */
const RATE_LIMIT_COOLDOWN_SECONDS = 60;
const DEFAULT_OTP_TTL_SECONDS = 5 * 60;
const URGENT_THRESHOLD_SECONDS = 30;

@Component({
  selector: 'app-otp',
  templateUrl: './otp.component.html',
  styleUrl: './otp.component.scss',
  imports: [
    FormDirective,
    FormControlDirective,
    FormLabelDirective,
    IconDirective,
    ReactiveFormsModule,
    SpinnerComponent,
    AlertMessageComponent,
    AuthBrandPanelComponent,
  ],
})
export class OtpComponent implements AfterViewInit {
  @ViewChildren('digitInput') private readonly digitInputs!: QueryList<ElementRef<HTMLInputElement>>;

  readonly form = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  readonly digits = signal<string[]>(Array(CODE_LENGTH).fill(''));
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly resending = signal(false);
  /** True once a resend attempt has failed — offers a "Réessayer" action instead of
   * immediately funneling the user to a full reconnect, since a resend can fail for
   * transient reasons (network blip, backend hiccup) and not just genuine challenge
   * expiry. Cleared as soon as a resend succeeds or a fresh attempt is in flight. */
  readonly resendFailed = signal(false);
  readonly resendCooldown = signal(0);
  readonly remainingSeconds = signal(this.authService.otpExpiresInSeconds() ?? DEFAULT_OTP_TTL_SECONDS);
  readonly totalSeconds = signal(this.remainingSeconds());

  readonly maskedPhone = this.authService.maskedPhone;
  readonly codeExpired = computed(() => this.remainingSeconds() <= 0);
  readonly isUrgent = computed(() => !this.codeExpired() && this.remainingSeconds() <= URGENT_THRESHOLD_SECONDS);
  readonly formattedRemaining = computed(() => {
    const total = Math.max(this.remainingSeconds(), 0);
    const minutes = Math.floor(total / 60).toString().padStart(2, '0');
    const seconds = (total % 60).toString().padStart(2, '0');
    return `${minutes}:${seconds}`;
  });
  readonly timerProgressPercent = computed(() => {
    const total = this.totalSeconds();
    if (total <= 0) return 0;
    return Math.max(0, Math.min(100, (this.remainingSeconds() / total) * 100));
  });

  private readonly destroyRef = inject(DestroyRef);
  private cooldownIntervalId?: ReturnType<typeof setInterval>;
  private countdownIntervalId?: ReturnType<typeof setInterval>;

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {
    this.startCountdown(this.remainingSeconds());
    this.destroyRef.onDestroy(() => {
      this.clearCooldownInterval();
      this.clearCountdownInterval();
    });
  }

  ngAfterViewInit(): void {
    this.focusDigit(0);
  }

  onDigitInput(index: number, rawValue: string): void {
    const char = rawValue.replace(/\D/g, '').slice(-1);
    const next = [...this.digits()];
    next[index] = char;
    this.digits.set(next);
    this.form.controls.code.setValue(next.join(''));

    if (char && index < CODE_LENGTH - 1) {
      this.focusDigit(index + 1);
    }
  }

  onDigitKeydown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace' && !this.digits()[index] && index > 0) {
      this.focusDigit(index - 1);
    }
  }

  onPaste(event: ClipboardEvent): void {
    const pasted = event.clipboardData?.getData('text').replace(/\D/g, '').slice(0, CODE_LENGTH) ?? '';
    if (!pasted) {
      return;
    }
    event.preventDefault();

    const next = Array(CODE_LENGTH).fill('');
    pasted.split('').forEach((char, i) => (next[i] = char));
    this.digits.set(next);
    this.form.controls.code.setValue(pasted.padEnd(CODE_LENGTH, ''));
    this.focusDigit(Math.min(pasted.length, CODE_LENGTH - 1));
  }

  verify(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { code } = this.form.getRawValue();
    this.authService.verifyLoginOtp(code as string).subscribe({
      next: (response) => {
        this.loading.set(false);
        if (response.user.mustChangePassword) {
          void this.router.navigate(['/change-password']);
          return;
        }
        void this.router.navigateByUrl(this.authService.returnUrl());
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Code invalide ou expiré.');
      },
    });
  }

  resend(): void {
    if (this.resendCooldown() > 0 || this.resending()) {
      return;
    }

    this.resending.set(true);
    this.authService.resendLoginOtp().subscribe({
      next: () => {
        this.resending.set(false);
        this.resendFailed.set(false);
        this.startCooldown();
        this.startCountdown(DEFAULT_OTP_TTL_SECONDS);
        this.digits.set(Array(CODE_LENGTH).fill(''));
        this.form.controls.code.setValue('');
        this.errorMessage.set(null);
        this.focusDigit(0);
      },
      // A resend can fail for reasons other than the challenge being truly dead
      // (network blip, backend hiccup, rate-limit) — offer "Réessayer" first via
      // resendFailed, rather than immediately funneling the user to a full reconnect.
      error: (err: unknown) => {
        this.resending.set(false);
        this.resendFailed.set(true);
        if (err instanceof HttpErrorResponse && err.status === 429) {
          // Confirmed via live testing: the backend rate-limits this endpoint
          // independently of the OTP/challenge's own validity — retrying
          // immediately would just 429 again, so cooldown before allowing it.
          this.startCooldown(RATE_LIMIT_COOLDOWN_SECONDS);
          this.errorMessage.set(
            `Trop de tentatives de renvoi. Réessayez dans ${RATE_LIMIT_COOLDOWN_SECONDS} secondes, ou reconnectez-vous.`
          );
        } else {
          this.errorMessage.set("Le renvoi du code a échoué. Réessayez, ou reconnectez-vous si le problème persiste.");
        }
      },
    });
  }

  backToLogin(): void {
    void this.router.navigate(['/login']);
  }

  private startCooldown(seconds: number = RESEND_COOLDOWN_SECONDS): void {
    this.resendCooldown.set(seconds);
    this.clearCooldownInterval();
    this.cooldownIntervalId = setInterval(() => {
      const remaining = this.resendCooldown() - 1;
      if (remaining <= 0) {
        this.resendCooldown.set(0);
        this.clearCooldownInterval();
      } else {
        this.resendCooldown.set(remaining);
      }
    }, 1000);
  }

  private clearCooldownInterval(): void {
    if (this.cooldownIntervalId !== undefined) {
      clearInterval(this.cooldownIntervalId);
      this.cooldownIntervalId = undefined;
    }
  }

  private startCountdown(seconds: number): void {
    this.remainingSeconds.set(seconds);
    this.totalSeconds.set(seconds);
    this.clearCountdownInterval();
    this.countdownIntervalId = setInterval(() => {
      const remaining = this.remainingSeconds() - 1;
      if (remaining <= 0) {
        this.remainingSeconds.set(0);
        this.clearCountdownInterval();
      } else {
        this.remainingSeconds.set(remaining);
      }
    }, 1000);
  }

  private clearCountdownInterval(): void {
    if (this.countdownIntervalId !== undefined) {
      clearInterval(this.countdownIntervalId);
      this.countdownIntervalId = undefined;
    }
  }

  private focusDigit(index: number): void {
    const input = this.digitInputs.get(index)?.nativeElement;
    input?.focus();
    input?.select();
  }
}
