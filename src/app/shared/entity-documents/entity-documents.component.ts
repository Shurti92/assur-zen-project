import { Component, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
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
  GedDocumentResponse,
  GedDocumentStatus,
  GedDocumentVersionResponse,
  GedEntityType,
} from '../../core/ged/models/ged-document.model';
import { GedDocumentTypeResponse } from '../../core/ged/models/ged-document-type.model';
import { GedDocumentCategoryResponse } from '../../core/ged/models/ged-document-category.model';
import { AlertMessageComponent } from '../alert-message/alert-message.component';
import { ModalComponent } from '../modal/modal.component';

const PAGE_SIZE = 10;

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

/** Options des pastilles "Niveau de confidentialité" — mappées sur les variantes sémantiques
 * .dl-chip (success/info/warning/danger). Dupliqué volontairement depuis GedComponent pour ne
 * pas créer de dépendance croisée entre ce widget partagé et le module GED. */
const CONFIDENTIALITY_OPTIONS: { value: GedConfidentialityLevel; label: string; variant: string }[] = [
  { value: 'PUBLIC', label: 'Public', variant: 'success' },
  { value: 'INTERNAL', label: 'Interne', variant: 'info' },
  { value: 'CONFIDENTIAL', label: 'Confidentiel', variant: 'warning' },
  { value: 'RESTRICTED', label: 'Restreint', variant: 'danger' },
];

/** Widget réutilisable listant/gérant les documents GED rattachés à une entité métier — évite
 * de dupliquer la logique GED dans chaque module consommateur (Assurés, Contrats, Fleet, ...).
 * Usage : <app-entity-documents [entityType]="'CLIENT'" [entityId]="insured.id" />. */
@Component({
  selector: 'app-entity-documents',
  templateUrl: './entity-documents.component.html',
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
    ModalComponent,
  ],
})
export class EntityDocumentsComponent {
  readonly entityType = input.required<GedEntityType>();
  readonly entityId = input.required<number>();

  private readonly documentService = inject(GedDocumentService);
  private readonly typeService = inject(GedDocumentTypeService);
  private readonly categoryService = inject(GedDocumentCategoryService);
  private readonly fb = inject(FormBuilder);

  readonly statusLabel = STATUS_LABEL;
  readonly confidentialityOptions = CONFIDENTIALITY_OPTIONS;

  readonly documents = signal<GedDocumentResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly totalElements = signal(0);

  readonly types = signal<GedDocumentTypeResponse[]>([]);
  readonly categories = signal<GedDocumentCategoryResponse[]>([]);
  /** Distinct de `error` (qui porte les erreurs de la liste de documents) — ces référentiels
   * étaient auparavant chargés en avalant l'erreur silencieusement (menu déroulant vide sans
   * aucun indice), ce qui rendait un vrai échec réseau/permissions indiscernable d'une liste
   * simplement vide côté backend. */
  readonly typesError = signal<string | null>(null);
  readonly categoriesError = signal<string | null>(null);

  private lastFetchedFor: string | null = null;

  constructor() {
    this.fetchReferentials();

    effect(() => {
      const key = `${this.entityType()}:${this.entityId()}`;
      if (key === this.lastFetchedFor) return;
      this.lastFetchedFor = key;
      this.page.set(0);
      this.fetch();
    });
  }

  fetchReferentials(): void {
    this.typesError.set(null);
    this.typeService.list(0, 200, undefined, true).subscribe({
      next: (p) => this.types.set(p.content),
      error: () => this.typesError.set('Impossible de charger les types de document.'),
    });
    this.categoriesError.set(null);
    this.categoryService.list(0, 200, undefined, true).subscribe({
      next: (p) => this.categories.set(p.content),
      error: () => this.categoriesError.set('Impossible de charger les catégories de document.'),
    });
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

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    const type = this.entityType();
    const id = this.entityId();
    const request$ =
      type === 'CLIENT'
        ? this.documentService.listByClient(id, this.page(), PAGE_SIZE)
        : type === 'CONTRACT'
          ? this.documentService.listByContract(id, this.page(), PAGE_SIZE)
          : type === 'VEHICLE'
            ? this.documentService.listByVehicle(id, this.page(), PAGE_SIZE)
            : type === 'FLEET'
              ? this.documentService.listByFleet(id, this.page(), PAGE_SIZE)
              : this.documentService.listByClaim(id, this.page(), PAGE_SIZE);
    request$.subscribe({
      next: (result) => {
        this.documents.set(result.content);
        this.totalPages.set(Math.max(1, result.totalPages));
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les documents.');
        this.loading.set(false);
      },
    });
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.page.set(index);
    this.fetch();
  }

