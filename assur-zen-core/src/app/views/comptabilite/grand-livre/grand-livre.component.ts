import { Component, computed, inject, signal } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, ColComponent, FormLabelDirective, FormSelectDirective, RowComponent, SpinnerComponent } from '@coreui/angular';

import { LedgerService } from '../../../core/ledger/ledger.service';
import { CompteComptableResponse, GrandLivreLineResponse, TypeOperation } from '../../../core/ledger/models/ledger.model';

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

@Component({
  selector: 'app-grand-livre',
  templateUrl: './grand-livre.component.html',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, RowComponent, ColComponent, FormSelectDirective, FormLabelDirective],
})
export class GrandLivreComponent {
  private readonly ledgerService = inject(LedgerService);

  readonly comptes = signal<CompteComptableResponse[]>([]);
  readonly comptesLoading = signal(true);
  readonly comptesError = signal<string | null>(null);

  readonly selectedCompteId = signal<number | null>(null);
  readonly lines = signal<GrandLivreLineResponse[]>([]);
  readonly linesLoading = signal(false);
  readonly linesError = signal<string | null>(null);

  readonly selectedCompte = computed(() => this.comptes().find((c) => c.id === this.selectedCompteId()) ?? null);

  readonly statTotalDebit = computed(() => this.lines().reduce((sum, l) => sum + l.montantDebit, 0));
  readonly statTotalCredit = computed(() => this.lines().reduce((sum, l) => sum + l.montantCredit, 0));
  readonly statSolde = computed(() => {
    const lines = this.lines();
    return lines.length ? lines[lines.length - 1].soldeProgressif : 0;
  });

  constructor() {
    this.fetchComptes();
  }

  fetchComptes(): void {
    this.comptesLoading.set(true);
    this.comptesError.set(null);
    this.ledgerService.listComptes().subscribe({
      next: (items) => {
        this.comptes.set(items);
        this.comptesLoading.set(false);
      },
      error: () => {
        this.comptesError.set('Impossible de charger le plan comptable.');
        this.comptesLoading.set(false);
      },
    });
  }

  onCompteSelected(value: string): void {
    const id = Number(value) || null;
    this.selectedCompteId.set(id);
    this.lines.set([]);
    this.linesError.set(null);
    if (!id) return;

    this.linesLoading.set(true);
    this.ledgerService.grandLivre(id).subscribe({
      next: (lines) => {
        this.lines.set(lines);
        this.linesLoading.set(false);
      },
      error: () => {
        this.linesError.set('Impossible de charger le grand livre de ce compte.');
        this.linesLoading.set(false);
      },
    });
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
}
