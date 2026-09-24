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

import { VillePartenaireService } from '../../../core/configuration/ville-partenaire.service';
import { VillePartenaireResponse } from '../../../core/configuration/models/ville-partenaire.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

@Component({
  selector: 'app-ville-partenaire',
  templateUrl: './ville-partenaire.component.html',
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
export class VillePartenaireComponent {
  private readonly villePartenaireService = inject(VillePartenaireService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<VillePartenaireResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly deleteTarget = signal<VillePartenaireResponse | null>(null);
  readonly deleting = signal(false);

  readonly form = this.fb.nonNullable.group({
    nomVille: [''],
    regions: [''],
    active: [true],
  });

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const items = this.items();
    if (!query) return items;
    return items.filter(
      (item) => item.nomVille.toLowerCase().includes(query) || item.regions.toLowerCase().includes(query)
    );
  });

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.villePartenaireService.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les villes partenaires.');
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
    this.form.reset({ nomVille: '', regions: '', active: true });
    this.formVisible.set(true);
  }

  openEdit(item: VillePartenaireResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.form.reset({ nomVille: item.nomVille, regions: item.regions, active: item.active });
    this.formVisible.set(true);
  }

  save(): void {
    const payload = this.form.getRawValue();
    if (!payload.nomVille.trim() || !payload.regions.trim()) {
      this.formError.set('Le nom de la ville et la région sont requis.');
      return;
    }
    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request = id ? this.villePartenaireService.update(id, payload) : this.villePartenaireService.create(payload);
    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer la ville partenaire. Réessayez.");
      },
    });
  }

  confirmDelete(item: VillePartenaireResponse): void {
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleting.set(true);
    this.villePartenaireService.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.error.set('Impossible de supprimer cette ville partenaire.');
        this.deleteTarget.set(null);
      },
    });
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }
}
