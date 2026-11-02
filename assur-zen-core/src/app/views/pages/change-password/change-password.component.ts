import { Component, computed, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  FormDirective,
  FormLabelDirective,
  SpinnerComponent
} from '@coreui/angular';

import { AuthService } from '../../../core/auth/auth.service';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { PasswordFieldComponent } from '../../../shared/password-field/password-field.component';
import {
  MIN_PASSWORD_LENGTH,
  checkPasswordRequirements,
  passwordsMatchValidator,
  strongPasswordValidator
} from '../../../shared/validators/password.validators';

@Component({
  selector: 'app-change-password',
  templateUrl: './change-password.component.html',
  imports: [
    CardComponent,
    CardBodyComponent,
    FormDirective,
    FormLabelDirective,
    ButtonDirective,
    ReactiveFormsModule,
    SpinnerComponent,
    PasswordFieldComponent,
    AlertMessageComponent,
    IconDirective,
  ],
})
export class ChangePasswordComponent {
  readonly minPasswordLength = MIN_PASSWORD_LENGTH;
  readonly isForced = this.authService.mustChangePassword;

  readonly form = this.fb.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH), strongPasswordValidator]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatchValidator }
  );

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private readonly newPasswordValue = toSignal(this.form.controls.newPassword.valueChanges, {
    initialValue: '',
  });

  readonly passwordRequirements = computed(() => checkPasswordRequirements(this.newPasswordValue() ?? ''));

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { currentPassword, newPassword } = this.form.getRawValue();
    this.authService
      .changePassword({ currentPassword, newPassword } as { currentPassword: string; newPassword: string })
      .subscribe({
        next: () => {
          this.loading.set(false);
          void this.router.navigateByUrl(this.authService.returnUrl());
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('Impossible de mettre à jour le mot de passe. Réessayez.');
        },
      });
  }
}
