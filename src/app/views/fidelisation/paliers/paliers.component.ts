import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, FormControlDirective, FormDirective, FormLabelDirective, SpinnerComponent } from '@coreui/angular';

import { FideliteService } from '../../../core/fidelite/fidelite.service';
import { PalierFideliteRequest, PalierFideliteResponse } from '../../../core/fidelite/models/fidelite.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

const EMPTY_FORM = { code: '', libelle: '', seuilPoints: '', avantages: '', active: true };

@Component({
  selector: 'app-paliers',
  templateUrl: './paliers.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class PaliersComponent {
  private readonly fideliteService = inject(FideliteService);
  private readonly fb = inject(FormBuilder);

  readonly paliers = signal<PalierFideliteResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');

  readonly formVisible = signal(false);
  readonly formMode = signal<'create' | 'edit'>('create');
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({ ...EMPTY_FORM });

  readonly deactivateTarget = signal<PalierFideliteResponse | null>(null);
  readonly deactivating = signal(false);

  readonly filteredPaliers = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const sorted = [...this.paliers()].sort((a, b) => a.seuilPoints - b.seuilPoints);
    if (!query) return sorted;
    return sorted.filter((p) => `${p.code} ${p.libelle}`.toLowerCase().includes(query));
  });

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.fideliteService.listPaliers().subscribe({
      next: (paliers) => {
        this.paliers.set(paliers);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les paliers de fidélité.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  openCreate(): void {
    this.formMode.set('create');
    this.editingId.set(null);
    this.form.reset({ ...EMPTY_FORM });
    this.formError.set(null);
    this.formVisible.set(true);
  }

  openEdit(palier: PalierFideliteResponse): void {
    this.formMode.set('edit');
    this.editingId.set(palier.id);
    this.form.reset({
      code: palier.code,
      libelle: palier.libelle,
      seuilPoints: String(palier.seuilPoints),
      avantages: palier.avantages ?? '',
      active: palier.active,
    });
    this.formError.set(null);
    this.formVisible.set(true);
  }

  closeForm(): void {
    this.formVisible.set(false);
  }

  submitForm(): void {
    const raw = this.form.getRawValue();
    if (!raw.code.trim() || !raw.libelle.trim()) {
      this.formError.set('Le code et le libellé sont requis.');
      return;
    }
    if (!raw.seuilPoints) {
      this.formError.set('Le seuil de points est requis.');
      return;
    }

    const payload: PalierFideliteRequest = {
      code: raw.code.trim(),
      libelle: raw.libelle.trim(),
      seuilPoints: Number(raw.seuilPoints),
      avantages: raw.avantages.trim() || undefined,
      active: raw.active,
    };

    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request$ = id ? this.fideliteService.updatePalier(id, payload) : this.fideliteService.createPalier(payload);
    request$.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer ce palier.");
      },
    });
  }

  askDeactivate(palier: PalierFideliteResponse): void {
    this.deactivateTarget.set(palier);
  }

  cancelDeactivate(): void {
    this.deactivateTarget.set(null);
  }

  confirmDeactivate(): void {
    const palier = this.deactivateTarget();
    if (!palier) return;
    this.deactivating.set(true);
    this.fideliteService.deactivatePalier(palier.id).subscribe({
      next: () => {
        this.deactivating.set(false);
        this.deactivateTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deactivating.set(false);
        this.deactivateTarget.set(null);
        this.error.set('Impossible de désactiver ce palier.');
      },
    });
  }
}
