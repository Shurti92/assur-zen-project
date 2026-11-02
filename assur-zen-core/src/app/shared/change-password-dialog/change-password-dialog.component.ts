import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, FormDirective, FormLabelDirective, SpinnerComponent } from '@coreui/angular';

import { AuthService } from '../../core/auth/auth.service';
import { AlertMessageComponent } from '../alert-message/alert-message.component';
import { ModalComponent } from '../modal/modal.component';
import { PasswordFieldComponent } from '../password-field/password-field.component';
import {
  MIN_PASSWORD_LENGTH,
  checkPasswordRequirements,
  passwordsMatchValidator,
  strongPasswordValidator
} from '../validators/password.validators';

@Component({
  selector: 'app-change-password-dialog',
  templateUrl: './change-password-dialog.component.html',
  imports: [
    ModalComponent,
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
export class ChangePasswordDialogComponent {
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly visible = input(false);
  readonly closed = output<void>();
  readonly changed = output<void>();

  readonly minPasswordLength = MIN_PASSWORD_LENGTH;

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
          this.form.reset();
          this.changed.emit();
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(
            err instanceof HttpErrorResponse && err.error?.code === 'INVALID_CURRENT_PASSWORD'
              ? 'Mot de passe actuel incorrect.'
              : 'Impossible de mettre à jour le mot de passe. Réessayez.'
          );
        },
      });
  }

  onClose(): void {
    this.form.reset();
    this.errorMessage.set(null);
    this.closed.emit();
  }
}
