import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, FormControlDirective, FormDirective, FormLabelDirective, SpinnerComponent } from '@coreui/angular';

import { ContractService } from '../../../core/contracts/contract.service';
import {
  ContractFractionneSummaryResponse,
  EcheanceResponse,
  PaiementPieceJointeResponse,
  PeriodiciteEcheance,
} from '../../../core/contracts/models/contract.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type RetardFilter = 'all' | 'late';

const PERIODICITE_LABEL: Record<PeriodiciteEcheance, string> = {
  MENSUELLE: 'Mensuelle',
  TRIMESTRIELLE: 'Trimestrielle',
  SEMESTRIELLE: 'Semestrielle',
  ANNUELLE: 'Annuelle',
};

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-contrats-fractionnes',
  templateUrl: './contrats-fractionnes.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    AlertMessageComponent,
    ModalComponent,
  ],
})
export class ContratsFractionnesComponent {
  private readonly contractService = inject(ContractService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<ContractFractionneSummaryResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly retardFilter = signal<RetardFilter>('all');

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const filter = this.retardFilter();
    return this.items()
      .filter((item) => filter === 'all' || item.echeancesEnRetard > 0)
      .filter(
        (item) =>
          !query ||
          item.policyNumber.toLowerCase().includes(query) ||
          (item.insuredName ?? '').toLowerCase().includes(query),
      );
  });

  readonly totalEnRetard = computed(() => this.items().filter((item) => item.echeancesEnRetard > 0).length);

  readonly selectedContract = signal<ContractFractionneSummaryResponse | null>(null);
  readonly echeances = signal<EcheanceResponse[]>([]);
  readonly echeancesLoading = signal(false);
  readonly echeancesError = signal<string | null>(null);

  readonly payingEcheance = signal<EcheanceResponse | null>(null);
  readonly paying = signal(false);
  readonly payError = signal<string | null>(null);
  /** Justificatif choisi dans le formulaire de saisie du paiement — uploadé juste après la
   * création du paiement (POST .../paiements reste JSON, pas de multipart sur cet endpoint). */
  readonly paymentProofFile = signal<File | null>(null);

  readonly paymentForm = this.fb.nonNullable.group({
    datePaiement: [today()],
    moyenPaiement: [''],
    reference: [''],
  });

  // -- Justificatif d'un paiement déjà enregistré --
  readonly proofPanelPaiementId = signal<number | null>(null);
  readonly proofItems = signal<PaiementPieceJointeResponse[]>([]);
  readonly proofLoading = signal(false);
  readonly proofError = signal<string | null>(null);
  readonly proofUploading = signal(false);
  readonly viewingProofId = signal<number | null>(null);

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.contractService.listFractionnes().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les contrats fractionnés.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  setRetardFilter(filter: RetardFilter): void {
    this.retardFilter.set(filter);
  }

  openEcheancier(item: ContractFractionneSummaryResponse): void {
    this.selectedContract.set(item);
    this.payingEcheance.set(null);
    this.fetchEcheances(item.contractId);
  }

  closeEcheancier(): void {
    this.selectedContract.set(null);
    this.echeances.set([]);
    this.payingEcheance.set(null);
    this.closeProofPanel();
  }

  private fetchEcheances(contractId: number): void {
    this.echeancesLoading.set(true);
    this.echeancesError.set(null);
    this.contractService.getEcheances(contractId).subscribe({
      next: (lines) => {
        this.echeances.set(lines);
        this.echeancesLoading.set(false);
      },
      error: () => {
        this.echeancesError.set("Impossible de charger l'échéancier.");
        this.echeancesLoading.set(false);
      },
    });
  }

  openPaymentForm(echeance: EcheanceResponse): void {
    this.payError.set(null);
    this.paymentForm.reset({ datePaiement: today(), moyenPaiement: '', reference: '' });
    this.paymentProofFile.set(null);
    this.closeProofPanel();
    this.payingEcheance.set(echeance);
  }

  cancelPaymentForm(): void {
    this.payingEcheance.set(null);
    this.paymentProofFile.set(null);
  }

  onPaymentProofSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.paymentProofFile.set(input.files?.[0] ?? null);
  }

  submitPayment(): void {
    const echeance = this.payingEcheance();
    const contract = this.selectedContract();
    if (!echeance || !contract) return;
    const raw = this.paymentForm.getRawValue();
    if (!raw.moyenPaiement.trim()) {
      this.payError.set('Le moyen de paiement est requis.');
      return;
    }
    this.paying.set(true);
    this.payError.set(null);
    const proofFile = this.paymentProofFile();
    this.contractService
      .createPaiement(contract.contractId, {
        montant: echeance.montant,
        datePaiement: raw.datePaiement,
        moyenPaiement: raw.moyenPaiement.trim(),
        reference: raw.reference.trim() || undefined,
        echeanceId: echeance.id,
      })
      .subscribe({
        next: (paiement) => {
          this.fetchEcheances(contract.contractId);
          this.fetch();
          if (!proofFile) {
            this.paying.set(false);
            this.payingEcheance.set(null);
            return;
          }
          // Le paiement est déjà créé et l'échéance déjà marquée payée à ce stade — un échec
          // ici ne doit pas donner l'impression que le paiement lui-même a échoué.
          this.contractService.uploadPaiementPieceJointe(contract.contractId, paiement.id, proofFile).subscribe({
            next: () => {
              this.paying.set(false);
              this.paymentProofFile.set(null);
              this.payingEcheance.set(null);
            },
            error: () => {
              this.paying.set(false);
              this.paymentProofFile.set(null);
              this.payingEcheance.set(null);
              // Le paiement lui-même a réussi — on rouvre directement le panneau "Justificatif"
              // de ce paiement pour permettre de réessayer l'upload, plutôt qu'un message qui
              // laisserait croire que le paiement a échoué.
              this.proofPanelPaiementId.set(paiement.id);
              this.proofItems.set([]);
              this.proofError.set("Le paiement a bien été enregistré, mais le justificatif n'a pas pu être joint. Réessayez ci-dessous.");
            },
          });
        },
        error: () => {
          this.paying.set(false);
          this.payError.set("Impossible d'enregistrer le paiement. Réessayez.");
        },
      });
  }

  // -- Justificatif d'un paiement déjà enregistré --

  openProofPanel(echeance: EcheanceResponse): void {
    if (!echeance.paiementId) return;
    this.payingEcheance.set(null);
    this.proofError.set(null);
    this.proofPanelPaiementId.set(echeance.paiementId);
    this.fetchProofItems(echeance.paiementId);
  }

  closeProofPanel(): void {
    this.proofPanelPaiementId.set(null);
    this.proofItems.set([]);
    this.proofError.set(null);
  }

  private fetchProofItems(paiementId: number): void {
    const contract = this.selectedContract();
    if (!contract) return;
    this.proofLoading.set(true);
    this.proofError.set(null);
    this.contractService.listPaiementPiecesJointes(contract.contractId, paiementId).subscribe({
      next: (items) => {
        this.proofItems.set(items);
        this.proofLoading.set(false);
      },
      error: () => {
        this.proofError.set('Impossible de charger le justificatif.');
        this.proofLoading.set(false);
      },
    });
  }

  onProofSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const contract = this.selectedContract();
    const paiementId = this.proofPanelPaiementId();
    if (!file || !contract || !paiementId) return;
    this.proofUploading.set(true);
    this.proofError.set(null);
    this.contractService.uploadPaiementPieceJointe(contract.contractId, paiementId, file).subscribe({
      next: () => {
        this.proofUploading.set(false);
        input.value = '';
        this.fetchProofItems(paiementId);
      },
      error: () => {
        this.proofUploading.set(false);
        input.value = '';
        this.proofError.set('Impossible de joindre ce fichier.');
      },
    });
  }

  viewProof(piece: PaiementPieceJointeResponse): void {
    const contract = this.selectedContract();
    const paiementId = this.proofPanelPaiementId();
    if (!contract || !paiementId) return;
    this.viewingProofId.set(piece.id);
    this.proofError.set(null);
    this.contractService.downloadPaiementPieceJointe(contract.contractId, paiementId, piece.id).subscribe({
      next: (blob) => {
        this.viewingProofId.set(null);
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => {
        this.viewingProofId.set(null);
        this.proofError.set("Impossible d'afficher ce fichier.");
      },
    });
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  periodiciteLabel(p: PeriodiciteEcheance): string {
    return PERIODICITE_LABEL[p];
  }

  statutVariant(e: EcheanceResponse): string {
    if (e.statut === 'PAYEE') return 'success';
    if (e.enRetard) return 'danger';
    return 'warning';
  }

  statutLabel(e: EcheanceResponse): string {
    if (e.statut === 'PAYEE') return 'Payée';
    if (e.enRetard) return 'En retard';
    return 'En attente';
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string): string {
    return new Date(value).toLocaleString('fr-FR');
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }
}
