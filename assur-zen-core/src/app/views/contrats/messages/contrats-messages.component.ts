import { Component, computed, inject, signal } from '@angular/core';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';
import { catchError, forkJoin, map, of } from 'rxjs';

import { ContractService } from '../../../core/contracts/contract.service';
import { ContractMessageResponse, ContractResponse } from '../../../core/contracts/models/contract.model';
import { ModalComponent } from '../../../shared/modal/modal.component';

interface MessagesSummary {
  latest: ContractMessageResponse | null;
  count: number;
}

const TYPE_MESSAGE_LABEL: Record<string, string> = {
  bienvenue: 'Bienvenue',
  rappel_echeance: "Rappel d'échéance",
  avis_echeance: "Avis d'échéance",
  fin_contrat: 'Fin de contrat',
};

const STATUT_ENVOI_LABEL: Record<string, string> = {
  en_attente: 'En attente',
  envoye: 'Envoyé',
  echoue: 'Échec',
  non_envoye: 'Échec',
  message_anti_date: 'Anti-daté',
};

const STATUT_ENVOI_VARIANT: Record<string, string> = {
  en_attente: 'warning',
  envoye: 'success',
  echoue: 'danger',
  non_envoye: 'danger',
  message_anti_date: 'neutral',
};

@Component({
  selector: 'app-contrats-messages',
  templateUrl: './contrats-messages.component.html',
  imports: [ButtonDirective, SpinnerComponent, ModalComponent],
})
export class ContratsMessagesComponent {
  private readonly contractService = inject(ContractService);

  readonly contracts = signal<ContractResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');

  readonly filteredContracts = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    if (!query) return this.contracts();
    return this.contracts().filter((c) =>
      `${c.policyNumber} ${c.insuredName}`.toLowerCase().includes(query),
    );
  });

  readonly selectedContract = signal<ContractResponse | null>(null);
  readonly messages = signal<ContractMessageResponse[]>([]);
  readonly messagesLoading = signal(false);
  readonly messagesError = signal<string | null>(null);

  readonly summaries = signal<Map<number, MessagesSummary>>(new Map());
  readonly summariesLoading = signal(false);

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.contractService.listAll().subscribe({
      next: (page) => {
        this.contracts.set(page.content);
        this.loading.set(false);
        this.loadSummaries(page.content);
      },
      error: () => {
        this.error.set('Impossible de charger les contrats.');
        this.loading.set(false);
      },
    });
  }

  /** Loads each contract's messages in parallel to derive a per-row status chip —
   * there is no bulk endpoint, only GET /contracts/{id}/messages. */
  private loadSummaries(contracts: ContractResponse[]): void {
    if (contracts.length === 0) {
      this.summaries.set(new Map());
      return;
    }
    this.summariesLoading.set(true);
    forkJoin(
      contracts.map((c) =>
        this.contractService.getMessages(c.id).pipe(
          map((messages) => ({ contractId: c.id, messages })),
          catchError(() => of({ contractId: c.id, messages: [] as ContractMessageResponse[] })),
        ),
      ),
    ).subscribe((results) => {
      const next = new Map<number, MessagesSummary>();
      for (const r of results) {
        const sorted = [...r.messages].sort((a, b) => b.dateEnvoi.localeCompare(a.dateEnvoi));
        next.set(r.contractId, { latest: sorted[0] ?? null, count: r.messages.length });
      }
      this.summaries.set(next);
      this.summariesLoading.set(false);
    });
  }

  summaryFor(contractId: number): MessagesSummary | undefined {
    return this.summaries().get(contractId);
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  openMessages(contract: ContractResponse): void {
    this.selectedContract.set(contract);
    this.messagesLoading.set(true);
    this.messagesError.set(null);
    this.contractService.getMessages(contract.id).subscribe({
      next: (items) => {
        this.messages.set(items);
        this.messagesLoading.set(false);
      },
      error: () => {
        this.messagesError.set('Impossible de charger les messages SMS de ce contrat.');
        this.messagesLoading.set(false);
      },
    });
  }

  closeMessages(): void {
    this.selectedContract.set(null);
    this.messages.set([]);
  }

  typeLabel(type: string): string {
    return TYPE_MESSAGE_LABEL[type] ?? this.humanize(type);
  }

  statutLabel(statut: string): string {
    return STATUT_ENVOI_LABEL[statut] ?? this.humanize(statut);
  }

  statutVariant(statut: string): string {
    return STATUT_ENVOI_VARIANT[statut] ?? 'neutral';
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }

  private humanize(value: string): string {
    const spaced = value.replace(/_/g, ' ');
    return spaced.charAt(0).toUpperCase() + spaced.slice(1);
  }
}
