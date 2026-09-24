import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  DropdownComponent,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { ProductTypeService } from '../../../core/configuration/product-type.service';
import { ProductTypeResponse } from '../../../core/configuration/models/product-type.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type ActifFilter = 'all' | 'active' | 'inactive';

const EMPTY_FORM = {
  code: '',
  nom: '',
  ordre: 0,
  couleur: '#5856d6',
  icone: '',
  description: '',
  actif: true,
};

@Component({
  selector: 'app-type-produit',
  templateUrl: './type-produit.component.html',
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
    FormCheckInputDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class TypeProduitComponent {
  private readonly productTypeService = inject(ProductTypeService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<ProductTypeResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly actifFilter = signal<ActifFilter>('all');

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly deleteTarget = signal<ProductTypeResponse | null>(null);
  readonly deleting = signal(false);

  readonly togglingId = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({ ...EMPTY_FORM });

  readonly scopedItems = computed(() => {
    const items = this.items();
    const actif = this.actifFilter();
    if (actif === 'all') return items;
    return items.filter((i) => i.actif === (actif === 'active'));
  });

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const sorted = [...this.scopedItems()].sort((a, b) => (a.ordre ?? 0) - (b.ordre ?? 0));
    if (!query) return sorted;
    return sorted.filter((i) => `${i.code} ${i.nom}`.toLowerCase().includes(query));
  });

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.productTypeService.list(0, 500).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les types de produit.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  setActifFilter(value: string): void {
    this.actifFilter.set(value as ActifFilter);
  }

  openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({ ...EMPTY_FORM });
    this.formVisible.set(true);
  }

  openEdit(item: ProductTypeResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.form.reset({
      code: item.code,
      nom: item.nom,
      ordre: item.ordre ?? 0,
      couleur: item.couleur ?? '#5856d6',
      icone: item.icone ?? '',
      description: item.description ?? '',
      actif: item.actif,
    });
    this.formVisible.set(true);
  }

  save(): void {
    const raw = this.form.getRawValue();
    if (!raw.code.trim() || !raw.nom.trim()) {
      this.formError.set('Le code et le nom sont requis.');
      return;
    }
    this.formSaving.set(true);
    this.formError.set(null);
    const payload = {
      code: raw.code.trim(),
      nom: raw.nom.trim(),
      ordre: raw.ordre ? Number(raw.ordre) : undefined,
      couleur: raw.couleur || undefined,
      icone: raw.icone.trim() || undefined,
      description: raw.description.trim() || undefined,
      actif: raw.actif,
    };
    const id = this.editingId();
    const request = id ? this.productTypeService.update(id, payload) : this.productTypeService.create(payload);
    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer ce type de produit.");
      },
    });
  }

  toggleActive(item: ProductTypeResponse): void {
    this.togglingId.set(item.id);
    this.error.set(null);
    const request = item.actif ? this.productTypeService.disable(item.id) : this.productTypeService.enable(item.id);
    request.subscribe({
      next: (updated) => {
        this.togglingId.set(null);
        this.items.set(this.items().map((i) => (i.id === updated.id ? updated : i)));
      },
      error: () => {
        this.togglingId.set(null);
        this.error.set('Impossible de changer le statut de ce type de produit.');
      },
    });
  }

  confirmDelete(item: ProductTypeResponse): void {
    if (item.systeme) return;
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleting.set(true);
    this.productTypeService.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.error.set('Impossible de supprimer ce type de produit.');
      },
    });
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }
}
