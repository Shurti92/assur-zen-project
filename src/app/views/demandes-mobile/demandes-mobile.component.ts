import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { MobileRequestService } from '../../core/mobile-admin/mobile-request.service';
import {
  CallbackRequest,
  CallbackSlot,
  ContractRequest,
  DeletionRequest,
} from '../../core/mobile-admin/models/mobile-request.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../shared/modal/modal.component';

type Tab = 'callbacks' | 'contracts' | 'deletions';
type Filter = 'pending' | 'done' | 'refused' | 'all';

const SLOT_LABEL: Record<CallbackSlot, string> = {
  ASAP: 'Dès que possible',
  MORNING: 'Matin',
  AFTERNOON: 'Après-midi',
  EVENING: 'Soir',
};

const ERROR_MESSAGES: Record<string, string> = {
  REQUEST_ALREADY_PROCESSED: 'Cette demande a déjà été traitée.',
  CONFIRMATION_REQUIRED: 'Une confirmation explicite est requise pour approuver la suppression.',
};

@Component({
  selector: 'app-demandes-mobile',
  templateUrl: './demandes-mobile.component.html',
  styleUrl: './demandes-mobile.component.scss',
  imports: [ButtonDirective, SpinnerComponent, FormsModule, RouterLink, AlertMessageComponent, ModalComponent],
})
export class DemandesMobileComponent {
  private readonly service = inject(MobileRequestService);

  readonly pending = this.service.pending;

  readonly tab = signal<Tab>('callbacks');
  readonly filter = signal<Filter>('pending');
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly callbacks = signal<CallbackRequest[]>([]);
  readonly contracts = signal<ContractRequest[]>([]);
  readonly deletions = signal<DeletionRequest[]>([]);

  // ---- Fenêtre de traitement (une seule ouverte à la fois) ----
  readonly selectedCallback = signal<CallbackRequest | null>(null);
  readonly selectedContract = signal<ContractRequest | null>(null);
  readonly selectedDeletion = signal<DeletionRequest | null>(null);
  readonly comment = signal('');
  readonly deletionConfirmed = signal(false);
  readonly busy = signal(false);
  readonly actionError = signal<string | null>(null);

  constructor() {
    this.service.refreshPending();
    this.load();
  }

  // ================= Chargement =================

  /** Le filtre est commun aux trois onglets mais les statuts diffèrent (RAPPELE/ANNULE vs TRAITEE/REFUSEE). */
  private statut(tab: Tab): string | undefined {
    switch (this.filter()) {
      case 'pending':
        return 'EN_ATTENTE';
      case 'done':
        return tab === 'callbacks' ? 'RAPPELE' : 'TRAITEE';
      case 'refused':
        return tab === 'callbacks' ? 'ANNULE' : 'REFUSEE';
      default:
        return undefined;
    }
  }

  load(): void {
    const tab = this.tab();
    this.loading.set(true);
    this.error.set(null);
    const fail = (err: unknown) => {
      this.error.set(this.messageOf(err, 'Impossible de charger les demandes.'));
      this.loading.set(false);
    };
    const statut = this.statut(tab);
    if (tab === 'callbacks') {
      this.service.listCallbacks(statut).subscribe({
        next: (list) => {
          this.callbacks.set(list);
          this.loading.set(false);
        },
        error: fail,
      });
    } else if (tab === 'contracts') {
      this.service.listContractRequests(statut).subscribe({
        next: (list) => {
          this.contracts.set(list);
          this.loading.set(false);
        },
        error: fail,
      });
    } else {
      this.service.listDeletionRequests(statut).subscribe({
        next: (list) => {
          this.deletions.set(list);
          this.loading.set(false);
        },
        error: fail,
      });
    }
  }

  refresh(): void {
    this.service.refreshPending();
    this.load();
  }

  setTab(tab: Tab): void {
    if (this.tab() === tab) return;
    this.tab.set(tab);
    this.load();
  }

  onFilterChange(value: string): void {
    this.filter.set(value as Filter);
    this.load();
  }

  // ================= Ouverture / fermeture =================

  private resetModal(): void {
    this.comment.set('');
    this.deletionConfirmed.set(false);
    this.actionError.set(null);
    this.busy.set(false);
  }

  openCallback(item: CallbackRequest): void {
    this.resetModal();
    this.comment.set(item.statut === 'EN_ATTENTE' ? '' : (item.comment ?? ''));
    this.selectedCallback.set(item);
  }

  openContract(item: ContractRequest): void {
    this.resetModal();
    this.comment.set(item.statut === 'EN_ATTENTE' ? '' : (item.comment ?? ''));
    this.selectedContract.set(item);
  }

  openDeletion(item: DeletionRequest): void {
    this.resetModal();
    this.comment.set(item.statut === 'EN_ATTENTE' ? '' : (item.comment ?? ''));
    this.selectedDeletion.set(item);
  }

  closeModal(): void {
    this.selectedCallback.set(null);
    this.selectedContract.set(null);
    this.selectedDeletion.set(null);
  }

  // ================= Traitement =================

  processCallback(statut: 'RAPPELE' | 'ANNULE'): void {
    const item = this.selectedCallback();
    if (!item) return;
    this.run(this.service.processCallback(item.id, statut, this.comment().trim()));
  }

  processContract(statut: 'TRAITEE' | 'REFUSEE'): void {
    const item = this.selectedContract();
    if (!item) return;
    this.run(this.service.processContractRequest(item.id, statut, this.comment().trim()));
  }

  processDeletion(decision: 'APPROVE' | 'REJECT'): void {
    const item = this.selectedDeletion();
    if (!item) return;
    // Garde-fou : l'approbation anonymise le compte de façon irréversible.
    if (decision === 'APPROVE' && !this.deletionConfirmed()) return;
    this.run(this.service.processDeletion(item.id, decision, this.comment().trim()));
  }

  private run(request: ReturnType<MobileRequestService['processCallback']>): void {
    this.busy.set(true);
    this.actionError.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.closeModal();
        this.refresh();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(this.messageOf(err, 'Impossible de traiter cette demande.'));
      },
    });
  }

  // ================= Affichage =================

  slotLabel(slot: CallbackSlot): string {
    return SLOT_LABEL[slot] ?? slot;
  }

  statusLabel(statut: string): string {
    return (
      { EN_ATTENTE: 'En attente', RAPPELE: 'Rappelé', ANNULE: 'Annulé', TRAITEE: 'Traitée', REFUSEE: 'Refusée' }[statut] ??
      statut
    );
  }

  statusVariant(statut: string): string {
    if (statut === 'EN_ATTENTE') return 'warning';
    if (statut === 'RAPPELE' || statut === 'TRAITEE') return 'success';
    return 'neutral';
  }

  displayName(name?: string | null): string {
    return name?.trim() || '—';
  }

  formatDateTime(value?: string | null): string {
    if (!value) return '—';
    const date = new Date(value);
    return `${date.toLocaleDateString('fr-FR')} ${date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      const code = err.error?.code as string | undefined;
      if (code && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code];
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return fallback;
  }
}
