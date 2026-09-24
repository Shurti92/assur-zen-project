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

import { ProduitCommissionCommercialService } from '../../../core/configuration/produit-commission-commercial.service';
import { ProduitCommissionCommercialResponse } from '../../../core/configuration/models/produit-commission-commercial.model';
import { ProduitService } from '../../../core/configuration/produit.service';
import { ProduitResponse } from '../../../core/configuration/models/produit.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

@Component({
  selector: 'app-commission-commerciale',
  templateUrl: './commission-commerciale.component.html',
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
export class CommissionCommercialeComponent {
  private readonly service = inject(ProduitCommissionCommercialService);
  private readonly produitService = inject(ProduitService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<ProduitCommissionCommercialResponse[]>([]);
  readonly produits = signal<ProduitResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly deleteTarget = signal<ProduitCommissionCommercialResponse | null>(null);
  readonly deleting = signal(false);

  readonly form = this.fb.nonNullable.group({
    produitId: [0],
    libTauxCommission: [''],
    tauxCommission: [0],
    active: [true],
  });

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    return this.items().filter(
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
    this.service.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les commissions commerciales.');
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
    this.form.reset({ produitId: 0, libTauxCommission: '', tauxCommission: 0, active: true });
    this.formVisible.set(true);
  }

  openEdit(item: ProduitCommissionCommercialResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.form.reset({
      produitId: item.produitId,
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
      libTauxCommission: raw.libTauxCommission,
      tauxCommission: Number(raw.tauxCommission),
      active: raw.active,
    };
    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request = id ? this.service.update(id, payload) : this.service.create(payload);
    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer la commission commerciale. Réessayez.");
      },
    });
  }

  confirmDelete(item: ProduitCommissionCommercialResponse): void {
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleting.set(true);
    this.service.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.error.set('Impossible de supprimer cette commission commerciale.');
        this.deleteTarget.set(null);
      },
    });
  }

  private fetchProduits(): void {
    this.produitService.list(0, 200).subscribe({
      next: (page) => this.produits.set(page.content),
      error: () => {
        // Silencieux : la liste déroulante de produits reste vide si ça échoue.
      },
    });
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }

  /** libTauxCommission est l'unité réelle choisie à la config (ex. "%" pour un taux, "FCFA"
   * pour un montant fixe) — même mécanique double mode que ProduitCommissionTaux côté
   * partenaire, confirmée par le backend le 2026-09-08. Ne pas supposer "%" à tort. */
  formatTaux(item: ProduitCommissionCommercialResponse): string {
    if (item.libTauxCommission.trim() === '%') {
      return `${item.tauxCommission.toFixed(2)} %`;
    }
    return `${new Intl.NumberFormat('fr-FR').format(item.tauxCommission)} ${item.libTauxCommission}`;
  }
}
