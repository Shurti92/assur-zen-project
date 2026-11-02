import { Component, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonDirective, FormControlDirective, FormDirective, FormLabelDirective } from '@coreui/angular';

import { AuthService } from '../../core/auth/auth.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';

/**
 * Déverrouillage manuel (admin) d'un compte bloqué par le mécanisme de verrouillage après
 * échecs de connexion — voir docs/api-contracts/account-lockout.md §4.2.
 * L'API ne fournit pas de liste des comptes verrouillés (`GET /auth/accounts/locked` n'existe
 * pas) : l'utilisateur doit connaître le nom d'utilisateur à déverrouiller (ex. repéré dans le
 * journal Audit → Interceptors → Audit sur un `POST /auth/login` en `423`).
 */
@Component({
  selector: 'app-deverrouillage-compte',
  templateUrl: './deverrouillage-compte.component.html',
  imports: [
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    ButtonDirective,
    AlertMessageComponent,
    ConfirmDialogComponent,
  ],
})
export class DeverrouillageCompteComponent {
  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly confirmVisible = signal(false);

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService
  ) {}

  askUnlock(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.confirmVisible.set(true);
  }

  cancelUnlock(): void {
    this.confirmVisible.set(false);
  }

  confirmUnlock(): void {
    this.confirmVisible.set(false);
    const username = this.form.getRawValue().username.trim();
    this.submitting.set(true);
    this.authService.unlockAccount(username).subscribe({
      next: () => {
        this.submitting.set(false);
        this.successMessage.set(`Le compte « ${username} » a été déverrouillé.`);
        this.form.reset({ username: '' });
      },
      // 403 : le compte de l'utilisateur connecté n'a pas le rôle ADMIN requis par l'API
      // (voir docs/api-contracts/account-lockout.md §5) — distinct d'un simple identifiant erroné.
      error: (err: unknown) => {
        this.submitting.set(false);
        if (err instanceof HttpErrorResponse && err.status === 403) {
          this.errorMessage.set("Vous n'avez pas les droits nécessaires pour déverrouiller un compte (rôle administrateur requis).");
        } else {
          this.errorMessage.set(`Impossible de déverrouiller le compte « ${username} ». Vérifiez le nom d'utilisateur.`);
        }
      },
    });
  }
}
