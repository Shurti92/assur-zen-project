import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { TypeProfileService } from '../../../core/configuration/type-profile.service';
import { TypeProfileResponse } from '../../../core/configuration/models/type-profile.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

@Component({
  selector: 'app-type-profile',
  templateUrl: './type-profile.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormCheckInputDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class TypeProfileComponent {
  private readonly typeProfileService = inject(TypeProfileService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<TypeProfileResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly deleteTarget = signal<TypeProfileResponse | null>(null);
  readonly deleting = signal(false);

  readonly form = this.fb.nonNullable.group({
    libelle: [''],
    active: [true],
    declencheCreationCompte: [false],
  });

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const items = this.items();
    if (!query) return items;
    return items.filter((item) => item.libelle.toLowerCase().includes(query));
  });

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.typeProfileService.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les types de profil.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({ libelle: '', active: true, declencheCreationCompte: false });
    this.formVisible.set(true);
  }

  openEdit(item: TypeProfileResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.form.reset({ libelle: item.libelle, active: item.active, declencheCreationCompte: item.declencheCreationCompte ?? false });
    this.formVisible.set(true);
  }

  save(): void {
    const payload = this.form.getRawValue();
    if (!payload.libelle.trim()) {
      this.formError.set('Le libellé est requis.');
      return;
    }
    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request = id ? this.typeProfileService.update(id, payload) : this.typeProfileService.create(payload);
    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer le type de profil. Réessayez.");
      },
    });
  }

  confirmDelete(item: TypeProfileResponse): void {
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleting.set(true);
    this.typeProfileService.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.error.set('Impossible de supprimer ce type de profil.');
        this.deleteTarget.set(null);
      },
    });
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }
}
