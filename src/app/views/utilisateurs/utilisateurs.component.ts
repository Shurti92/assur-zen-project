import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  DropdownComponent,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { UserService } from '../../core/user/user.service';
import { UserProfileResponse, UserSessionResponse } from '../../core/user/models/user.model';
import { generateTemporaryPassword } from '../../core/user/temporary-password.util';
import { TypeProfileService } from '../../core/configuration/type-profile.service';
import { TypeProfileResponse } from '../../core/configuration/models/type-profile.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../shared/modal/modal.component';

type ActiveFilter = 'all' | 'active' | 'inactive';

/**
 * Comptes utilisateur back-office ("classiques") — voir
 * docs/api-contracts/creation-comptes-utilisateurs.md §4bis. Distinct des comptes partenaires
 * (créés automatiquement depuis views/partenaire-affaire, jamais via cet écran).
 */
@Component({
  selector: 'app-utilisateurs',
  templateUrl: './utilisateurs.component.html',
  styleUrl: 'utilisateurs.component.scss',
  imports: [
    ButtonDirective,
    DropdownComponent,
    DropdownItemDirective,
    DropdownMenuDirective,
    DropdownToggleDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class UtilisateursComponent {
  private readonly userService = inject(UserService);
  private readonly typeProfileService = inject(TypeProfileService);
  private readonly fb = inject(FormBuilder);

  readonly pageSize = 20;

  readonly items = signal<UserProfileResponse[]>([]);
  readonly roles = signal<string[]>([]);
  readonly typeProfiles = signal<TypeProfileResponse[]>([]);
  readonly pageIndex = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(1);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly activeFilter = signal<ActiveFilter>('all');
  readonly exporting = signal(false);

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  /** Affiché juste après une création réussie — le mot de passe généré n'est jamais
   * récupérable ensuite, donc on le garde visible jusqu'à ce que l'admin ferme explicitement. */
  readonly createdAccount = signal<UserProfileResponse | null>(null);

  readonly statusTarget = signal<UserProfileResponse | null>(null);
  readonly togglingStatus = signal(false);

  readonly resetTarget = signal<UserProfileResponse | null>(null);
  readonly resetting = signal(false);
  readonly resetResult = signal<{ username: string; temporaryPassword: string } | null>(null);

  readonly deleteTarget = signal<UserProfileResponse | null>(null);
  readonly deleting = signal(false);

  readonly sessionsPageSize = 10;
  readonly sessionsVisible = signal(false);
  readonly sessionsTarget = signal<UserProfileResponse | null>(null);
  readonly sessions = signal<UserSessionResponse[]>([]);
  readonly sessionsLoading = signal(true);
  readonly sessionsError = signal<string | null>(null);
  readonly sessionsPageIndex = signal(0);
  readonly sessionsTotalElements = signal(0);
  readonly sessionsTotalPages = signal(1);
  readonly sessionsActiveCount = computed(() => this.sessions().filter((s) => !s.revoked).length);
  readonly sessionsMostRecentId = computed(() => this.sessions()[0]?.id ?? null);
  /** Ticks périodiquement pour que relativeTime() reste stable dans un même cycle de détection
   * de changements — même pattern que audit.component.ts. */
  private readonly sessionsNow = signal(Date.now());

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    firstname: ['', Validators.required],
    lastname: ['', Validators.required],
    phone: [''],
    email: ['', Validators.email],
    roles: ['', Validators.required],
    typeProfileId: [''],
  });

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    const nowIntervalId = setInterval(() => this.sessionsNow.set(Date.now()), 30_000);
    inject(DestroyRef).onDestroy(() => {
      if (this.searchDebounce) clearTimeout(this.searchDebounce);
      clearInterval(nowIntervalId);
    });
    this.fetch();
    this.fetchRoles();
    this.fetchTypeProfiles();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    const active = this.activeFilter() === 'all' ? undefined : this.activeFilter() === 'active';
    this.userService.list(this.pageIndex(), this.pageSize, { q: this.searchQuery() || undefined, active }).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(Math.max(1, result.totalPages));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les utilisateurs.');
        this.loading.set(false);
      },
    });
  }

  private fetchRoles(): void {
    this.userService.getRoles().subscribe({
      next: (roles) => this.roles.set(roles),
      error: () => {
        // Silent: le select de rôle reste vide si l'appel échoue.
      },
    });
  }

  private fetchTypeProfiles(): void {
    this.typeProfileService.list().subscribe({
      next: (typeProfiles) => this.typeProfiles.set(typeProfiles),
      error: () => {
        // Silent: le select de Type Profil reste vide si l'appel échoue.
      },
    });
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
    this.fetch();
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.pageIndex.set(0);
      this.fetch();
    }, 400);
  }

  setActiveFilter(filter: ActiveFilter): void {
    this.activeFilter.set(filter);
    this.pageIndex.set(0);
    this.fetch();
  }

  export(): void {
    this.exporting.set(true);
    this.error.set(null);
    const active = this.activeFilter() === 'all' ? undefined : this.activeFilter() === 'active';
    this.userService.export({ q: this.searchQuery() || undefined, active }).subscribe({
      next: (blob) => {
        this.downloadBlob(blob, 'utilisateurs.csv');
        this.exporting.set(false);
      },
      error: () => {
        this.error.set("Impossible d'exporter les utilisateurs.");
        this.exporting.set(false);
      },
    });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({ username: '', firstname: '', lastname: '', phone: '', email: '', roles: '', typeProfileId: '' });
    this.formVisible.set(true);
  }

  openEdit(item: UserProfileResponse): void {
    this.editingId.set(item.userId);
    this.formError.set(null);
    this.form.reset({
      username: item.username,
      firstname: item.firstname,
      lastname: item.lastname,
      phone: item.phone ?? '',
      email: item.email ?? '',
      roles: item.roles,
      // Le backend n'expose pas encore le typeProfileId d'un compte existant en lecture
      // (voir docs/api-contracts/user-type-profile-id.md) — impossible de le pré-remplir ici.
      typeProfileId: '',
    });
    this.formVisible.set(true);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const payload = {
      username: raw.username.trim(),
      firstname: raw.firstname.trim(),
      lastname: raw.lastname.trim(),
      phone: raw.phone.trim() || undefined,
      email: raw.email.trim() || undefined,
      roles: raw.roles,
      typeProfileId: raw.typeProfileId ? Number(raw.typeProfileId) : undefined,
    };
    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request = id ? this.userService.update(id, payload) : this.userService.create(payload);
    request.subscribe({
      next: (response) => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
        if (!id) {
          // Nouvelle création : le mot de passe généré n'est visible qu'ici, une seule fois.
          this.createdAccount.set(response);
        }
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer l'utilisateur. Vérifiez que l'identifiant n'est pas déjà utilisé.");
      },
    });
  }

  askToggleStatus(item: UserProfileResponse): void {
    this.statusTarget.set(item);
  }

  cancelToggleStatus(): void {
    this.statusTarget.set(null);
  }

  confirmToggleStatus(): void {
    const item = this.statusTarget();
    if (!item) return;
    this.togglingStatus.set(true);
    this.userService.setStatus(item.userId, !item.userActive).subscribe({
      next: () => {
        this.togglingStatus.set(false);
        this.statusTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.togglingStatus.set(false);
        this.statusTarget.set(null);
        this.error.set('Impossible de changer le statut de ce compte.');
      },
    });
  }

  askResetPassword(item: UserProfileResponse): void {
    this.resetTarget.set(item);
  }

  cancelResetPassword(): void {
    this.resetTarget.set(null);
  }

  confirmResetPassword(): void {
    const item = this.resetTarget();
    if (!item) return;
    this.resetting.set(true);
    const newPassword = generateTemporaryPassword();
    this.userService.resetPassword(item.userId, newPassword).subscribe({
      next: () => {
        this.resetting.set(false);
        this.resetTarget.set(null);
        this.resetResult.set({ username: item.username, temporaryPassword: newPassword });
      },
      error: () => {
        this.resetting.set(false);
        this.resetTarget.set(null);
        this.error.set('Impossible de réinitialiser le mot de passe de ce compte.');
      },
    });
  }

  confirmDelete(item: UserProfileResponse): void {
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleting.set(true);
    this.userService.delete(item.userId).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.error.set('Impossible de supprimer ce compte.');
      },
    });
  }

  openSessions(item: UserProfileResponse): void {
    this.sessionsTarget.set(item);
    this.sessionsPageIndex.set(0);
    this.sessionsVisible.set(true);
    this.fetchSessions();
  }

  closeSessions(): void {
    this.sessionsVisible.set(false);
  }

  private fetchSessions(): void {
    const target = this.sessionsTarget();
    if (!target) return;
    this.sessionsLoading.set(true);
    this.sessionsError.set(null);
    this.userService.getLoginHistory(target.userId, this.sessionsPageIndex(), this.sessionsPageSize).subscribe({
      next: (result) => {
        this.sessions.set(result.content);
        this.sessionsTotalElements.set(result.totalElements);
        this.sessionsTotalPages.set(Math.max(1, result.totalPages));
        this.sessionsLoading.set(false);
      },
      error: () => {
        this.sessionsError.set("Impossible de charger l'historique de connexion.");
        this.sessionsLoading.set(false);
      },
    });
  }

  goToSessionsPage(index: number): void {
    if (index < 0 || index >= this.sessionsTotalPages()) return;
    this.sessionsPageIndex.set(index);
    this.fetchSessions();
  }

  formatDateTime(value: string): string {
    return new Date(value).toLocaleString('fr-FR');
  }

  relativeTime(value: string): string {
    const diffMs = this.sessionsNow() - new Date(value).getTime();
    const minutes = Math.round(diffMs / 60000);
    if (minutes <= 0) return "à l'instant";
    if (minutes < 60) return `il y a ${minutes} min`;
    const hours = Math.round(minutes / 60);
    if (hours < 24) return `il y a ${hours} h`;
    const days = Math.round(hours / 24);
    if (days < 30) return `il y a ${days} j`;
    return `il y a ${Math.round(days / 30)} mois`;
  }
}