  downloadCurrentVersion(doc: GedDocumentResponse): void {
    this.documentService.listVersions(doc.id).subscribe({
      next: (versions) => {
        const current = versions.find((v) => v.versionNumber === doc.currentVersionNumber) ?? versions[0];
        const filename = current?.originalFileName ?? `${doc.documentNumber}.bin`;
        this.documentService.download(doc.id).subscribe({
          next: (blob) => this.downloadBlob(blob, filename),
          error: () => this.error.set('Impossible de télécharger ce document.'),
        });
      },
      error: () => this.error.set('Impossible de télécharger ce document.'),
    });
  }

  // -- dépôt (entité déjà rattachée implicitement) --

  readonly uploadFormVisible = signal(false);
  readonly uploadSaving = signal(false);
  readonly uploadError = signal<string | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly uploadForm = this.fb.nonNullable.group({
    documentTypeId: [''],
    categoryId: [''],
    title: [''],
    confidentialityLevel: ['INTERNAL' as GedConfidentialityLevel],
  });

  openUploadForm(): void {
    this.uploadError.set(null);
    this.selectedFile.set(null);
    this.uploadForm.reset({ documentTypeId: '', categoryId: '', title: '', confidentialityLevel: 'INTERNAL' });
    this.uploadFormVisible.set(true);
    if (this.types().length === 0 || this.categories().length === 0) this.fetchReferentials();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
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
    this.uploadSaving.set(true);
    this.uploadError.set(null);
    this.documentService
      .upload(file, {
        documentTypeId: Number(raw.documentTypeId),
        categoryId: raw.categoryId ? Number(raw.categoryId) : undefined,
        title: raw.title.trim(),
        entityType: this.entityType(),
        entityId: this.entityId(),
        confidentialityLevel: raw.confidentialityLevel,
      })
      .subscribe({
        next: () => {
          this.uploadSaving.set(false);
          this.uploadFormVisible.set(false);
          this.page.set(0);
          this.fetch();
        },
        error: (err) => {
          this.uploadSaving.set(false);
          if (err?.status === 415) this.uploadError.set("Ce format de fichier n'est pas accepté.");
          else if (err?.status === 400) this.uploadError.set('La taille du fichier dépasse la limite autorisée.');
          else this.uploadError.set('Impossible de déposer ce document. Réessayez.');
        },
      });
  }

  // -- détail --

  readonly detailVisible = signal(false);
  readonly selectedDocument = signal<GedDocumentResponse | null>(null);
  readonly detailVersions = signal<GedDocumentVersionResponse[]>([]);
  readonly detailVersionsLoading = signal(false);
  readonly detailActionError = signal<string | null>(null);
  readonly detailActionPending = signal(false);

  openDetail(doc: GedDocumentResponse): void {
    this.selectedDocument.set(doc);
    this.detailActionError.set(null);
    this.detailVersions.set([]);
    this.detailVisible.set(true);
    this.detailVersionsLoading.set(true);
    this.documentService.listVersions(doc.id).subscribe({
      next: (versions) => {
        this.detailVersions.set(versions);
        this.detailVersionsLoading.set(false);
      },
      error: () => this.detailVersionsLoading.set(false),
    });
  }

  closeDetail(): void {
    this.detailVisible.set(false);
    this.selectedDocument.set(null);
  }

  private applyDocumentUpdate(updated: GedDocumentResponse): void {
    this.selectedDocument.set(updated);
    this.documents.set(this.documents().map((d) => (d.id === updated.id ? updated : d)));
  }

  downloadVersion(version: GedDocumentVersionResponse): void {
    const doc = this.selectedDocument();
    if (!doc) return;
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
      error: () => {
        this.detailActionPending.set(false);
        this.detailActionError.set('Impossible de valider ce document.');
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

  // -- rejet --

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
}
