import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, FormControlDirective, FormDirective, FormLabelDirective, FormSelectDirective, SpinnerComponent } from '@coreui/angular';

import { FideliteService } from '../../../core/fidelite/fidelite.service';
import {
  CompteFideliteResponse,
  EchangeStatut,
  FidelisationHistoriqueResponse,
  FidelisationMouvementType,
  RecompenseEchangeResponse,
  RecompenseResponse,
} from '../../../core/fidelite/models/fidelite.model';
import { InsuredService } from '../../../core/insured/insured.service';
import { InsuredResponse } from '../../../core/insured/models/insured.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';

const MOUVEMENT_LABEL: Record<FidelisationMouvementType, string> = {
  GAIN: 'Gain',
  UTILISATION: 'Utilisation',
  EXPIRATION: 'Expiration',
  AJUSTEMENT: 'Ajustement',
  SCORE_RECALCULE: 'Score recalculé',
  ENTREE_VIP: 'Entrée VIP',
  CHANGEMENT_NIVEAU: 'Changement de niveau',
  SORTIE_VIP: 'Sortie VIP',
  AVANTAGE_ATTRIBUE: 'Avantage attribué',
};

const MOUVEMENT_VARIANT: Record<FidelisationMouvementType, string> = {
  GAIN: 'success',
  UTILISATION: 'info',
  EXPIRATION: 'danger',
  AJUSTEMENT: 'warning',
  SCORE_RECALCULE: 'neutral',
  ENTREE_VIP: 'success',
  CHANGEMENT_NIVEAU: 'info',
  SORTIE_VIP: 'warning',
  AVANTAGE_ATTRIBUE: 'success',
};

const ECHANGE_STATUT_LABEL: Record<EchangeStatut, string> = {
  EN_ATTENTE: 'En attente',
  VALIDE: 'Validé',
  LIVREE: 'Livrée',
  ANNULEE: 'Annulée',
};

const ECHANGE_STATUT_VARIANT: Record<EchangeStatut, string> = {
  EN_ATTENTE: 'warning',
  VALIDE: 'info',
  LIVREE: 'success',
  ANNULEE: 'danger',
};

@Component({
  selector: 'app-programmes',
  templateUrl: './programmes.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    AlertMessageComponent,
  ],
})
export class ProgrammesComponent {
  private readonly fideliteService = inject(FideliteService);
  private readonly insuredService = inject(InsuredService);
  private readonly fb = inject(FormBuilder);

  readonly mouvementLabel = MOUVEMENT_LABEL;
  readonly echangeStatutLabel = ECHANGE_STATUT_LABEL;

  readonly insureds = signal<InsuredResponse[]>([]);
  readonly insuredsLoading = signal(true);
  readonly selectedInsuredId = signal<number | null>(null);

  readonly compte = signal<CompteFideliteResponse | null>(null);
  readonly compteLoading = signal(false);
  readonly compteNotFound = signal(false);
  readonly compteError = signal<string | null>(null);
  readonly adhering = signal(false);

  readonly historique = signal<FidelisationHistoriqueResponse[]>([]);
  readonly historiqueLoading = signal(false);
  readonly historiqueError = signal<string | null>(null);

  readonly ajustementForm = this.fb.nonNullable.group({ points: 0, motif: '' });
  readonly ajustementSaving = signal(false);
  readonly ajustementError = signal<string | null>(null);

  readonly recompenses = signal<RecompenseResponse[]>([]);
  readonly echangeForm = this.fb.nonNullable.group({ recompenseId: 0 });
  readonly echangeSaving = signal(false);
  readonly echangeError = signal<string | null>(null);
  readonly lastEchange = signal<RecompenseEchangeResponse | null>(null);
  readonly echangeTraiting = signal(false);

  readonly selectedInsured = computed(() => {
    const id = this.selectedInsuredId();
    return id ? (this.insureds().find((i) => i.id === id) ?? null) : null;
  });

  readonly hasAccount = computed(() => this.compte() !== null);

  constructor() {
    this.insuredService.list(0, 500).subscribe({
      next: (page) => {
        this.insureds.set(page.content);
        this.insuredsLoading.set(false);
      },
      error: () => this.insuredsLoading.set(false),
    });
    this.fideliteService.listRecompenses().subscribe({
      next: (list) => this.recompenses.set(list.filter((r) => r.active)),
      error: () => undefined,
    });
  }

