import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  ColComponent,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  RowComponent,
  SpinnerComponent,
} from '@coreui/angular';

import { ReclamationService } from '../../../../core/reclamations/reclamation.service';
import {
  ReclamationCanal,
  ReclamationCommentaireResponse,
  ReclamationPieceJointeResponse,
  ReclamationPriorite,
  ReclamationResponse,
  ReclamationStatistiquesResponse,
  ReclamationStatut,
  ReclamationType,
} from '../../../../core/reclamations/models/reclamation.model';
import { InsuredService } from '../../../../core/insured/insured.service';
import { InsuredResponse } from '../../../../core/insured/models/insured.model';
import { ContractService } from '../../../../core/contracts/contract.service';
import { ContractResponse } from '../../../../core/contracts/models/contract.model';
import { ProduitService } from '../../../../core/configuration/produit.service';
import { ProduitResponse } from '../../../../core/configuration/models/produit.model';
import { SinistreService } from '../../../../core/sinistres/sinistre.service';
import { SinistreResponse } from '../../../../core/sinistres/models/sinistre.model';
import { ProfileService } from '../../../../core/profile/profile.service';
import { ProfileRecord } from '../../../../core/profile/models/profile-details.model';
import { AlertMessageComponent } from '../../../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../../../shared/modal/modal.component';
import { ConfirmDialogComponent } from '../../../../shared/confirm-dialog/confirm-dialog.component';
import { AuditHistoryComponent } from '../../../../shared/audit-history/audit-history.component';
import {
  RECLAMATION_CANAL_LABEL,
  RECLAMATION_PRIORITE_LABEL,
  RECLAMATION_PRIORITE_VARIANT,
  RECLAMATION_STATUTS_TERMINAUX,
  RECLAMATION_STATUT_LABEL,
  RECLAMATION_STATUT_ORDER,
  RECLAMATION_STATUT_VARIANT,
  RECLAMATION_TYPE_LABEL,
} from './reclamation-status';

const COMMENTAIRE_MAX_LENGTH = 2000;
type StatutFilter = 'all' | ReclamationStatut;

interface AdvancedFilters {
  type: string;
  priorite: string;
  canal: string;
  gestionnaireId: string;
}

const EMPTY_ADVANCED_FILTERS: AdvancedFilters = { type: 'all', priorite: 'all', canal: 'all', gestionnaireId: 'all' };

/** Le backend renvoie déjà un message clair et actionnable pour le cas d'usage le plus courant
 * (409 "a des commentaires : clôturez-la plutôt") — on le relaie tel quel plutôt que de le
 * remplacer par un message générique. */
function extractErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { message?: string } | null;
    if (body?.message) return body.message;
  }
  return fallback;
}

const TYPE_OPTIONS: ReclamationType[] = ['SERVICE', 'PAIEMENT', 'SINISTRE', 'SOUSCRIPTION', 'DOCUMENT', 'RELATION_CLIENT'];
const PRIORITE_OPTIONS: ReclamationPriorite[] = ['FAIBLE', 'NORMALE', 'HAUTE', 'CRITIQUE'];
const CANAL_OPTIONS: ReclamationCanal[] = [
  'ENQUETE_SATISFACTION',
  'APPEL_TELEPHONIQUE',
  'EMAIL',
  'WHATSAPP',
  'AGENT',
  'COURTIER',
  'PORTAIL_CLIENT',
];

@Component({
  selector: 'app-reclamations',
  templateUrl: './reclamations.component.html',
  imports: [
    DecimalPipe,
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    RowComponent,
    ColComponent,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
    AuditHistoryComponent,
  ],
})
export class ReclamationsComponent {
  private readonly reclamationService = inject(ReclamationService);
  private readonly insuredService = inject(InsuredService);
  private readonly contractService = inject(ContractService);
  private readonly produitService = inject(ProduitService);
  private readonly sinistreService = inject(SinistreService);
  private readonly profileService = inject(ProfileService);
  private readonly fb = inject(FormBuilder);

  readonly statutOrder = RECLAMATION_STATUT_ORDER;
  readonly typeLabel = RECLAMATION_TYPE_LABEL;
  readonly prioriteLabel = RECLAMATION_PRIORITE_LABEL;
  readonly canalLabel = RECLAMATION_CANAL_LABEL;
  readonly typeOptions = TYPE_OPTIONS;
  readonly prioriteOptions = PRIORITE_OPTIONS;
  readonly canalOptions = CANAL_OPTIONS;
  readonly commentaireMaxLength = COMMENTAIRE_MAX_LENGTH;

  readonly pageSize = 20;

  readonly items = signal<ReclamationResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly statutFilter = signal<StatutFilter>('all');
  readonly pageIndex = signal(0);

  readonly stats = signal<ReclamationStatistiquesResponse | null>(null);

