import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import { FormControlDirective, FormDirective, FormLabelDirective, SpinnerComponent } from '@coreui/angular';

import { AuthService } from '../../../core/auth/auth.service';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { AuthBrandPanelComponent } from '../../../shared/auth-brand-panel/auth-brand-panel.component';

const MIN_PASSWORD_LENGTH = 8;
const RESEND_COOLDOWN_SECONDS = 30;

interface PasswordRequirements {
  minLength: boolean;
  hasUpperCase: boolean;
  hasLowerCase: boolean;
  hasNumber: boolean;
  hasSpecialChar: boolean;
}

function checkPasswordRequirements(password: string): PasswordRequirements {
  return {
    minLength: password.length >= MIN_PASSWORD_LENGTH,
    hasUpperCase: /[A-Z]/.test(password),
    hasLowerCase: /[a-z]/.test(password),
    hasNumber: /[0-9]/.test(password),
    hasSpecialChar: /[^A-Za-z0-9]/.test(password),
  };
}

function strongPasswordValidator(control: AbstractControl): ValidationErrors | null {
  const requirements = checkPasswordRequirements(control.value ?? '');
  return Object.values(requirements).every(Boolean) ? null : { weakPassword: true };
}

function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const newPassword = control.get('newPassword')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  return newPassword === confirmPassword ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-forgot-password',
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss',
  imports: [
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    ReactiveFormsModule,
    RouterLink,
    SpinnerComponent,
    AlertMessageComponent,
    IconDirective,
    AuthBrandPanelComponent,
  ],
})
export class ForgotPasswordComponent {
  readonly minPasswordLength = MIN_PASSWORD_LENGTH;

  readonly step = signal<'request' | 'reset'>('request');
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly infoMessage = signal<string | null>(null);
  readonly resendCooldown = signal(0);
  readonly resending = signal(false);

  private readonly destroyRef = inject(DestroyRef);
  private cooldownIntervalId?: ReturnType<typeof setInterval>;

  readonly requestForm = this.fb.group({
    username: ['', [Validators.required, Validators.maxLength(10)]],
  });

  readonly resetForm = this.fb.group(
    {
      code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
      newPassword: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH), strongPasswordValidator]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatchValidator }
  );

  private readonly newPasswordValue = toSignal(this.resetForm.controls.newPassword.valueChanges, {
    initialValue: '',
  });

  readonly passwordRequirements = computed(() => checkPasswordRequirements(this.newPasswordValue() ?? ''));

  readonly newPasswordVisible = signal(false);
  readonly confirmPasswordVisible = signal(false);

  toggleNewPasswordVisibility(): void {
    this.newPasswordVisible.update((v) => !v);
  }

  toggleConfirmPasswordVisibility(): void {
    this.confirmPasswordVisible.update((v) => !v);
  }

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {
    this.destroyRef.onDestroy(() => this.clearCooldownInterval());
  }

  requestCode(): void {
    if (this.requestForm.invalid) {
      this.requestForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { username } = this.requestForm.getRawValue();
    this.authService.forgotPassword(username as string).subscribe({
      next: () => {
        this.loading.set(false);
        this.infoMessage.set('Un code de vérification vient de vous être envoyé.');
        this.step.set('reset');
        this.startCooldown();
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set("Impossible d'envoyer le code. Vérifiez le nom d'utilisateur saisi.");
      },
    });
  }

  resend(): void {
    if (this.resendCooldown() > 0 || this.resending()) {
      return;
    }

    const { username } = this.requestForm.getRawValue();
    if (!username) {
      this.errorMessage.set("Nom d'utilisateur introuvable. Recommencez depuis le début.");
      return;
    }

    this.resending.set(true);
    this.errorMessage.set(null);

    this.authService.forgotPassword(username).subscribe({
      next: () => {
        this.resending.set(false);
        this.infoMessage.set('Un nouveau code vient de vous être envoyé.');
        this.startCooldown();
      },
      error: () => {
        this.resending.set(false);
        this.errorMessage.set('Impossible de renvoyer le code. Réessayez dans quelques instants.');
      },
    });
  }

  resetPassword(): void {
    if (this.resetForm.invalid) {
      this.resetForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { username } = this.requestForm.getRawValue();
    const { code, newPassword, confirmPassword } = this.resetForm.getRawValue();
    this.authService
      .resetPassword({ username, code, newPassword, confirmPassword } as {
        username: string;
        code: string;
        newPassword: string;
        confirmPassword: string;
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          void this.router.navigate(['/login']);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('Code invalide ou expiré.');
        },
      });
  }

  private startCooldown(): void {
    this.resendCooldown.set(RESEND_COOLDOWN_SECONDS);
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
}