  onInsuredChange(value: string): void {
    const insuredId = Number(value);
    this.selectedInsuredId.set(insuredId || null);
    this.compte.set(null);
    this.compteNotFound.set(false);
    this.compteError.set(null);
    this.historique.set([]);
    this.lastEchange.set(null);
    this.ajustementForm.reset({ points: 0, motif: '' });
    this.echangeForm.reset({ recompenseId: 0 });
    if (!insuredId) return;
    this.fetchCompte(insuredId);
  }

  private fetchCompte(insuredId: number): void {
    this.compteLoading.set(true);
    this.compteError.set(null);
    this.fideliteService.getCompte(insuredId).subscribe({
      next: (compte) => {
        this.compte.set(compte);
        this.compteNotFound.set(false);
        this.compteLoading.set(false);
        this.fetchHistorique(insuredId);
      },
      error: (err: unknown) => {
        this.compteLoading.set(false);
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.compteNotFound.set(true);
        } else {
          this.compteError.set('Impossible de charger le compte fidélité.');
        }
      },
    });
  }

  private fetchHistorique(insuredId: number): void {
    this.historiqueLoading.set(true);
    this.historiqueError.set(null);
    this.fideliteService.getHistorique(insuredId).subscribe({
      next: (list) => {
        this.historique.set([...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
        this.historiqueLoading.set(false);
      },
      error: () => {
        this.historiqueError.set("Impossible de charger l'historique de points.");
        this.historiqueLoading.set(false);
      },
    });
  }

  adherer(): void {
    const insuredId = this.selectedInsuredId();
    if (!insuredId) return;
    this.adhering.set(true);
    this.compteError.set(null);
    this.fideliteService.adherer(insuredId).subscribe({
      next: (compte) => {
        this.adhering.set(false);
        this.compte.set(compte);
        this.compteNotFound.set(false);
        this.fetchHistorique(insuredId);
      },
      error: () => {
        this.adhering.set(false);
        this.compteError.set("Impossible d'adhérer cet assuré au programme de fidélité.");
      },
    });
  }

  submitAjustement(): void {
    const insuredId = this.selectedInsuredId();
    const raw = this.ajustementForm.getRawValue();
    if (!insuredId) return;
    if (!raw.points) {
      this.ajustementError.set('Le nombre de points est requis (positif ou négatif).');
      return;
    }
    if (!raw.motif.trim()) {
      this.ajustementError.set('Le motif est requis.');
      return;
    }
    this.ajustementSaving.set(true);
    this.ajustementError.set(null);
    this.fideliteService.ajuster({ insuredId, points: Number(raw.points), motif: raw.motif.trim() }).subscribe({
      next: () => {
        this.ajustementSaving.set(false);
        this.ajustementForm.reset({ points: 0, motif: '' });
        this.fetchCompte(insuredId);
      },
      error: () => {
        this.ajustementSaving.set(false);
        this.ajustementError.set("Impossible d'enregistrer cet ajustement.");
      },
    });
  }

  submitEchange(): void {
    const insuredId = this.selectedInsuredId();
    const raw = this.echangeForm.getRawValue();
    if (!insuredId) return;
    if (!raw.recompenseId) {
      this.echangeError.set('Sélectionnez une récompense.');
      return;
    }
    this.echangeSaving.set(true);
    this.echangeError.set(null);
    this.fideliteService.echangerRecompense({ insuredId, recompenseId: Number(raw.recompenseId) }).subscribe({
      next: (echange) => {
        this.echangeSaving.set(false);
        this.lastEchange.set(echange);
        this.echangeForm.reset({ recompenseId: 0 });
        this.fetchCompte(insuredId);
      },
      error: () => {
        this.echangeSaving.set(false);
        this.echangeError.set("Impossible d'enregistrer cet échange — vérifiez que le solde de points est suffisant.");
      },
    });
  }

  traiterEchange(statut: EchangeStatut): void {
    const echange = this.lastEchange();
    if (!echange) return;
    this.echangeTraiting.set(true);
    this.fideliteService.traiterEchange(echange.id, statut).subscribe({
      next: (updated) => {
        this.echangeTraiting.set(false);
        this.lastEchange.set(updated);
      },
      error: () => {
        this.echangeTraiting.set(false);
        this.echangeError.set("Impossible de mettre à jour cet échange.");
      },
    });
  }

  mouvementVariant(type: FidelisationMouvementType): string {
    return MOUVEMENT_VARIANT[type];
  }

  echangeStatutVariant(statut: EchangeStatut): string {
    return ECHANGE_STATUT_VARIANT[statut];
  }

  insuredLabel(insured: InsuredResponse): string {
    return `${insured.nom} ${insured.prenoms}`;
  }

  formatPoints(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} pts`;
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }

  formatDateTime(value: string): string {
    return new Date(value).toLocaleString('fr-FR');
  }
}