  readonly advancedFiltersOpen = signal(false);
  readonly advancedFilters = signal<AdvancedFilters>({ ...EMPTY_ADVANCED_FILTERS });

  readonly insureds = signal<InsuredResponse[]>([]);
  readonly produits = signal<ProduitResponse[]>([]);
  readonly sinistres = signal<SinistreResponse[]>([]);
  readonly gestionnaires = signal<ProfileRecord[]>([]);
  readonly contractsForInsured = signal<ContractResponse[]>([]);

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  // Create -------------------------------------------------------------------

  readonly createVisible = signal(false);
  readonly createSaving = signal(false);
  readonly createError = signal<string | null>(null);
  readonly createForm = this.fb.nonNullable.group({
    insuredId: 0,
    contratId: 0,
    produitId: 0,
    sinistreId: 0,
    canal: 'AGENT' as ReclamationCanal,
    type: 'SERVICE' as ReclamationType,
    priorite: 'NORMALE' as ReclamationPriorite,
    description: '',
  });

  // Detail ---------------------------------------------------------------

  readonly detailVisible = signal(false);
  readonly selected = signal<ReclamationResponse | null>(null);

  readonly commentaires = signal<ReclamationCommentaireResponse[]>([]);
  readonly commentairesLoading = signal(true);
  readonly commentairesError = signal<string | null>(null);
  readonly commentaireForm = this.fb.nonNullable.group({ contenu: '' });
  readonly commentaireSaving = signal(false);

  readonly pieces = signal<ReclamationPieceJointeResponse[]>([]);
  readonly piecesLoading = signal(true);
  readonly pieceError = signal<string | null>(null);
  readonly pieceUploading = signal(false);
  readonly viewingPieceId = signal<number | null>(null);

  readonly statutForm = this.fb.nonNullable.group({ statut: 'NOUVELLE' as ReclamationStatut, commentaire: '' });
  readonly statutSaving = signal(false);
  readonly statutError = signal<string | null>(null);

  readonly affectationForm = this.fb.nonNullable.group({ gestionnaireId: 0, service: '' });
  readonly affectationSaving = signal(false);
  readonly affectationError = signal<string | null>(null);

  readonly clotureForm = this.fb.nonNullable.group({ motif: '' });
  readonly clotureSaving = signal(false);
  readonly clotureError = signal<string | null>(null);

  /** Suppression physique — réservée à la purge d'un test/doublon (voir DELETE /reclamations/{id}
   * côté backend, rejeté en 409 dès qu'il y a un commentaire, une pièce jointe, ou une origine NPS auto). */
  readonly deleteConfirmVisible = signal(false);
  readonly deleting = signal(false);
  readonly deleteError = signal<string | null>(null);

  readonly isTerminal = computed(() => {
    const item = this.selected();
    return !!item && RECLAMATION_STATUTS_TERMINAUX.includes(item.statut);
  });

  // Filtering / pagination -------------------------------------------------------------------

  readonly activeAdvancedFilterCount = computed(() => {
    const f = this.advancedFilters();
    return Object.values(f).filter((v) => v !== 'all').length;
  });

