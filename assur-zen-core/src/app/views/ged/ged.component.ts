import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  DropdownComponent,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { GedDocumentService } from '../../core/ged/ged-document.service';
import { GedDocumentTypeService } from '../../core/ged/ged-document-type.service';
import { GedDocumentCategoryService } from '../../core/ged/ged-document-category.service';
import {
  GedConfidentialityLevel,
  GedDocumentLinkResponse,
  GedDocumentResponse,
  GedDocumentStatus,
  GedDocumentVersionResponse,
  GedEntityType,
} from '../../core/ged/models/ged-document.model';
import { GedDocumentTypeResponse } from '../../core/ged/models/ged-document-type.model';
import { GedDocumentCategoryResponse } from '../../core/ged/models/ged-document-category.model';
import { InsuredService } from '../../core/insured/insured.service';
import { InsuredResponse } from '../../core/insured/models/insured.model';
import { ContractService } from '../../core/contracts/contract.service';
import { FleetService } from '../../core/fleet/fleet.service';
import { SinistreService } from '../../core/sinistres/sinistre.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { AuthService } from '../../core/auth/auth.service';

type View = 'documents' | 'types' | 'categories';
type RefActifFilter = 'all' | 'active' | 'inactive';
const PAGE_SIZE = 20;

const STATUS_LABEL: Record<GedDocumentStatus, string> = {
  UPLOADED: 'Déposé',
  VALIDATED: 'Validé',
  REJECTED: 'Rejeté',
  ARCHIVED: 'Archivé',
  DELETED: 'Supprimé',
};

const STATUS_VARIANT: Record<GedDocumentStatus, string> = {
  UPLOADED: 'warning',
  VALIDATED: 'success',
  REJECTED: 'danger',
  ARCHIVED: 'neutral',
  DELETED: 'neutral',
};

const CONFIDENTIALITY_LABEL: Record<GedConfidentialityLevel, string> = {
  PUBLIC: 'Public',
  INTERNAL: 'Interne',
  CONFIDENTIAL: 'Confidentiel',
  RESTRICTED: 'Restreint',
};

const ENTITY_TYPE_LABEL: Record<string, string> = {
  CLIENT: 'Client',
  CONTRACT: 'Contrat',
  VEHICLE: 'Véhicule',
  FLEET: 'Flotte',
  CLAIM: 'Sinistre',
};

/** Options du sélecteur en boutons icônes "Type d'entité" de la modale de dépôt. */
const ENTITY_TYPE_OPTIONS: { value: GedEntityType; label: string; icon: string }[] = [
  { value: 'CLIENT', label: 'Client', icon: 'cilUser' },
  { value: 'CONTRACT', label: 'Contrat', icon: 'cilDescription' },
  { value: 'VEHICLE', label: 'Véhicule', icon: 'cilCarAlt' },
  { value: 'FLEET', label: 'Flotte', icon: 'grid' },
  { value: 'CLAIM', label: 'Sinistre', icon: 'triangleAlert' },
];

/** Options des pastilles "Niveau de confidentialité" — mappées sur les variantes sémantiques
 * déjà utilisées par .dl-chip (success/info/warning/danger) pour rester lisibles d'un coup d'œil. */
const CONFIDENTIALITY_OPTIONS: { value: GedConfidentialityLevel; label: string; variant: string }[] = [
  { value: 'PUBLIC', label: 'Public', variant: 'success' },
  { value: 'INTERNAL', label: 'Interne', variant: 'info' },
  { value: 'CONFIDENTIAL', label: 'Confidentiel', variant: 'warning' },
  { value: 'RESTRICTED', label: 'Restreint', variant: 'danger' },
];

