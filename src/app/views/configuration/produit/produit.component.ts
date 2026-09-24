import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { ProduitService } from '../../../core/configuration/produit.service';
import { ProduitResponse } from '../../../core/configuration/models/produit.model';
import { ProductTypeService } from '../../../core/configuration/product-type.service';
import { ProductTypeSummaryResponse } from '../../../core/configuration/models/product-type.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type ActiveFilter = 'all' | 'active' | 'inactive';

@Component({
  selector: 'app-produit',
  templateUrl: './produit.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormCheckInputDirective,
    FormSelectDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class ProduitComponent {
  private readonly produitService = inject(ProduitService);
  private readonly productTypeService = inject(ProductTypeService);
  private readonly fb = inject(FormBuilder);

  readonly productTypes = signal<ProductTypeSummaryResponse[]>([]);

  readonly pageSize = 20;

  readonly items = signal<ProduitResponse[]>([]);
  readonly pageIndex = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly activeFilter = signal<ActiveFilter>('all');
  readonly exporting = signal(false);

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly deleteTarget = signal<ProduitResponse | null>(null);
  readonly deleting = signal(false);

  readonly form = this.fb.nonNullable.group({
    nomProduit: [''],
    active: [true],
    productTypeId: [''],
  });

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.searchDebounce) clearTimeout(this.searchDebounce);
    });
    this.fetch();
    this.productTypeService.listActive().subscribe({
      next: (types) => this.productTypes.set(types),
      error: () => undefined,
    });
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    const active = this.activeFilter() === 'all' ? undefined : this.activeFilter() === 'active';
    this.produitService.list(this.pageIndex(), this.pageSize, this.searchQuery() || undefined, active).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les produits.');
        this.loading.set(false);
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
    this.produitService.export(this.searchQuery() || undefined, active).subscribe({
      next: (blob) => {
        this.downloadBlob(blob, 'produits.csv');
        this.exporting.set(false);
      },
      error: () => {
        this.error.set("Impossible d'exporter les produits.");
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
    this.form.reset({ nomProduit: '', active: true, productTypeId: '' });
    this.formVisible.set(true);
  }

  openEdit(item: ProduitResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.form.reset({
      nomProduit: item.nomProduit,
      active: item.active,
      productTypeId: item.productTypeId != null ? String(item.productTypeId) : '',
    });
    this.formVisible.set(true);
  }

  save(): void {
    const raw = this.form.getRawValue();
    if (!raw.nomProduit.trim()) {
      this.formError.set('Le nom du produit est requis.');
      return;
    }
    this.formSaving.set(true);
    this.formError.set(null);
    const payload = {
      nomProduit: raw.nomProduit,
      active: raw.active,
      productTypeId: raw.productTypeId ? Number(raw.productTypeId) : undefined,
    };
    const id = this.editingId();
    const request = id ? this.produitService.update(id, payload) : this.produitService.create(payload);
    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer le produit. Réessayez.");
      },
    });
  }

  confirmDelete(item: ProduitResponse): void {
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleting.set(true);
    this.produitService.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.error.set('Impossible de supprimer ce produit.');
        this.deleteTarget.set(null);
      },
    });
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }
}
