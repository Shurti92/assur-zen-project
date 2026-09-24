import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  AvatarComponent,
  ButtonDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  SpinnerComponent
} from '@coreui/angular';

import { AuthService } from '../../../core/auth/auth.service';
import { ProfileDetails, ProfileRecord } from '../../../core/profile/models/profile-details.model';
import { ProfileService } from '../../../core/profile/profile.service';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ChangePasswordDialogComponent } from '../../../shared/change-password-dialog/change-password-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type ProfileSection = 'info' | 'address' | 'security' | 'danger';

const COMPLETION_FIELDS = [
  'bio',
  'facebookUrl',
  'twitterUrl',
  'linkedinUrl',
  'instagramUrl',
  'country',
  'city',
  'state',
  'postalCode',
  'taxId',
  'avatarUrl',
] as const;

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
  imports: [
    AvatarComponent,
    ButtonDirective,
    IconDirective,
    FormCheckInputDirective,
    AlertMessageComponent,
    ModalComponent,
    ChangePasswordDialogComponent,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    SpinnerComponent
  ],
})
export class ProfileComponent {
  private readonly authService = inject(AuthService);
  private readonly profileService = inject(ProfileService);
  private readonly fb = inject(FormBuilder);

  readonly user = this.authService.currentUser;
  readonly loadError = signal(false);
  readonly activeSection = signal<ProfileSection>('info');

  /** Authoritative extra fields (firstname/lastname/phone/…) plus the profile's own id.
   * GET /profiles has no userId filter, so we list every profile once on load and pick
   * the one whose userId matches the signed-in user — then reuse it to safely merge
   * before every PUT, since the update endpoint expects the full object and fields we
   * never display must still round-trip unchanged (account status, ville partenaire…). */
  readonly profileRecord = signal<ProfileRecord | null>(null);
  readonly profileDetailsError = signal(false);

  readonly profileDetails = computed<ProfileDetails | null>(() => this.profileRecord());

  readonly changePasswordDialogVisible = signal(false);

  readonly editProfileDialogVisible = signal(false);
  readonly editProfileSaving = signal(false);
  readonly editProfileError = signal<string | null>(null);

  readonly editAddressDialogVisible = signal(false);
  readonly editAddressSaving = signal(false);
  readonly editAddressError = signal<string | null>(null);

  readonly editProfileForm = this.fb.nonNullable.group({
    firstname: [''],
    lastname: [''],
    phone: [''],
    bio: [''],
    facebookUrl: [''],
    twitterUrl: [''],
    linkedinUrl: [''],
    instagramUrl: [''],
  });

  readonly editAddressForm = this.fb.nonNullable.group({
    country: [''],
    city: [''],
    state: [''],
    postalCode: [''],
    taxId: [''],
  });

  readonly firstName = computed(() => this.profileDetails()?.firstname || this.splitName(this.user()?.fullName).first);
  readonly lastName = computed(() => this.profileDetails()?.lastname || this.splitName(this.user()?.fullName).last);
  readonly phone = computed(() => this.profileDetails()?.phone ?? '');

  /** "Arizona, United States" style summary used under the name. */
  readonly headerLocation = computed(() => this.joinLocation([this.user()?.state, this.user()?.country]));

  /** "Phoenix, Arizona, United States" style summary used in the Address panel. */
  readonly fullLocation = computed(() =>
    this.joinLocation([this.user()?.city, this.user()?.state, this.user()?.country])
  );

  readonly completion = computed(() => {
    const u = this.user();
    if (!u) {
      return 0;
    }
    const filled = COMPLETION_FIELDS.filter((field) => !!u[field]).length;
    return Math.round((filled / COMPLETION_FIELDS.length) * 100);
  });

  constructor() {
    this.authService.getMeData().subscribe({
      error: () => this.loadError.set(true),
    });
    this.loadProfileDetails();
  }

  openChangePassword(): void {
    this.changePasswordDialogVisible.set(true);
  }

  onPasswordChanged(): void {
    this.changePasswordDialogVisible.set(false);
  }

  openEditProfile(): void {
    this.editProfileError.set(null);
    this.editProfileDialogVisible.set(true);
    const details = this.profileDetails();
    if (details) {
      this.editProfileForm.setValue({
        firstname: details.firstname,
        lastname: details.lastname,
        phone: details.phone,
        bio: details.bio,
        facebookUrl: details.facebookUrl,
        twitterUrl: details.twitterUrl,
        linkedinUrl: details.linkedinUrl,
        instagramUrl: details.instagramUrl,
      });
    }
  }

  saveProfile(): void {
    const record = this.profileRecord();
    if (!record) {
      this.editProfileError.set("Impossible d'enregistrer : profil non chargé. Réessayez.");
      return;
    }

    this.editProfileSaving.set(true);
    this.editProfileError.set(null);

    const payload: ProfileDetails = { ...record, ...this.editProfileForm.getRawValue() };
    this.profileService.update(record.id, payload).subscribe({
      next: (updated) => {
        this.profileRecord.set(updated);
        this.editProfileSaving.set(false);
        this.editProfileDialogVisible.set(false);
        this.authService.getMeData().subscribe();
      },
      error: () => {
        this.editProfileSaving.set(false);
        this.editProfileError.set("Impossible d'enregistrer les modifications. Réessayez.");
      },
    });
  }

  openEditAddress(): void {
    this.editAddressError.set(null);
    this.editAddressDialogVisible.set(true);
    const details = this.profileDetails();
    if (details) {
      this.editAddressForm.setValue({
        country: details.country,
        city: details.city,
        state: details.state,
        postalCode: details.postalCode,
        taxId: details.taxId,
      });
    }
  }

  saveAddress(): void {
    const record = this.profileRecord();
    if (!record) {
      this.editAddressError.set("Impossible d'enregistrer : profil non chargé. Réessayez.");
      return;
    }

    this.editAddressSaving.set(true);
    this.editAddressError.set(null);

    const payload: ProfileDetails = { ...record, ...this.editAddressForm.getRawValue() };
    this.profileService.update(record.id, payload).subscribe({
      next: (updated) => {
        this.profileRecord.set(updated);
        this.editAddressSaving.set(false);
        this.editAddressDialogVisible.set(false);
        this.authService.getMeData().subscribe();
      },
      error: () => {
        this.editAddressSaving.set(false);
        this.editAddressError.set("Impossible d'enregistrer les modifications. Réessayez.");
      },
    });
  }

  goToSection(section: ProfileSection): void {
    this.activeSection.set(section);
    document.getElementById(`profile-${section}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  private loadProfileDetails(): void {
    const userId = Number(this.user()?.id);
    if (!Number.isFinite(userId)) {
      this.profileDetailsError.set(true);
      return;
    }
    // GET /profiles has no userId filter (Swagger confirms "No parameters"), so the
    // only real way to find this user's profile is to list them all and match userId.
    this.profileService.list().subscribe({
      next: (profiles) => {
        const match = profiles.find((profile) => profile.userId === userId);
        if (match) {
          this.profileRecord.set(match);
        } else {
          this.profileDetailsError.set(true);
        }
      },
      error: () => this.profileDetailsError.set(true),
    });
  }

  private splitName(fullName: string | undefined): { first: string; last: string } {
    const parts = (fullName ?? '').trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) {
      return { first: '', last: '' };
    }
    return { first: parts[0], last: parts.slice(1).join(' ') };
  }

  private joinLocation(parts: (string | undefined)[]): string {
    return parts.filter((part): part is string => !!part).join(', ');
  }
}
