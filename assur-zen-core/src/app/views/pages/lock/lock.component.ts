import { Component, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import {
  AvatarComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  FormDirective,
  FormLabelDirective,
  SpinnerComponent
} from '@coreui/angular';

import { AuthService } from '../../../core/auth/auth.service';
import { LockService } from '../../../core/auth/lock.service';
import { isOtpChallenge } from '../../../core/auth/models/auth.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { PasswordFieldComponent } from '../../../shared/password-field/password-field.component';

@Component({
  selector: 'app-lock',
  templateUrl: './lock.component.html',
  imports: [
    CardComponent,
    CardBodyComponent,
    FormDirective,
    FormLabelDirective,
    ButtonDirective,
    AvatarComponent,
    IconDirective,
    ReactiveFormsModule,
    SpinnerComponent,
    PasswordFieldComponent,
    AlertMessageComponent,
  ],
})
export class LockComponent {
  readonly form = this.fb.group({
    password: ['', Validators.required],
  });

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly user = this.authService.currentUser;

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly lockService: LockService,
    private readonly router: Router
  ) {}

  unlock(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { password } = this.form.getRawValue();

    this.authService.unlock(password as string).subscribe({
      next: (response) => {
        this.loading.set(false);
        if (isOtpChallenge(response)) {
          this.errorMessage.set('Impossible de déverrouiller la session. Veuillez vous reconnecter.');
          this.authService.logout();
          return;
        }
        this.lockService.unlock();
        void this.router.navigateByUrl(this.authService.returnUrl());
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Mot de passe incorrect.');
      },
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
