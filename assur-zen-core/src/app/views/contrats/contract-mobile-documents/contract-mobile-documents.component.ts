import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { AuthService } from '../../../core/auth/auth.service';
import { MobileDocumentService, documentErrorMessage } from '../../../core/mobile-admin/mobile-document.service';
import {
  DOCUMENT_SOURCE_LABEL,
  DOCUMENT_STATUS_LABEL,
  DOCUMENT_STATUS_VARIANT,
  DOCUMENT_TYPE_OPTIONS,
  MobileContractDocument,
  MobileDocumentStatus,
  documentTypeLabel,
  formatDocumentSize,
} from '../../../core/mobile-admin/models/mobile-document.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { DocumentRejectModalComponent } from '../../../shared/document-reject-modal/document-reject-modal.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type StatusFilter = '' | MobileDocumentStatus;

const MAX_BYTES = 5 * 1024 * 1024;
const ALLOWED_TYPES = ['application/pdf', 'image/jpeg', 'image/png'];

/** Section « Documents mobile » de la fiche contrat (ADMIN) : documents visibles par l'assuré dans l'application. */
@Component({
  selector: 'app-contract-mobile-documents',
  templateUrl: './contract-mobile-documents.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    FormsModule,
    AlertMessageComponent,
    ConfirmDialogComponent,
    DocumentRejectModalComponent,
    ModalComponent,
  ],
})
export class ContractMobileDocumentsComponent {
  private readonly service = inject(MobileDocumentService);
  private readonly auth = inject(AuthService);

  readonly contractId = input.required<number>();
  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');

  readonly items = signal<MobileContractDocument[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  readonly busy = signal(false);
  readonly filter = signal<StatusFilter>('');

  readonly pendingCount = computed(() => this.items().filter((d) => d.status === 'PENDING_REVIEW').length);
  readonly visible = computed(() => this.items().filter((d) => !this.filter() || d.status === this.filter()));
  readonly count = (s: MobileDocumentStatus): number => this.items().filter((d) => d.status === s).length;

  readonly addVisible = signal(false);
  readonly addType = signal('CONTRAT_ASSURANCE');
  readonly addFile = signal<File | null>(null);
  readonly addError = signal<string | null>(null);

  readonly rejectTarget = signal<MobileContractDocument | null>(null);
  readonly deleteTarget = signal<MobileContractDocument | null>(null);

  readonly typeOptions = DOCUMENT_TYPE_OPTIONS;
  readonly typeLabel = documentTypeLabel;
  readonly statusLabel = DOCUMENT_STATUS_LABEL;
  readonly statusVariant = DOCUMENT_STATUS_VARIANT;
  readonly sourceLabel = DOCUMENT_SOURCE_LABEL;
  readonly size = formatDocumentSize;

  constructor() {
    effect(() => {
      const id = this.contractId();
      if (!untracked(this.isAdmin)) return;
      untracked(() => this.load(id));
    });
  }

  private load(id: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.filter.set('');
    this.service.list(id).subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: (err) => {
        this.items.set([]);
        this.error.set(documentErrorMessage(err, 'Impossible de charger les documents.'));
        this.loading.set(false);
      },
    });
  }

  private reload(): void {
    this.load(this.contractId());
    this.service.refreshPending();
  }

  preview(doc: MobileContractDocument): void {
    this.actionError.set(null);
    this.service.openFile(doc.contractId, doc.id).subscribe({
      error: (err) => this.actionError.set(documentErrorMessage(err, "Impossible d'ouvrir le fichier.")),
    });
  }

  validate(doc: MobileContractDocument): void {
    this.run(this.service.validate(doc.contractId, doc.id), `« ${doc.fileName} » validé : l'assuré est notifié.`, 'Impossible de valider le document.');
  }

  confirmReject(reason: string): void {
    const doc = this.rejectTarget();
    if (!doc) return;
    this.run(
      this.service.reject(doc.contractId, doc.id, reason),
      `« ${doc.fileName} » refusé : le motif est communiqué à l'assuré.`,
      'Impossible de refuser le document.',
      () => this.rejectTarget.set(null),
    );
  }

  confirmDelete(): void {
    const doc = this.deleteTarget();
    this.deleteTarget.set(null);
    if (!doc) return;
    this.run(this.service.remove(doc.contractId, doc.id), `« ${doc.fileName} » supprimé.`, 'Impossible de supprimer le document.');
  }

  openAdd(): void {
    this.addType.set('CONTRAT_ASSURANCE');
    this.addFile.set(null);
    this.addError.set(null);
    this.addVisible.set(true);
  }

  onFileChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.addFile.set(file);
    this.addError.set(null);
    if (file && file.size > MAX_BYTES) this.addError.set('Fichier trop volumineux (5 Mo maximum).');
    else if (file && !ALLOWED_TYPES.includes(file.type)) this.addError.set('Format non autorisé : PDF, JPG ou PNG uniquement.');
  }

  submitAdd(): void {
    const file = this.addFile();
    if (!file) {
      this.addError.set('Choisissez un fichier.');
      return;
    }
    if (this.addError()) return;
    this.busy.set(true);
    this.service.upload(this.contractId(), this.addType(), file).subscribe({
      next: () => {
        this.busy.set(false);
        this.addVisible.set(false);
        this.info.set(`« ${file.name} » ajouté${this.addType() === 'PIECE_IDENTITE' ? '' : " : l'assuré est notifié"}.`);
        this.reload();
      },
      error: (err) => {
        this.busy.set(false);
        this.addError.set(documentErrorMessage(err, "Impossible d'ajouter le document."));
      },
    });
  }

  private run(call: import('rxjs').Observable<unknown>, okMessage: string, failMessage: string, after?: () => void): void {
    this.busy.set(true);
    this.actionError.set(null);
    this.info.set(null);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        after?.();
        this.info.set(okMessage);
        this.reload();
      },
      error: (err) => {
        this.busy.set(false);
        after?.();
        this.actionError.set(documentErrorMessage(err, failMessage));
        this.reload();
      },
    });
  }

  formatDate(value: string | null | undefined): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }
}