  readonly scopedItems = computed(() => {
    const all = this.items();
    const statut = this.statutFilter();
    const f = this.advancedFilters();
    return all.filter((r) => {
      if (statut !== 'all' && r.statut !== statut) return false;
      if (f.type !== 'all' && r.type !== f.type) return false;
      if (f.priorite !== 'all' && r.priorite !== f.priorite) return false;
      if (f.canal !== 'all' && r.canal !== f.canal) return false;
      if (f.gestionnaireId !== 'all' && String(r.gestionnaireId ?? '') !== f.gestionnaireId) return false;
      return true;
    });
  });

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const scoped = this.scopedItems();
    if (!query) return scoped;
    return scoped.filter((r) =>
      `${r.numero} ${r.insuredName} ${r.contratPolicyNumber ?? ''} ${r.description}`.toLowerCase().includes(query),
    );
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filteredItems().length / this.pageSize)));

  readonly pagedItems = computed(() => {
    const start = this.pageIndex() * this.pageSize;
    return this.filteredItems().slice(start, start + this.pageSize);
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.searchDebounce) clearTimeout(this.searchDebounce);
    });
    this.fetch();
    this.fetchStats();
    this.produitService.list(0, 200, undefined, true).subscribe({
      next: (page) => this.produits.set(page.content),
      error: () => undefined,
    });
    this.sinistreService.list(0, 500).subscribe({
      next: (page) => this.sinistres.set(page.content),
      error: () => undefined,
    });
    this.profileService.list().subscribe({
      next: (profiles) => this.gestionnaires.set(profiles),
      error: () => undefined,
    });
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.pageIndex.set(0);
    this.reclamationService.listAll().subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les réclamations.');
        this.loading.set(false);
      },
    });
  }

  private fetchStats(): void {
    this.reclamationService.statistiques().subscribe({
      next: (stats) => this.stats.set(stats),
      error: () => undefined,
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.pageIndex.set(0), 400);
  }

  setStatutFilter(value: string): void {
    this.statutFilter.set(value as StatutFilter);
    this.pageIndex.set(0);
  }

  toggleAdvancedFilters(): void {
    this.advancedFiltersOpen.set(!this.advancedFiltersOpen());
  }

  closeAdvancedFilters(): void {
    this.advancedFiltersOpen.set(false);
  }

  updateAdvancedFilter<K extends keyof AdvancedFilters>(key: K, value: string): void {
    this.advancedFilters.set({ ...this.advancedFilters(), [key]: value });
    this.pageIndex.set(0);
  }

  resetAdvancedFilters(): void {
    this.advancedFilters.set({ ...EMPTY_ADVANCED_FILTERS });
    this.pageIndex.set(0);
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
  }

  // Create -------------------------------------------------------------------

  openCreate(): void {
    this.createForm.reset({
      insuredId: 0,
      contratId: 0,
      produitId: 0,
      sinistreId: 0,
      canal: 'AGENT',
      type: 'SERVICE',
      priorite: 'NORMALE',
      description: '',
    });
    this.contractsForInsured.set([]);
    if (this.insureds().length === 0) {
      this.insuredService.list(0, 500).subscribe({
        next: (page) => this.insureds.set(page.content),
        error: () => undefined,
      });
    }
    this.createError.set(null);
    this.createVisible.set(true);
  }

  onCreateInsuredChange(value: string): void {
    const insuredId = Number(value);
    this.createForm.patchValue({ insuredId, contratId: 0 });
    this.contractsForInsured.set([]);
    if (!insuredId) return;
    this.contractService.list(0, 200, { insuredId }).subscribe({
      next: (page) => this.contractsForInsured.set(page.content),
      error: () => undefined,
    });
  }

  submitCreate(): void {
    const raw = this.createForm.getRawValue();
    if (!raw.insuredId) {
      this.createError.set("Sélectionnez l'assuré concerné.");
      return;
    }
    if (!raw.description.trim()) {
      this.createError.set('La description de la réclamation est requise.');
      return;
    }
    this.createSaving.set(true);
    this.createError.set(null);
    this.reclamationService
      .create({
        insuredId: Number(raw.insuredId),
        contratId: raw.contratId ? Number(raw.contratId) : undefined,
        produitId: raw.produitId ? Number(raw.produitId) : undefined,
        sinistreId: raw.sinistreId ? Number(raw.sinistreId) : undefined,
        canal: raw.canal,
        type: raw.type,
        priorite: raw.priorite,
        description: raw.description.trim(),
      })
      .subscribe({
        next: () => {
          this.createSaving.set(false);
          this.createVisible.set(false);
          this.fetch();
          this.fetchStats();
        },
        error: () => {
          this.createSaving.set(false);
          this.createError.set("Impossible d'enregistrer cette réclamation. Réessayez.");
        },
      });
  }

  // Detail -------------------------------------------------------------------

  openDetail(item: ReclamationResponse): void {
    this.selected.set(item);
    this.statutForm.reset({ statut: item.statut, commentaire: '' });
    this.statutError.set(null);
    this.affectationForm.reset({ gestionnaireId: item.gestionnaireId ?? 0, service: item.service ?? '' });
    this.affectationError.set(null);
    this.clotureForm.reset({ motif: '' });
    this.clotureError.set(null);
    this.deleteError.set(null);
    this.commentaireForm.reset({ contenu: '' });
    this.detailVisible.set(true);
    this.fetchCommentaires(item.id);
    this.fetchPieces(item.id);
  }

  private fetchCommentaires(id: number): void {
    this.commentairesLoading.set(true);
    this.commentairesError.set(null);
    this.reclamationService.listCommentaires(id).subscribe({
      next: (list) => {
        this.commentaires.set(list);
        this.commentairesLoading.set(false);
      },
      error: () => {
        this.commentairesError.set('Impossible de charger les commentaires.');
        this.commentairesLoading.set(false);
      },
    });
  }

  submitCommentaire(): void {
    const item = this.selected();
    const contenu = this.commentaireForm.getRawValue().contenu.trim();
    if (!item || !contenu) return;
    this.commentaireSaving.set(true);
    this.reclamationService.addCommentaire(item.id, { contenu }).subscribe({
      next: () => {
        this.commentaireSaving.set(false);
        this.commentaireForm.reset({ contenu: '' });
        this.fetchCommentaires(item.id);
      },
      error: () => {
        this.commentaireSaving.set(false);
        this.commentairesError.set("Impossible d'ajouter ce commentaire.");
      },
    });
  }

  private fetchPieces(id: number): void {
    this.piecesLoading.set(true);
    this.pieceError.set(null);
    this.reclamationService.listPiecesJointes(id).subscribe({
      next: (list) => {
        this.pieces.set(list);
        this.piecesLoading.set(false);
      },
      error: () => {
        this.pieceError.set('Impossible de charger les pièces jointes.');
        this.piecesLoading.set(false);
      },
    });
  }

  onPieceSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const item = this.selected();
    if (!file || !item) return;
    this.pieceUploading.set(true);
    this.pieceError.set(null);
    this.reclamationService.uploadPieceJointe(item.id, file).subscribe({
      next: () => {
        this.pieceUploading.set(false);
        input.value = '';
        this.fetchPieces(item.id);
      },
      error: () => {
        this.pieceUploading.set(false);
        input.value = '';
        this.pieceError.set('Impossible de joindre ce fichier.');
      },
    });
  }

  viewPiece(piece: ReclamationPieceJointeResponse): void {
    const item = this.selected();
    if (!item) return;
    this.viewingPieceId.set(piece.id);
    this.pieceError.set(null);
    this.reclamationService.downloadPieceJointe(item.id, piece.id).subscribe({
      next: (blob) => {
        this.viewingPieceId.set(null);
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => {
        this.viewingPieceId.set(null);
        this.pieceError.set("Impossible d'ouvrir ce fichier.");
      },
    });
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  submitStatut(): void {
    const item = this.selected();
    if (!item) return;
    const raw = this.statutForm.getRawValue();
    this.statutSaving.set(true);
    this.statutError.set(null);
    this.reclamationService
      .changerStatut(item.id, { statut: raw.statut, commentaire: raw.commentaire.trim() || undefined })
      .subscribe({
        next: (updated) => {
          this.statutSaving.set(false);
          this.selected.set(updated);
          this.applyUpdate(updated);
          this.fetchStats();
        },
        error: () => {
          this.statutSaving.set(false);
          this.statutError.set('Impossible de mettre à jour le statut.');
        },
      });
  }

  submitAffectation(): void {
    const item = this.selected();
    if (!item) return;
    const raw = this.affectationForm.getRawValue();
    if (!raw.gestionnaireId) {
      this.affectationError.set('Sélectionnez un gestionnaire.');
      return;
    }
    this.affectationSaving.set(true);
    this.affectationError.set(null);
    this.reclamationService
      .affecter(item.id, { gestionnaireId: Number(raw.gestionnaireId), service: raw.service.trim() || undefined })
      .subscribe({
        next: (updated) => {
          this.affectationSaving.set(false);
          this.selected.set(updated);
          this.applyUpdate(updated);
        },
        error: () => {
          this.affectationSaving.set(false);
          this.affectationError.set("Impossible d'affecter ce dossier.");
        },
      });
  }

  submitCloture(): void {
    const item = this.selected();
    const motif = this.clotureForm.getRawValue().motif.trim();
    if (!item) return;
    if (!motif) {
      this.clotureError.set('Le motif de clôture est requis.');
      return;
    }
    this.clotureSaving.set(true);
    this.clotureError.set(null);
    this.reclamationService.cloturer(item.id, { motif }).subscribe({
      next: (updated) => {
        this.clotureSaving.set(false);
        this.selected.set(updated);
        this.applyUpdate(updated);
        this.fetchStats();
      },
      error: () => {
        this.clotureSaving.set(false);
        this.clotureError.set('Impossible de clôturer ce dossier.');
      },
    });
  }

  askDelete(): void {
    this.deleteError.set(null);
    this.deleteConfirmVisible.set(true);
  }

  cancelDelete(): void {
    this.deleteConfirmVisible.set(false);
  }

  confirmDelete(): void {
    const item = this.selected();
    if (!item) return;
    this.deleting.set(true);
    this.reclamationService.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteConfirmVisible.set(false);
        this.detailVisible.set(false);
        this.items.set(this.items().filter((r) => r.id !== item.id));
        this.fetchStats();
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.deleteConfirmVisible.set(false);
        this.deleteError.set(extractErrorMessage(err, 'Impossible de supprimer cette réclamation.'));
      },
    });
  }

  private applyUpdate(updated: ReclamationResponse): void {
    this.items.set(this.items().map((r) => (r.id === updated.id ? updated : r)));
  }

  gestionnaireName(profile: ProfileRecord): string {
    return `${profile.firstname} ${profile.lastname}`.trim();
  }

  statutLabel(statut: ReclamationStatut): string {
    return RECLAMATION_STATUT_LABEL[statut];
  }

  statutVariant(statut: ReclamationStatut): string {
    return RECLAMATION_STATUT_VARIANT[statut];
  }

  prioriteVariant(priorite: ReclamationPriorite): string {
    return RECLAMATION_PRIORITE_VARIANT[priorite];
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }
}
