import { Component, computed, inject, signal } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, ColComponent, RowComponent, SpinnerComponent } from '@coreui/angular';

import { LedgerService } from '../../../core/ledger/ledger.service';
import { EcritureComptableResponse, TypeOperation } from '../../../core/ledger/models/ledger.model';
import { ModalComponent } from '../../../shared/modal/modal.component';

type TypeFilter = 'all' | TypeOperation;

const TYPE_LABEL: Record<TypeOperation, string> = {
  SOUSCRIPTION: 'Souscription',
  PAIEMENT: 'Paiement',
  COMMISSION: 'Commission',
  ANNULATION: 'Annulation',
};

const TYPE_VARIANT: Record<TypeOperation, string> = {
  SOUSCRIPTION: 'info',
  PAIEMENT: 'success',
  COMMISSION: 'warning',
  ANNULATION: 'danger',
};

const TYPE_ORDER: TypeOperation[] = ['SOUSCRIPTION', 'PAIEMENT', 'COMMISSION', 'ANNULATION'];

@Component({
  selector: 'app-ecritures',
  templateUrl: './ecritures.component.html',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, RowComponent, ColComponent, ModalComponent],
})
export class EcrituresComponent {
  private readonly ledgerService = inject(LedgerService);

  readonly typeOrder = TYPE_ORDER;
  readonly pageSize = 20;

  readonly items = signal<EcritureComptableResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly typeFilter = signal<TypeFilter>('all');
  readonly pageIndex = signal(0);

  readonly selected = signal<EcritureComptableResponse | null>(null);

  readonly scopedItems = computed(() => {
    const filter = this.typeFilter();
    const all = this.items();
    return filter === 'all' ? all : all.filter((e) => e.typeOperation === filter);
  });

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const scoped = this.scopedItems();
    if (!query) return scoped;
    return scoped.filter((e) => `${e.reference} ${e.libelle} ${e.contractPolicyNumber}`.toLowerCase().includes(query));
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filteredItems().length / this.pageSize)));

  readonly pagedItems = computed(() => {
    const start = this.pageIndex() * this.pageSize;
    return this.filteredItems().slice(start, start + this.pageSize);
  });

  readonly statTotal = computed(() => this.filteredItems().length);

  readonly statTotalDebit = computed(() =>
    this.filteredItems().reduce((sum, e) => sum + e.lignes.reduce((s, l) => s + l.montantDebit, 0), 0)
  );

  readonly statTotalCredit = computed(() =>
    this.filteredItems().reduce((sum, e) => sum + e.lignes.reduce((s, l) => s + l.montantCredit, 0), 0)
  );

  readonly statContrepassees = computed(() => this.filteredItems().filter((e) => e.contrepassee).length);

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.pageIndex.set(0);
    this.ledgerService.listAllEcritures().subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les écritures comptables.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    this.pageIndex.set(0);
  }

  setTypeFilter(filter: TypeFilter): void {
    this.typeFilter.set(filter);
    this.pageIndex.set(0);
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
  }

  openDetail(item: EcritureComptableResponse): void {
    this.selected.set(item);
  }

  closeDetail(): void {
    this.selected.set(null);
  }

  ligneTotal(item: EcritureComptableResponse, field: 'montantDebit' | 'montantCredit'): number {
    return item.lignes.reduce((sum, l) => sum + l[field], 0);
  }

  typeLabel(type: TypeOperation): string {
    return TYPE_LABEL[type];
  }

  typeVariant(type: TypeOperation): string {
    return TYPE_VARIANT[type];
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }

  formatDateTime(value: string): string {
    return new Date(value).toLocaleString('fr-FR');
  }
}
