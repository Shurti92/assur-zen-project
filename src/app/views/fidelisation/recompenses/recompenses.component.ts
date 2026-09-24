import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, FormControlDirective, FormDirective, FormLabelDirective, SpinnerComponent } from '@coreui/angular';

import { FideliteService } from '../../../core/fidelite/fidelite.service';
import { RecompenseRequest, RecompenseResponse } from '../../../core/fidelite/models/fidelite.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

const EMPTY_FORM = { code: '', nom: '', description: '', coutPoints: '', stock: '', active: true };

@Component({
  selector: 'app-recompenses',
  templateUrl: './recompenses.component.html',
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
export class RecompensesComponent {
  private readonly fideliteService = inject(FideliteService);
  private readonly fb = inject(FormBuilder);

  readonly recompenses = signal<RecompenseResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');

  readonly formVisible = signal(false);
  readonly formMode = signal<'create' | 'edit'>('create');
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({ ...EMPTY_FORM });

  readonly deleteTarget = signal<RecompenseResponse | null>(null);
  readonly deleting = signal(false);

  readonly filteredRecompenses = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const sorted = [...this.recompenses()].sort((a, b) => a.coutPoints - b.coutPoints);
    if (!query) return sorted;
    return sorted.filter((r) => `${r.code} ${r.nom}`.toLowerCase().includes(query));
  });

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.fideliteService.listRecompenses().subscribe({
      next: (recompenses) => {
        this.recompenses.set(recompenses);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger le catalogue de récompenses.');
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

  openEdit(recompense: RecompenseResponse): void {
    this.formMode.set('edit');
    this.editingId.set(recompense.id);
    this.form.reset({
      code: recompense.code,
      nom: recompense.nom,
      description: recompense.description ?? '',
      coutPoints: String(recompense.coutPoints),
      stock: recompense.stock != null ? String(recompense.stock) : '',
      active: recompense.active,
    });
    this.formError.set(null);
    this.formVisible.set(true);
  }

  closeForm(): void {
    this.formVisible.set(false);
  }

  submitForm(): void {
    const raw = this.form.getRawValue();
    if (!raw.code.trim() || !raw.nom.trim()) {
      this.formError.set('Le code et le nom sont requis.');
      return;
    }
    if (!raw.coutPoints) {
      this.formError.set('Le coût en points est requis.');
      return;
    }

    const payload: RecompenseRequest = {
      code: raw.code.trim(),
      nom: raw.nom.trim(),
      description: raw.description.trim() || undefined,
      coutPoints: Number(raw.coutPoints),
      stock: raw.stock ? Number(raw.stock) : undefined,
      active: raw.active,
    };

    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request$ = id
      ? this.fideliteService.updateRecompense(id, payload)
      : this.fideliteService.createRecompense(payload);
    request$.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer cette récompense.");
      },
    });
  }

  askDelete(recompense: RecompenseResponse): void {
    this.deleteTarget.set(recompense);
  }

  cancelDelete(): void {
    this.deleteTarget.set(null);
  }

  confirmDelete(): void {
    const recompense = this.deleteTarget();
    if (!recompense) return;
    this.deleting.set(true);
    this.fideliteService.deleteRecompense(recompense.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.error.set('Impossible de retirer cette récompense.');
      },
    });
  }

  formatPoints(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} pts`;
  }
}
