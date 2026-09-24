import { Component, computed, inject, signal } from '@angular/core';
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

import { ProduitCommissionTauxService } from '../../../core/configuration/produit-commission-taux.service';
import {
  ProduitCommissionTauxResponse,
  TypePartenaire,
} from '../../../core/configuration/models/produit-commission-taux.model';
import { ProduitService } from '../../../core/configuration/produit.service';
import { ProduitResponse } from '../../../core/configuration/models/produit.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type PartenaireFilter = 'all' | TypePartenaire;

@Component({
  selector: 'app-taux-commission',
  templateUrl: './taux-commission.component.html',
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
export class TauxCommissionComponent {
  private readonly tauxService = inject(ProduitCommissionTauxService);
  private readonly produitService = inject(ProduitService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<ProduitCommissionTauxResponse[]>([]);
  readonly produits = signal<ProduitResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly partenaireFilter = signal<PartenaireFilter>('all');

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly deleteTarget = signal<ProduitCommissionTauxResponse | null>(null);
  readonly deleting = signal(false);

  readonly form = this.fb.nonNullable.group({
    produitId: [0],
    typePartenaire: ['DIRECT'],
    libTauxCommission: [''],
    tauxCommission: [0],
    active: [true],
  });

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const filter = this.partenaireFilter();
    return this.items()
      .filter((item) => filter === 'all' || item.typePartenaire === filter)
      .filter(
        (item) =>
          !query ||
          item.produitName.toLowerCase().includes(query) ||
          item.libTauxCommission.toLowerCase().includes(query)
      );
  });

  constructor() {
    this.fetch();
    this.fetchProduits();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.tauxService.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les taux de commission.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  setPartenaireFilter(filter: PartenaireFilter): void {
    this.partenaireFilter.set(filter);
  }

  openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({
      produitId: 0,
      typePartenaire: 'DIRECT',
      libTauxCommission: '',
      tauxCommission: 0,
      active: true,
    });
    this.formVisible.set(true);
  }

  openEdit(item: ProduitCommissionTauxResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.form.reset({
      produitId: item.produitId,
      typePartenaire: item.typePartenaire,
      libTauxCommission: item.libTauxCommission,
      tauxCommission: item.tauxCommission,
      active: item.active,
    });
    this.formVisible.set(true);
  }

  save(): void {
    const raw = this.form.getRawValue();
    if (!raw.produitId) {
      this.formError.set('Sélectionnez un produit.');
      return;
    }
    if (!raw.libTauxCommission.trim()) {
      this.formError.set('Le libellé est requis.');
      return;
    }
    const payload = {
      produitId: Number(raw.produitId),
      typePartenaire: raw.typePartenaire as TypePartenaire,
      libTauxCommission: raw.libTauxCommission,
      tauxCommission: Number(raw.tauxCommission),
      active: raw.active,
    };
    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request = id ? this.tauxService.update(id, payload) : this.tauxService.create(payload);
    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer le taux de commission. Réessayez.");
      },
    });
  }

  confirmDelete(item: ProduitCommissionTauxResponse): void {
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleting.set(true);
    this.tauxService.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.error.set('Impossible de supprimer ce taux de commission.');
        this.deleteTarget.set(null);
      },
    });
  }

  private fetchProduits(): void {
    this.produitService.list(0, 200).subscribe({
      next: (page) => this.produits.set(page.content),
      error: () => {
        // Silent: the produit dropdown will just stay empty if this fails.
      },
    });
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }

  formatTaux(value: number): string {
    return `${value.toFixed(2)} %`;
  }

  typeLabel(type: TypePartenaire): string {
    if (type === 'DIRECT') return 'Direct';
    if (type === 'AGENT') return 'Agent';
    return 'Apporteur';
  }

  typeVariant(type: TypePartenaire): string {
    if (type === 'DIRECT') return 'info';
    if (type === 'AGENT') return 'neutral';
    return 'warning';
  }
}