@Component({
  selector: 'app-ged',
  templateUrl: './ged.component.html',
  imports: [
    ButtonDirective,
    DropdownComponent,
    DropdownItemDirective,
    DropdownMenuDirective,
    DropdownToggleDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    FormCheckInputDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class GedComponent {
  private readonly documentService = inject(GedDocumentService);
  private readonly typeService = inject(GedDocumentTypeService);
  private readonly categoryService = inject(GedDocumentCategoryService);
  private readonly insuredService = inject(InsuredService);
  private readonly contractService = inject(ContractService);
  private readonly fleetService = inject(FleetService);
  private readonly sinistreService = inject(SinistreService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  /** Module réservé ADMIN côté backend depuis le durcissement IDOR du 2026-09-12 (voir
   * docs/api-contracts/securisation-idor-2026-09-12.md §4) — lecture/téléchargement des documents
   * en 403 pour tout le reste. Masquage d'affichage uniquement, le vrai rempart reste ce 403
   * backend (même choix que Tarifs/Livraisons dans VignettesMotoComponent). */
  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');

  readonly view = signal<View>('documents');
  readonly statusLabel = STATUS_LABEL;
  readonly confidentialityLabel = CONFIDENTIALITY_LABEL;
  readonly entityTypeLabel = ENTITY_TYPE_LABEL;
  readonly entityTypeOptions = ENTITY_TYPE_OPTIONS;
  readonly confidentialityOptions = CONFIDENTIALITY_OPTIONS;

  setView(view: View): void {
    this.view.set(view);
    if (view === 'types' && this.types().length === 0) this.fetchTypes();
    if (view === 'categories' && this.categories().length === 0) this.fetchCategories();
  }

  constructor() {
    if (!this.isAdmin()) return;
    this.fetchDocuments();
    this.fetchTypes();
    this.fetchCategories();
  }

  statusVariant(status: GedDocumentStatus): string {
    return STATUS_VARIANT[status];
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  // ================================================================ RÉFÉRENTIELS (types & catégories) ================================================================

  readonly types = signal<GedDocumentTypeResponse[]>([]);
  readonly typesLoading = signal(false);
  readonly typesError = signal<string | null>(null);
  readonly typesSearch = signal('');
  readonly typesActifFilter = signal<RefActifFilter>('all');

  readonly filteredTypes = computed(() => {
    const query = this.typesSearch().trim().toLowerCase();
    const actif = this.typesActifFilter();
    return this.types()
      .filter((t) => actif === 'all' || t.actif === (actif === 'active'))
      .filter((t) => !query || `${t.code} ${t.nom}`.toLowerCase().includes(query))
      .sort((a, b) => (a.ordre ?? 0) - (b.ordre ?? 0));
  });

  fetchTypes(): void {
    this.typesLoading.set(true);
    this.typesError.set(null);
    this.typeService.list(0, 200).subscribe({
      next: (page) => {
        this.types.set(page.content);
        this.typesLoading.set(false);
      },
      error: () => {
        this.typesError.set('Impossible de charger les types de document.');
        this.typesLoading.set(false);
      },
    });
  }

  readonly typeFormVisible = signal(false);
  readonly typeFormSaving = signal(false);
  readonly typeFormError = signal<string | null>(null);
  readonly editingTypeId = signal<number | null>(null);
  readonly typeForm = this.fb.nonNullable.group({ code: [''], nom: [''], description: [''], ordre: [0], actif: [true] });

  openCreateType(): void {
    this.editingTypeId.set(null);
    this.typeFormError.set(null);
    this.typeForm.reset({ code: '', nom: '', description: '', ordre: 0, actif: true });
    this.typeFormVisible.set(true);
  }

  openEditType(item: GedDocumentTypeResponse): void {
    this.editingTypeId.set(item.id);
    this.typeFormError.set(null);
    this.typeForm.reset({ code: item.code, nom: item.nom, description: item.description ?? '', ordre: item.ordre ?? 0, actif: item.actif });
    this.typeFormVisible.set(true);
  }

  saveType(): void {
    const raw = this.typeForm.getRawValue();
    if (!raw.code.trim() || !raw.nom.trim()) {
      this.typeFormError.set('Le code et le nom sont requis.');
      return;
    }
    this.typeFormSaving.set(true);
    this.typeFormError.set(null);
    const payload = { code: raw.code.trim(), nom: raw.nom.trim(), description: raw.description.trim() || undefined, ordre: Number(raw.ordre) || undefined, actif: raw.actif };
    const id = this.editingTypeId();
    const request = id ? this.typeService.update(id, payload) : this.typeService.create(payload);
    request.subscribe({
      next: () => {
        this.typeFormSaving.set(false);
        this.typeFormVisible.set(false);
        this.fetchTypes();
      },
      error: () => {
        this.typeFormSaving.set(false);
        this.typeFormError.set("Impossible d'enregistrer ce type de document.");
      },
    });
  }

  readonly deleteTypeTarget = signal<GedDocumentTypeResponse | null>(null);
  readonly deletingType = signal(false);

  confirmDeleteType(item: GedDocumentTypeResponse): void {
    if (item.systeme) return;
    this.deleteTypeTarget.set(item);
  }

  performDeleteType(): void {
    const item = this.deleteTypeTarget();
    if (!item) return;
    this.deletingType.set(true);
    this.typeService.delete(item.id).subscribe({
      next: () => {
        this.deletingType.set(false);
        this.deleteTypeTarget.set(null);
        this.fetchTypes();
      },
      error: () => {
        this.deletingType.set(false);
        this.deleteTypeTarget.set(null);
        this.typesError.set('Impossible de supprimer ce type de document.');
      },
    });
  }

  readonly categories = signal<GedDocumentCategoryResponse[]>([]);
  readonly categoriesLoading = signal(false);
  readonly categoriesError = signal<string | null>(null);
  readonly categoriesSearch = signal('');
  readonly categoriesActifFilter = signal<RefActifFilter>('all');

  readonly filteredCategories = computed(() => {
    const query = this.categoriesSearch().trim().toLowerCase();
    const actif = this.categoriesActifFilter();
    return this.categories()
      .filter((c) => actif === 'all' || c.actif === (actif === 'active'))
      .filter((c) => !query || `${c.code} ${c.nom}`.toLowerCase().includes(query))
      .sort((a, b) => (a.ordre ?? 0) - (b.ordre ?? 0));
  });

  fetchCategories(): void {
    this.categoriesLoading.set(true);
    this.categoriesError.set(null);
    this.categoryService.list(0, 200).subscribe({
      next: (page) => {
        this.categories.set(page.content);
        this.categoriesLoading.set(false);
      },
      error: () => {
        this.categoriesError.set('Impossible de charger les catégories de document.');
        this.categoriesLoading.set(false);
      },
    });
  }

  readonly categoryFormVisible = signal(false);
  readonly categoryFormSaving = signal(false);
  readonly categoryFormError = signal<string | null>(null);
  readonly editingCategoryId = signal<number | null>(null);
  readonly categoryForm = this.fb.nonNullable.group({ code: [''], nom: [''], description: [''], ordre: [0], actif: [true] });

  openCreateCategory(): void {
    this.editingCategoryId.set(null);
    this.categoryFormError.set(null);
    this.categoryForm.reset({ code: '', nom: '', description: '', ordre: 0, actif: true });
    this.categoryFormVisible.set(true);
  }

  openEditCategory(item: GedDocumentCategoryResponse): void {
    this.editingCategoryId.set(item.id);
    this.categoryFormError.set(null);
    this.categoryForm.reset({ code: item.code, nom: item.nom, description: item.description ?? '', ordre: item.ordre ?? 0, actif: item.actif });
    this.categoryFormVisible.set(true);
  }

  saveCategory(): void {
    const raw = this.categoryForm.getRawValue();
    if (!raw.code.trim() || !raw.nom.trim()) {
      this.categoryFormError.set('Le code et le nom sont requis.');
      return;
    }
    this.categoryFormSaving.set(true);
    this.categoryFormError.set(null);
    const payload = { code: raw.code.trim(), nom: raw.nom.trim(), description: raw.description.trim() || undefined, ordre: Number(raw.ordre) || undefined, actif: raw.actif };
    const id = this.editingCategoryId();
    const request = id ? this.categoryService.update(id, payload) : this.categoryService.create(payload);
    request.subscribe({
      next: () => {
        this.categoryFormSaving.set(false);
        this.categoryFormVisible.set(false);
        this.fetchCategories();
      },
      error: () => {
        this.categoryFormSaving.set(false);
        this.categoryFormError.set('Impossible d’enregistrer cette catégorie.');
      },
    });
  }

  readonly deleteCategoryTarget = signal<GedDocumentCategoryResponse | null>(null);
  readonly deletingCategory = signal(false);

  confirmDeleteCategory(item: GedDocumentCategoryResponse): void {
    if (item.systeme) return;
    this.deleteCategoryTarget.set(item);
  }

  performDeleteCategory(): void {
    const item = this.deleteCategoryTarget();
    if (!item) return;
    this.deletingCategory.set(true);
    this.categoryService.delete(item.id).subscribe({
      next: () => {
        this.deletingCategory.set(false);
        this.deleteCategoryTarget.set(null);
        this.fetchCategories();
      },
      error: () => {
        this.deletingCategory.set(false);
        this.deleteCategoryTarget.set(null);
        this.categoriesError.set('Impossible de supprimer cette catégorie.');
      },
    });
  }

  // ================================================================ DOCUMENTS ================================================================

  readonly documents = signal<GedDocumentResponse[]>([]);
  readonly documentsLoading = signal(true);
  readonly documentsError = signal<string | null>(null);
  readonly documentsPage = signal(0);
  readonly documentsTotalPages = signal(1);
  readonly documentsTotalElements = signal(0);

  readonly numeroFilter = signal('');
  readonly typeFilter = signal('');
  readonly categoryFilter = signal('');
  readonly statusFilter = signal<'all' | GedDocumentStatus>('all');
  readonly confidentialityFilter = signal<'all' | GedConfidentialityLevel>('all');
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  fetchDocuments(): void {
    this.documentsLoading.set(true);
    this.documentsError.set(null);
    const status = this.statusFilter();
    const confidentiality = this.confidentialityFilter();
    this.documentService
      .search(this.documentsPage(), PAGE_SIZE, {
        documentNumber: this.numeroFilter() || undefined,
        documentType: this.typeFilter() || undefined,
        category: this.categoryFilter() || undefined,
        status: status === 'all' ? undefined : status,
        confidentialityLevel: confidentiality === 'all' ? undefined : confidentiality,
      })
      .subscribe({
        next: (page) => {
          this.documents.set(page.content);
          this.documentsTotalPages.set(Math.max(1, page.totalPages));
          this.documentsTotalElements.set(page.totalElements);
          this.documentsLoading.set(false);
        },
        error: () => {
          this.documentsError.set('Impossible de charger les documents.');
          this.documentsLoading.set(false);
        },
      });
  }

  onNumeroFilterInput(value: string): void {
    this.numeroFilter.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.documentsPage.set(0);
      this.fetchDocuments();
    }, 400);
  }

  setTypeFilter(value: string): void {
    this.typeFilter.set(value);
    this.documentsPage.set(0);
    this.fetchDocuments();
  }

  setCategoryFilter(value: string): void {
    this.categoryFilter.set(value);
    this.documentsPage.set(0);
    this.fetchDocuments();
  }

  setStatusFilter(value: string): void {
    this.statusFilter.set(value as 'all' | GedDocumentStatus);
    this.documentsPage.set(0);
    this.fetchDocuments();
  }

  setConfidentialityFilter(value: string): void {
    this.confidentialityFilter.set(value as 'all' | GedConfidentialityLevel);
    this.documentsPage.set(0);
    this.fetchDocuments();
  }

  resetFilters(): void {
    this.numeroFilter.set('');
    this.typeFilter.set('');
    this.categoryFilter.set('');
    this.statusFilter.set('all');
    this.confidentialityFilter.set('all');
    this.documentsPage.set(0);
    this.fetchDocuments();
  }

  goToDocumentsPage(index: number): void {
    if (index < 0 || index >= this.documentsTotalPages()) return;
    this.documentsPage.set(index);
    this.fetchDocuments();
  }

  /** Télécharge la version courante en retrouvant son nom de fichier réel via /versions
   * (GedDocumentResponse n'expose pas le nom de fichier — seule GedDocumentVersionResponse l'a). */
  downloadCurrentVersion(doc: GedDocumentResponse): void {
    this.documentService.listVersions(doc.id).subscribe({
      next: (versions) => {
        const current = versions.find((v) => v.versionNumber === doc.currentVersionNumber) ?? versions[0];
        const filename = current?.originalFileName ?? `${doc.documentNumber}.bin`;
        this.documentService.download(doc.id).subscribe({
          next: (blob) => this.downloadBlob(blob, filename),
          error: () => this.documentsError.set('Impossible de télécharger ce document.'),
        });
      },
      error: () => this.documentsError.set('Impossible de télécharger ce document.'),
    });
  }

  // -- création / dépôt --

  readonly uploadFormVisible = signal(false);
  readonly uploadSaving = signal(false);
  readonly uploadError = signal<string | null>(null);
  readonly selectedFile = signal<File | null>(null);

  readonly uploadForm = this.fb.nonNullable.group({
    documentTypeId: [''],
    categoryId: [''],
    title: [''],
    description: [''],
    entityType: [''],
    entityId: [''],
    confidentialityLevel: ['INTERNAL' as GedConfidentialityLevel],
    source: [''],
  });

  readonly resolvingEntity = signal(false);
  readonly resolvedEntityLabel = signal<string | null>(null);
  readonly resolvedEntityError = signal<string | null>(null);

  openUploadForm(): void {
    this.uploadError.set(null);
    this.selectedFile.set(null);
    this.resolvedEntityLabel.set(null);
    this.resolvedEntityError.set(null);
    this.uploadForm.reset({
      documentTypeId: '',
      categoryId: '',
      title: '',
      description: '',
      entityType: '',
      entityId: '',
      confidentialityLevel: 'INTERNAL',
      source: '',
    });
    this.uploadFormVisible.set(true);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile.set(file);
  }

  clearSelectedFile(): void {
    this.selectedFile.set(null);
  }

  onDropzoneDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  onDropzoneDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0];
    if (file) this.selectedFile.set(file);
  }

  onEntityTypeChange(value: string): void {
    this.uploadForm.controls.entityType.setValue(value);
    this.uploadForm.controls.entityId.setValue('');
    this.resolvedEntityLabel.set(null);
    this.resolvedEntityError.set(null);
  }

  /** Résout l'id saisi contre le service du module concerné, pour afficher un libellé de
   * confirmation avant l'envoi plutôt que de rattacher un document à un id à l'aveugle. */
  resolveEntity(): void {
    const entityType = this.uploadForm.controls.entityType.value as GedEntityType | '';
    const entityIdRaw = this.uploadForm.controls.entityId.value;
    const entityId = Number(entityIdRaw);
    if (!entityType || !entityIdRaw || !Number.isFinite(entityId)) return;
    this.resolvingEntity.set(true);
    this.resolvedEntityLabel.set(null);
    this.resolvedEntityError.set(null);

    const onError = () => {
      this.resolvingEntity.set(false);
      this.resolvedEntityError.set('Aucun enregistrement trouvé avec cet identifiant.');
    };

    if (entityType === 'CLIENT') {
      this.insuredService.get(entityId).subscribe({
        next: (i: InsuredResponse) => {
          this.resolvingEntity.set(false);
          this.resolvedEntityLabel.set(`${i.nom} ${i.prenoms}`);
        },
        error: onError,
      });
    } else if (entityType === 'CONTRACT') {
      this.contractService.get(entityId).subscribe({
        next: (c) => {
          this.resolvingEntity.set(false);
          this.resolvedEntityLabel.set(`${c.policyNumber} — ${c.productName}`);
        },
        error: onError,
      });
    } else if (entityType === 'VEHICLE') {
      this.fleetService.getVehicule(entityId).subscribe({
        next: (v) => {
          this.resolvingEntity.set(false);
          this.resolvedEntityLabel.set(`${v.immatriculation} — ${v.marque} ${v.modele}`);
        },
        error: onError,
      });
    } else if (entityType === 'FLEET') {
      this.fleetService.getFleet(entityId).subscribe({
        next: (f) => {
          this.resolvingEntity.set(false);
          this.resolvedEntityLabel.set(`${f.fleetNumber} — ${f.name}`);
        },
        error: onError,
      });
    } else if (entityType === 'CLAIM') {
      this.sinistreService.getById(entityId).subscribe({
        next: (s) => {
          this.resolvingEntity.set(false);
          this.resolvedEntityLabel.set(`${s.numero} — ${s.nature}`);
        },
        error: onError,
      });
    }
  }

  submitUpload(): void {
    const raw = this.uploadForm.getRawValue();
    const file = this.selectedFile();
    if (!file) {
      this.uploadError.set('Sélectionnez un fichier.');
      return;
    }
    if (!raw.documentTypeId || !raw.title.trim()) {
      this.uploadError.set('Le type de document et le titre sont requis.');
      return;
    }
    if (raw.entityType && (!raw.entityId || this.resolvedEntityError())) {
      this.uploadError.set("Vérifiez l'entité associée avant d'enregistrer.");
      return;
    }
    this.uploadSaving.set(true);
    this.uploadError.set(null);
    this.documentService
      .upload(file, {
        documentTypeId: Number(raw.documentTypeId),
        categoryId: raw.categoryId ? Number(raw.categoryId) : undefined,
        title: raw.title.trim(),
        description: raw.description.trim() || undefined,
        entityType: raw.entityType || undefined,
        entityId: raw.entityType && raw.entityId ? Number(raw.entityId) : undefined,
        confidentialityLevel: raw.confidentialityLevel,
        source: raw.source.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.uploadSaving.set(false);
          this.uploadFormVisible.set(false);
          this.fetchDocuments();
        },
        error: (err) => {
          this.uploadSaving.set(false);
          if (err?.status === 415) this.uploadError.set("Ce format de fichier n'est pas accepté.");
          else if (err?.status === 400) this.uploadError.set('La taille du fichier dépasse la limite autorisée.');
          else this.uploadError.set("Impossible de déposer ce document. Réessayez.");
        },
      });
  }

  // -- détail --

  readonly detailVisible = signal(false);
  readonly selectedDocument = signal<GedDocumentResponse | null>(null);
  readonly detailVersions = signal<GedDocumentVersionResponse[]>([]);
  readonly detailVersionsLoading = signal(false);
  readonly detailLinks = signal<GedDocumentLinkResponse[]>([]);
  readonly detailLinksLoading = signal(false);
  readonly detailActionError = signal<string | null>(null);
  readonly detailActionPending = signal(false);

  openDetail(doc: GedDocumentResponse): void {
    this.selectedDocument.set(doc);
    this.detailActionError.set(null);
    this.detailVersions.set([]);
    this.detailLinks.set([]);
    this.detailVisible.set(true);
    this.fetchDetailVersions(doc.id);
    this.fetchDetailLinks(doc.id);
  }

  closeDetail(): void {
    this.detailVisible.set(false);
    this.selectedDocument.set(null);
  }

  private fetchDetailVersions(id: number): void {
    this.detailVersionsLoading.set(true);
    this.documentService.listVersions(id).subscribe({
      next: (versions) => {
        this.detailVersions.set(versions);
        this.detailVersionsLoading.set(false);
      },
      error: () => this.detailVersionsLoading.set(false),
    });
  }

  private fetchDetailLinks(id: number): void {
    this.detailLinksLoading.set(true);
    this.documentService.listLinks(id).subscribe({
      next: (links) => {
        this.detailLinks.set(links);
        this.detailLinksLoading.set(false);
      },
      error: () => this.detailLinksLoading.set(false),
    });
  }

  private applyDocumentUpdate(updated: GedDocumentResponse): void {
    this.selectedDocument.set(updated);
    this.documents.set(this.documents().map((d) => (d.id === updated.id ? updated : d)));
  }

  downloadVersion(doc: GedDocumentResponse, version: GedDocumentVersionResponse): void {
    this.documentService.downloadVersion(doc.id, version.id).subscribe({
      next: (blob) => this.downloadBlob(blob, version.originalFileName),
      error: () => this.detailActionError.set('Impossible de télécharger cette version.'),
    });
  }

  validateDocument(doc: GedDocumentResponse): void {
    this.detailActionPending.set(true);
    this.detailActionError.set(null);
    this.documentService.validate(doc.id).subscribe({
      next: (updated) => {
        this.detailActionPending.set(false);
        this.applyDocumentUpdate(updated);
      },
      error: (err) => {
        this.detailActionPending.set(false);
        this.detailActionError.set(
          err?.status === 409 ? 'Ce document ne peut plus être validé (statut déjà changé).' : 'Impossible de valider ce document.',
        );
      },
    });
  }

  archiveDocument(doc: GedDocumentResponse): void {
    this.detailActionPending.set(true);
    this.detailActionError.set(null);
    this.documentService.archive(doc.id).subscribe({
      next: (updated) => {
        this.detailActionPending.set(false);
        this.applyDocumentUpdate(updated);
      },
      error: () => {
        this.detailActionPending.set(false);
        this.detailActionError.set("Impossible d'archiver ce document.");
      },
    });
  }

  // -- rejet (motif obligatoire) --

  readonly rejectTarget = signal<GedDocumentResponse | null>(null);
  readonly rejectForm = this.fb.nonNullable.group({ reason: [''] });
  readonly rejectSaving = signal(false);
  readonly rejectError = signal<string | null>(null);

  askReject(doc: GedDocumentResponse): void {
    this.rejectError.set(null);
    this.rejectForm.reset({ reason: '' });
    this.rejectTarget.set(doc);
  }

  submitReject(): void {
    const doc = this.rejectTarget();
    const reason = this.rejectForm.getRawValue().reason.trim();
    if (!doc) return;
    if (!reason) {
      this.rejectError.set('Le motif du rejet est obligatoire.');
      return;
    }
    this.rejectSaving.set(true);
    this.rejectError.set(null);
    this.documentService.reject(doc.id, { reason }).subscribe({
      next: (updated) => {
        this.rejectSaving.set(false);
        this.rejectTarget.set(null);
        this.applyDocumentUpdate(updated);
      },
      error: () => {
        this.rejectSaving.set(false);
        this.rejectError.set('Impossible de rejeter ce document.');
      },
    });
  }

  // -- nouvelle version --

  readonly newVersionFile = signal<File | null>(null);
  readonly newVersionSaving = signal(false);
  readonly newVersionError = signal<string | null>(null);

  onNewVersionFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.newVersionFile.set(input.files?.[0] ?? null);
  }

  submitNewVersion(doc: GedDocumentResponse): void {
    const file = this.newVersionFile();
    if (!file) {
      this.newVersionError.set('Sélectionnez un fichier.');
      return;
    }
    this.newVersionSaving.set(true);
    this.newVersionError.set(null);
    this.documentService.addVersion(doc.id, file).subscribe({
      next: () => {
        this.newVersionSaving.set(false);
        this.newVersionFile.set(null);
        this.fetchDetailVersions(doc.id);
        this.documentService.get(doc.id).subscribe((updated) => this.applyDocumentUpdate(updated));
      },
      error: (err) => {
        this.newVersionSaving.set(false);
        if (err?.status === 415) this.newVersionError.set("Ce format de fichier n'est pas accepté.");
        else if (err?.status === 400) this.newVersionError.set('La taille du fichier dépasse la limite autorisée.');
        else this.newVersionError.set('Impossible de déposer cette nouvelle version.');
      },
    });
  }

  // -- métadonnées --

  readonly editMetaVisible = signal(false);
  readonly editMetaSaving = signal(false);
  readonly editMetaError = signal<string | null>(null);
  readonly editMetaForm = this.fb.nonNullable.group({
    title: [''],
    description: [''],
    categoryId: [''],
    confidentialityLevel: ['INTERNAL' as GedConfidentialityLevel],
  });

  openEditMeta(doc: GedDocumentResponse): void {
    this.editMetaError.set(null);
    this.editMetaForm.reset({
      title: doc.title,
      description: doc.description ?? '',
      categoryId: doc.categoryId != null ? String(doc.categoryId) : '',
      confidentialityLevel: doc.confidentialityLevel,
    });
    this.editMetaVisible.set(true);
  }

  submitEditMeta(): void {
    const doc = this.selectedDocument();
    const raw = this.editMetaForm.getRawValue();
    if (!doc) return;
    if (!raw.title.trim()) {
      this.editMetaError.set('Le titre est requis.');
      return;
    }
    this.editMetaSaving.set(true);
    this.editMetaError.set(null);
    this.documentService
      .updateMetadata(doc.id, {
        title: raw.title.trim(),
        description: raw.description.trim() || undefined,
        categoryId: raw.categoryId ? Number(raw.categoryId) : undefined,
        confidentialityLevel: raw.confidentialityLevel,
      })
      .subscribe({
        next: (updated) => {
          this.editMetaSaving.set(false);
          this.editMetaVisible.set(false);
          this.applyDocumentUpdate(updated);
        },
        error: () => {
          this.editMetaSaving.set(false);
          this.editMetaError.set("Impossible d'enregistrer les modifications.");
        },
      });
  }

  // -- liens --

  readonly linkFormVisible = signal(false);
  readonly linkForm = this.fb.nonNullable.group({ entityType: ['CLIENT' as GedEntityType], entityId: [''], relationshipType: [''] });
  readonly linkSaving = signal(false);
  readonly linkError = signal<string | null>(null);

  openAddLink(): void {
    this.linkError.set(null);
    this.linkForm.reset({ entityType: 'CLIENT', entityId: '', relationshipType: '' });
    this.linkFormVisible.set(true);
  }

  submitAddLink(): void {
    const doc = this.selectedDocument();
    const raw = this.linkForm.getRawValue();
    if (!doc) return;
    if (!raw.entityId) {
      this.linkError.set("L'identifiant de l'entité est requis.");
      return;
    }
    this.linkSaving.set(true);
    this.linkError.set(null);
    this.documentService
      .createLink(doc.id, { entityType: raw.entityType, entityId: Number(raw.entityId), relationshipType: raw.relationshipType.trim() || undefined })
      .subscribe({
        next: () => {
          this.linkSaving.set(false);
          this.linkFormVisible.set(false);
          this.fetchDetailLinks(doc.id);
        },
        error: () => {
          this.linkSaving.set(false);
          this.linkError.set("Impossible d'ajouter ce lien.");
        },
      });
  }

  readonly deleteLinkTarget = signal<GedDocumentLinkResponse | null>(null);
  readonly deletingLink = signal(false);

  confirmDeleteLink(link: GedDocumentLinkResponse): void {
    this.deleteLinkTarget.set(link);
  }

  performDeleteLink(): void {
    const doc = this.selectedDocument();
    const link = this.deleteLinkTarget();
    if (!doc || !link) return;
    this.deletingLink.set(true);
    this.documentService.deleteLink(doc.id, link.id).subscribe({
      next: () => {
        this.deletingLink.set(false);
        this.deleteLinkTarget.set(null);
        this.fetchDetailLinks(doc.id);
      },
      error: () => {
        this.deletingLink.set(false);
        this.deleteLinkTarget.set(null);
        this.detailActionError.set('Impossible de détacher ce lien.');
      },
    });
  }
}
