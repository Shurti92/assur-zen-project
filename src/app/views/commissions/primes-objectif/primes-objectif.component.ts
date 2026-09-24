import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, FormControlDirective, FormLabelDirective, SpinnerComponent } from '@coreui/angular';

import { PrimeObjectifService } from '../../../core/prime-objectif/prime-objectif.service';
import {
  PrimeObjectifResponse,
  PrimeObjectifSimulationResponse,
  PrimeObjectifStatut,
} from '../../../core/prime-objectif/models/prime-objectif.model';
import { PartenaireService } from '../../../core/partenaire/partenaire.service';
import { PartenaireResponse } from '../../../core/partenaire/models/partenaire.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type StatutFilter = 'all' | PrimeObjectifStatut;

const PAGE_SIZE = 20;

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { message?: string } | null;
    if (body?.message) return body.message;
  }
  return fallback;
}

@Component({
  selector: 'app-primes-objectif',
  templateUrl: './primes-objectif.component.html',
  imports: [
    IconDirective,
    FormsModule,
    ButtonDirective,
    FormControlDirective,
    FormLabelDirective,
    SpinnerComponent,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class PrimesObjectifComponent {
  private readonly primeObjectifService = inject(PrimeObjectifService);
  private readonly partenaireService = inject(PartenaireService);

  readonly items = signal<PrimeObjectifResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly totalElements = signal(0);

  readonly statutFilter = signal<StatutFilter>('all');

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    const statut = this.statutFilter();
    this.primeObjectifService
      .list(this.page(), PAGE_SIZE, statut !== 'all' ? { statut } : undefined)
      .subscribe({
        next: (result) => {
          this.items.set(result.content);
          this.totalPages.set(Math.max(1, result.totalPages));
          this.totalElements.set(result.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Impossible de charger les demandes de prime d\'objectif.');
          this.loading.set(false);
        },
      });
  }

  setStatutFilter(statut: StatutFilter): void {
    this.statutFilter.set(statut);
    this.page.set(0);
    this.fetch();
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.page.set(index);
    this.fetch();
  }

  // ================= Nouvelle demande (recherche partenaire + simulation) =================

  readonly requestModalVisible = signal(false);
  readonly partnerSearchQuery = signal('');
  readonly partnerSearchResults = signal<PartenaireResponse[]>([]);
  readonly partnerSearchLoading = signal(false);
  readonly selectedPartner = signal<PartenaireResponse | null>(null);
  readonly simulation = signal<PrimeObjectifSimulationResponse | null>(null);
  readonly simulationLoading = signal(false);
  readonly simulationError = signal<string | null>(null);
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  openRequestModal(): void {
    this.partnerSearchQuery.set('');
    this.partnerSearchResults.set([]);
    this.selectedPartner.set(null);
    this.simulation.set(null);
    this.simulationError.set(null);
    this.createError.set(null);
    this.requestModalVisible.set(true);
  }

  closeRequestModal(): void {
    this.requestModalVisible.set(false);
  }

  onPartnerSearchInput(value: string): void {
    this.partnerSearchQuery.set(value);
    this.selectedPartner.set(null);
    this.simulation.set(null);
    this.simulationError.set(null);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    const query = value.trim();
    if (!query) {
      this.partnerSearchResults.set([]);
      return;
    }
    this.searchDebounce = setTimeout(() => {
      this.partnerSearchLoading.set(true);
      this.partenaireService.list(0, 10, query, true).subscribe({
        next: (page) => {
          this.partnerSearchResults.set(page.content);
          this.partnerSearchLoading.set(false);
        },
        error: () => {
          this.partnerSearchLoading.set(false);
        },
      });
    }, 300);
  }

  selectPartner(partner: PartenaireResponse): void {
    this.selectedPartner.set(partner);
    this.partnerSearchResults.set([]);
    this.simulation.set(null);
    this.simulationError.set(null);
    this.simulationLoading.set(true);
    this.primeObjectifService.simulate(partner.id).subscribe({
      next: (sim) => {
        this.simulation.set(sim);
        this.simulationLoading.set(false);
      },
      error: (err: unknown) => {
        this.simulationLoading.set(false);
        this.simulationError.set(
          extractErrorMessage(err, 'Impossible de calculer la simulation pour ce partenaire.')
        );
      },
    });
  }

  confirmCreate(): void {
    const partner = this.selectedPartner();
    if (!partner) return;
    this.creating.set(true);
    this.createError.set(null);
    this.primeObjectifService.create(partner.id).subscribe({
      next: () => {
        this.creating.set(false);
        this.closeRequestModal();
        this.page.set(0);
        this.fetch();
      },
      error: (err: unknown) => {
        this.creating.set(false);
        this.createError.set(extractErrorMessage(err, "Impossible de créer la demande."));
      },
    });
  }

  // ================= Transitions de statut =================

  readonly statutTarget = signal<PrimeObjectifResponse | null>(null);
  readonly statutAction = signal<'VALIDEE' | 'PAYEE' | null>(null);
  readonly statutPending = signal(false);

  readonly rejectTarget = signal<PrimeObjectifResponse | null>(null);
  readonly rejectMotif = signal('');
  readonly rejectPending = signal(false);
  readonly rejectError = signal<string | null>(null);

  confirmValider(item: PrimeObjectifResponse): void {
    this.statutTarget.set(item);
    this.statutAction.set('VALIDEE');
  }

  confirmPayer(item: PrimeObjectifResponse): void {
    this.statutTarget.set(item);
    this.statutAction.set('PAYEE');
  }

  cancelStatutAction(): void {
    this.statutTarget.set(null);
    this.statutAction.set(null);
  }

  performStatutAction(): void {
    const item = this.statutTarget();
    const statut = this.statutAction();
    if (!item || !statut) return;
    this.statutPending.set(true);
    this.primeObjectifService.updateStatut(item.id, { statut }).subscribe({
      next: () => {
        this.statutPending.set(false);
        this.statutTarget.set(null);
        this.statutAction.set(null);
        this.fetch();
      },
      error: (err: unknown) => {
        this.statutPending.set(false);
        this.error.set(
          extractErrorMessage(err, "Impossible de mettre à jour le statut de cette demande.")
        );
        this.statutTarget.set(null);
        this.statutAction.set(null);
      },
    });
  }

  openReject(item: PrimeObjectifResponse): void {
    this.rejectTarget.set(item);
    this.rejectMotif.set('');
    this.rejectError.set(null);
  }

  cancelReject(): void {
    this.rejectTarget.set(null);
  }

  performReject(): void {
    const item = this.rejectTarget();
    if (!item) return;
    if (!this.rejectMotif().trim()) {
      this.rejectError.set('Le motif du rejet est obligatoire.');
      return;
    }
    this.rejectPending.set(true);
    this.rejectError.set(null);
    this.primeObjectifService
      .updateStatut(item.id, { statut: 'REJETEE', motifRejet: this.rejectMotif().trim() })
      .subscribe({
        next: () => {
          this.rejectPending.set(false);
          this.rejectTarget.set(null);
          this.fetch();
        },
        error: (err: unknown) => {
          this.rejectPending.set(false);
          this.rejectError.set(extractErrorMessage(err, 'Impossible de rejeter cette demande.'));
        },
      });
  }

  // ================= Présentation =================

  statutLabel(statut: PrimeObjectifStatut): string {
    switch (statut) {
      case 'EN_ATTENTE':
        return 'En attente';
      case 'VALIDEE':
        return 'Validée';
      case 'PAYEE':
        return 'Payée';
      case 'REJETEE':
        return 'Rejetée';
    }
  }

  statutVariant(statut: PrimeObjectifStatut): string {
    switch (statut) {
      case 'EN_ATTENTE':
        return 'warning';
      case 'VALIDEE':
        return 'info';
      case 'PAYEE':
        return 'success';
      case 'REJETEE':
        return 'danger';
    }
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }
}
