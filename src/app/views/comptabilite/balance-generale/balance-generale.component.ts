import { Component, computed, inject, signal } from '@angular/core';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { LedgerService } from '../../../core/ledger/ledger.service';
import { BalanceCompteResponse, TypeCompte } from '../../../core/ledger/models/ledger.model';

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
  selector: 'app-balance-generale',
  templateUrl: './balance-generale.component.html',
  imports: [ButtonDirective, SpinnerComponent],
})
export class BalanceGeneraleComponent {
  private readonly ledgerService = inject(LedgerService);

  readonly items = signal<BalanceCompteResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly typeFilter = signal<TypeFilter>('all');

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const filter = this.typeFilter();
    return this.items()
      .filter((item) => filter === 'all' || item.type === filter)
      .filter((item) => !query || item.compteCode.toLowerCase().includes(query) || item.compteLibelle.toLowerCase().includes(query));
  });

  readonly totalDebit = computed(() => this.filteredItems().reduce((sum, item) => sum + item.totalDebit, 0));
  readonly totalCredit = computed(() => this.filteredItems().reduce((sum, item) => sum + item.totalCredit, 0));
  readonly totalSolde = computed(() => this.filteredItems().reduce((sum, item) => sum + item.solde, 0));
  readonly equilibree = computed(() => this.totalDebit() === this.totalCredit());

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.ledgerService.balance().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger la balance générale.');
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

  typeLabel(type: TypeCompte): string {
    return TYPE_LABEL[type];
  }

  typeVariant(type: TypeCompte): string {
    return TYPE_VARIANT[type];
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }
}
