import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';
import { TranslocoPipe } from '@jsverse/transloco';

import { SinistreService } from '../../core/sinistres/sinistre.service';
import {
  SinistrePhotoResponse,
  SinistreResponse,
  SinistreStatus,
  SinistreSuiviResponse,
} from '../../core/sinistres/models/sinistre.model';
import {
  SINISTRE_OPEN_STATUSES,
  SINISTRE_STATUS_LABEL,
  SINISTRE_STATUS_ORDER,
  SINISTRE_STATUS_VARIANT,
} from '../../core/sinistres/sinistre-status';
import { CompagnieService } from '../../core/configuration/compagnie.service';
import { CompagnieResponse } from '../../core/configuration/models/compagnie.model';
import { ProfileService } from '../../core/profile/profile.service';
import { ProfileRecord } from '../../core/profile/models/profile-details.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { ThousandSeparatorDirective } from '../../shared/directives/thousand-separator.directive';
import { AuditHistoryComponent } from '../../shared/audit-history/audit-history.component';
import { EntityDocumentsComponent } from '../../shared/entity-documents/entity-documents.component';

type SinistresMode = 'liste' | 'suivi';
type StatusFilter = 'all' | SinistreStatus;

const COMMENTAIRE_MAX_LENGTH = 2000;

@Component({
  selector: 'app-sinistres',
  templateUrl: './sinistres.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    AlertMessageComponent,
    ModalComponent,
    ThousandSeparatorDirective,
    TranslocoPipe,
    AuditHistoryComponent,
    EntityDocumentsComponent,
  ],
})
export class SinistresComponent {
  private readonly sinistreService = inject(SinistreService);
  private readonly compagnieService = inject(CompagnieService);
  private readonly profileService = inject(ProfileService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly mode = (this.route.snapshot.data['mode'] as SinistresMode) ?? 'liste';
  readonly titleKey = this.mode === 'suivi' ? 'sinistres.mode.suivi' : 'sinistres.mode.liste';
  readonly isSuiviMode = this.mode === 'suivi';
  readonly statusOrder = SINISTRE_STATUS_ORDER;

  readonly pageSize = 20;

  readonly allItems = signal<SinistreResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly statusFilter = signal<StatusFilter>('all');
  readonly showClosedInSuivi = signal(false);
  readonly pageIndex = signal(0);

  readonly detailVisible = signal(false);
  readonly selectedSinistre = signal<SinistreResponse | null>(null);
  readonly suivis = signal<SinistreSuiviResponse[]>([]);
  readonly suivisLoading = signal(true);
  readonly suivisError = signal<string | null>(null);

  readonly photos = signal<SinistrePhotoResponse[]>([]);
  readonly photosLoading = signal(true);
  readonly photoError = signal<string | null>(null);
  readonly photoUploading = signal(false);
  readonly viewingPhotoId = signal<number | null>(null);

  readonly suiviForm = this.fb.nonNullable.group({
    statut: [''],
    commentaire: [''],
    montantIndemnise: [0],
  });
  readonly suiviSaving = signal(false);
  readonly suiviError = signal<string | null>(null);

  readonly compagnies = signal<CompagnieResponse[]>([]);
  readonly gestionnaires = signal<ProfileRecord[]>([]);

  readonly editMode = signal(false);
  readonly editForm = this.fb.nonNullable.group({
    nature: [''],
    dateSurvenance: [''],
    montantEstime: [0],
    compagnieId: [0],
    assigneeId: [0],
    description: [''],
    notes: [''],
  });
  readonly editSaving = signal(false);
  readonly editError = signal<string | null>(null);

  readonly commentaireMaxLength = COMMENTAIRE_MAX_LENGTH;
  private readonly commentaireValue = toSignal(this.suiviForm.controls.commentaire.valueChanges, {
    initialValue: this.suiviForm.controls.commentaire.value,
  });
  readonly commentaireLength = computed(() => this.commentaireValue().length);

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  readonly scopedItems = computed(() => {
    const all = this.allItems();
    if (this.mode === 'suivi') {
      return this.showClosedInSuivi() ? all : all.filter((s) => SINISTRE_OPEN_STATUSES.includes(s.status));
    }
    const filter = this.statusFilter();
    return filter === 'all' ? all : all.filter((s) => s.status === filter);
  });

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const scoped = this.scopedItems();
    if (!query) return scoped;
    return scoped.filter((s) =>
      `${s.numero} ${s.insuredName} ${s.contractPolicyNumber} ${s.nature}`.toLowerCase().includes(query)
    );
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filteredItems().length / this.pageSize)));

  readonly pagedItems = computed(() => {
    const start = this.pageIndex() * this.pageSize;
    return this.filteredItems().slice(start, start + this.pageSize);
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.searchDebounce) clearTimeout(this.searchDebounce);
    });
    this.fetch();
    this.compagnieService.list(0, 200, undefined, true).subscribe({
      next: (page) => this.compagnies.set(page.content),
      error: () => undefined,
    });
    this.profileService.list().subscribe({
      next: (profiles) => this.gestionnaires.set(profiles),
      error: () => undefined,
    });
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.pageIndex.set(0);
    this.sinistreService.list(0, 500).subscribe({
      next: (page) => {
        this.allItems.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('sinistres.errors.chargementListe');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.pageIndex.set(0), 400);
  }

  setStatusFilter(value: string): void {
    this.statusFilter.set(value as StatusFilter);
    this.pageIndex.set(0);
  }

  toggleShowClosed(): void {
    this.showClosedInSuivi.set(!this.showClosedInSuivi());
    this.pageIndex.set(0);
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
  }

  openDetail(sinistre: SinistreResponse): void {
    this.selectedSinistre.set(sinistre);
    this.suiviForm.reset({
      statut: sinistre.status,
      commentaire: '',
      montantIndemnise: sinistre.montantIndemnise ?? 0,
    });
    this.suiviError.set(null);
    this.editMode.set(false);
    this.editError.set(null);
    this.detailVisible.set(true);
    this.fetchSuivis(sinistre.id);
    this.fetchPhotos(sinistre.id);
    // Relit la fiche complète (GET /sinistres/{id}) plutôt que de se fier uniquement
    // à l'entrée de la liste, qui peut être légèrement obsolète.
    this.sinistreService.getById(sinistre.id).subscribe({
      next: (fresh) => this.selectedSinistre.set(fresh),
      error: () => undefined,
    });
  }

  startEdit(): void {
    const sinistre = this.selectedSinistre();
    if (!sinistre) return;
    this.editForm.reset({
      nature: sinistre.nature,
      dateSurvenance: sinistre.dateSurvenance,
      montantEstime: sinistre.montantEstime ?? 0,
      compagnieId: sinistre.compagnieId ?? 0,
      assigneeId: sinistre.assigneeId ?? 0,
      description: sinistre.description ?? '',
      notes: sinistre.notes ?? '',
    });
    this.editError.set(null);
    this.editMode.set(true);
  }

  cancelEdit(): void {
    this.editMode.set(false);
    this.editError.set(null);
  }

  submitEdit(): void {
    const sinistre = this.selectedSinistre();
    if (!sinistre) return;
    const raw = this.editForm.getRawValue();
    if (!raw.nature.trim()) {
      this.editError.set('sinistres.errors.natureRequise');
      return;
    }
    if (!raw.dateSurvenance) {
      this.editError.set('sinistres.errors.dateRequise');
      return;
    }
    this.editSaving.set(true);
    this.editError.set(null);
    this.sinistreService
      .update(sinistre.id, {
        contractId: sinistre.contractId,
        nature: raw.nature.trim(),
        dateSurvenance: raw.dateSurvenance,
        montantEstime: raw.montantEstime ? Number(raw.montantEstime) : undefined,
        compagnieId: raw.compagnieId ? Number(raw.compagnieId) : undefined,
        assigneeId: raw.assigneeId ? Number(raw.assigneeId) : undefined,
        description: raw.description.trim() || undefined,
        notes: raw.notes.trim() || undefined,
      })
      .subscribe({
        next: (updated) => {
          this.editSaving.set(false);
          this.editMode.set(false);
          this.selectedSinistre.set(updated);
          this.fetch();
        },
        error: () => {
          this.editSaving.set(false);
          this.editError.set('sinistres.errors.enregistrementModification');
        },
      });
  }

  private fetchPhotos(sinistreId: number): void {
    this.photosLoading.set(true);
    this.photoError.set(null);
    this.sinistreService.listPhotos(sinistreId).subscribe({
      next: (photos) => {
        this.photos.set(photos);
        this.photosLoading.set(false);
      },
      error: () => {
        this.photoError.set('sinistres.photos.erreurChargement');
        this.photosLoading.set(false);
      },
    });
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const sinistre = this.selectedSinistre();
    if (!file || !sinistre) return;

    this.photoUploading.set(true);
    this.photoError.set(null);
    this.sinistreService.uploadPhoto(sinistre.id, file).subscribe({
      next: () => {
        this.photoUploading.set(false);
        input.value = '';
        this.fetchPhotos(sinistre.id);
      },
      error: () => {
        this.photoUploading.set(false);
        input.value = '';
        this.photoError.set('sinistres.photos.erreurJoindre');
      },
    });
  }

  viewPhoto(photo: SinistrePhotoResponse): void {
    const sinistre = this.selectedSinistre();
    if (!sinistre) return;
    this.viewingPhotoId.set(photo.id);
    this.photoError.set(null);
    this.sinistreService.downloadPhoto(sinistre.id, photo.id).subscribe({
      next: (blob) => {
        this.viewingPhotoId.set(null);
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => {
        this.viewingPhotoId.set(null);
        this.photoError.set('sinistres.photos.erreurAfficher');
      },
    });
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  private fetchSuivis(sinistreId: number): void {
    this.suivisLoading.set(true);
    this.suivisError.set(null);
    this.sinistreService.getSuivis(sinistreId).subscribe({
      next: (suivis) => {
        this.suivis.set(suivis);
        this.suivisLoading.set(false);
      },
      error: () => {
        this.suivisError.set('sinistres.errors.chargementHistorique');
        this.suivisLoading.set(false);
      },
    });
  }

  submitSuivi(): void {
    const sinistre = this.selectedSinistre();
    if (!sinistre) return;
    const raw = this.suiviForm.getRawValue();
    if (!raw.statut) {
      this.suiviError.set('sinistres.errors.statutRequis');
      return;
    }
    this.suiviSaving.set(true);
    this.suiviError.set(null);
    this.sinistreService
      .addSuivi(sinistre.id, {
        statut: raw.statut as SinistreStatus,
        commentaire: raw.commentaire.trim() || undefined,
        montantIndemnise: raw.montantIndemnise ? Number(raw.montantIndemnise) : undefined,
      })
      .subscribe({
        next: () => {
          this.suiviSaving.set(false);
          this.selectedSinistre.set({ ...sinistre, status: raw.statut as SinistreStatus });
          this.fetchSuivis(sinistre.id);
          this.fetch();
        },
        error: () => {
          this.suiviSaving.set(false);
          this.suiviError.set('sinistres.errors.enregistrementSuivi');
        },
      });
  }

  /** Legacy French-only label, kept for pages not yet migrated to Transloco
   * (contrats/assures still read SINISTRE_STATUS_LABEL directly). */
  statusLabel(status: SinistreStatus): string {
    return SINISTRE_STATUS_LABEL[status];
  }

  statusLabelKey(status: SinistreStatus): string {
    return `sinistres.status.${status}`;
  }

  statusVariant(status: SinistreStatus): string {
    return SINISTRE_STATUS_VARIANT[status];
  }

  gestionnaireLabel(profile: ProfileRecord): string {
    return `${profile.firstname} ${profile.lastname}`;
  }

  formatAmount(value: number | null): string {
    return value === null ? '—' : `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string): string {
    return new Date(value).toLocaleString('fr-FR');
  }
}
