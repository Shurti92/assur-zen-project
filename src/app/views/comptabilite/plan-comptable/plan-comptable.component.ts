import { Component, computed, inject, signal } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { LedgerService } from '../../../core/ledger/ledger.service';
import { CompteComptableResponse, GrandLivreLineResponse, TypeCompte } from '../../../core/ledger/models/ledger.model';
import { ModalComponent } from '../../../shared/modal/modal.component';

type TypeFilter = 'all' | TypeCompte;

const TYPE_LABEL: Record<TypeCompte, string> = {
  ACTIF: 'Actif',
  PASSIF: 'Passif',
  CHARGE: 'Charge',
  PRODUIT: 'Produit',
};

const TYPE_VARIANT: Record<TypeCompte, string> = {
  ACTIF: 'info',
  PASSIF: 'warning',
  CHARGE: 'danger',
  PRODUIT: 'success',
};

@Component({
  selector: 'app-plan-comptable',
  templateUrl: './plan-comptable.component.html',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, ModalComponent],
})
export class PlanComptableComponent {
  private readonly ledgerService = inject(LedgerService);

  readonly items = signal<CompteComptableResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly typeFilter = signal<TypeFilter>('all');

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const filter = this.typeFilter();
    return this.items()
      .filter((item) => filter === 'all' || item.type === filter)
      .filter((item) => !query || item.code.toLowerCase().includes(query) || item.libelle.toLowerCase().includes(query));
  });

  readonly selectedCompte = signal<CompteComptableResponse | null>(null);
  readonly grandLivreLines = signal<GrandLivreLineResponse[]>([]);
  readonly grandLivreLoading = signal(false);
  readonly grandLivreError = signal<string | null>(null);

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.ledgerService.listComptes().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger le plan comptable.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  setTypeFilter(filter: TypeFilter): void {
    this.typeFilter.set(filter);
  }

  openGrandLivre(item: CompteComptableResponse): void {
    this.selectedCompte.set(item);
    this.grandLivreLines.set([]);
    this.grandLivreError.set(null);
    this.grandLivreLoading.set(true);
    this.ledgerService.grandLivre(item.id).subscribe({
      next: (lines) => {
        this.grandLivreLines.set(lines);
        this.grandLivreLoading.set(false);
      },
      error: () => {
        this.grandLivreError.set('Impossible de charger le grand livre de ce compte.');
        this.grandLivreLoading.set(false);
      },
    });
  }

  closeGrandLivre(): void {
    this.selectedCompte.set(null);
  }

  typeLabel(type: TypeCompte): string {
    return TYPE_LABEL[type];
  }

  typeVariant(type: TypeCompte): string {
    return TYPE_VARIANT[type];
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }
}
