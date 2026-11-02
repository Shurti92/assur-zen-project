import { Component, inject, signal } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { ContractService } from '../../../core/contracts/contract.service';
import { ContractResponse } from '../../../core/contracts/models/contract.model';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';

/**
 * Contrats signés, tous partenaires confondus, dont la prime encaissée par le partenaire n'a
 * pas encore été reversée à la compagnie — réservé aux administrateurs (DTO/endpoints livrés
 * le 2026-08-22, voir GET/POST /contracts/prime-non-reversee et /contracts/{id}/prime/reverser).
 */
@Component({
  selector: 'app-prime-non-reversee',
  templateUrl: './prime-non-reversee.component.html',
  imports: [ButtonDirective, SpinnerComponent, IconDirective, ConfirmDialogComponent],
})
export class PrimeNonReverseeComponent {
  private readonly contractService = inject(ContractService);

  readonly pageSize = 20;

  readonly items = signal<ContractResponse[]>([]);
  readonly pageIndex = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(1);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly reverserTarget = signal<ContractResponse | null>(null);
  readonly reversing = signal(false);
  readonly reverserError = signal<string | null>(null);

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.contractService.listPrimeNonReversee(this.pageIndex(), this.pageSize).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(Math.max(1, result.totalPages));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les contrats.');
        this.loading.set(false);
      },
    });
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
    this.fetch();
  }

  askReverser(item: ContractResponse): void {
    this.reverserError.set(null);
    this.reverserTarget.set(item);
  }

  cancelReverser(): void {
    this.reverserTarget.set(null);
  }

  confirmReverser(): void {
    const item = this.reverserTarget();
    if (!item) return;
    this.reversing.set(true);
    this.contractService.reverserPrime(item.id).subscribe({
      next: () => {
        this.reversing.set(false);
        this.reverserTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.reversing.set(false);
        this.reverserTarget.set(null);
        this.error.set('Impossible de marquer cette prime comme reversée.');
      },
    });
  }

  clientLabel(item: ContractResponse): string {
    if (item.clientFirstName || item.clientLastName) {
      return `${item.clientFirstName ?? ''} ${item.clientLastName ?? ''}`.trim();
    }
    return item.insuredName ?? '—';
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }
}
