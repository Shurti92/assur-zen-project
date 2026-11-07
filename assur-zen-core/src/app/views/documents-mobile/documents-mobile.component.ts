import { Component, inject, signal } from '@angular/core';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { MobileDocumentService, documentErrorMessage } from '../../core/mobile-admin/mobile-document.service';
import {
  MobilePendingDocument,
  documentTypeLabel,
  formatDocumentSize,
} from '../../core/mobile-admin/models/mobile-document.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { DocumentRejectModalComponent } from '../../shared/document-reject-modal/document-reject-modal.component';

/** Mobile AssurZen › Documents à valider : file des documents déposés par les assurés. */
@Component({
  selector: 'app-documents-mobile',
  templateUrl: './documents-mobile.component.html',
  imports: [ButtonDirective, SpinnerComponent, AlertMessageComponent, DocumentRejectModalComponent],
})
export class DocumentsMobileComponent {
  private readonly service = inject(MobileDocumentService);

  readonly pageSize = 20;
  readonly items = signal<MobilePendingDocument[]>([]);
  readonly pageIndex = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  readonly busyId = signal<number | null>(null);
  readonly rejectTarget = signal<MobilePendingDocument | null>(null);

  readonly typeLabel = documentTypeLabel;
  readonly size = formatDocumentSize;

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.pending(this.pageIndex(), this.pageSize).subscribe({
      next: (p) => {
        this.items.set(p.items);
        this.totalElements.set(p.totalElements);
        this.totalPages.set(p.totalPages);
        this.service.pendingCount.set(p.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(documentErrorMessage(err, 'Impossible de charger les documents à valider.'));
        this.loading.set(false);
      },
    });
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
    this.fetch();
  }

  preview(item: MobilePendingDocument): void {
    this.actionError.set(null);
    this.service.openFile(item.document.contractId, item.document.id).subscribe({
      error: (err) => this.actionError.set(documentErrorMessage(err, "Impossible d'ouvrir le fichier.")),
    });
  }

  validate(item: MobilePendingDocument): void {
    this.busyId.set(item.document.id);
    this.actionError.set(null);
    this.info.set(null);
    this.service.validate(item.document.contractId, item.document.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.info.set(`« ${item.document.fileName} » validé : l'assuré est notifié.`);
        this.afterAction();
      },
      error: (err) => {
        this.busyId.set(null);
        this.actionError.set(documentErrorMessage(err, 'Impossible de valider le document.'));
        this.fetch();
      },
    });
  }

  confirmReject(reason: string): void {
    const item = this.rejectTarget();
    if (!item) return;
    this.busyId.set(item.document.id);
    this.actionError.set(null);
    this.info.set(null);
    this.service.reject(item.document.contractId, item.document.id, reason).subscribe({
      next: () => {
        this.busyId.set(null);
        this.rejectTarget.set(null);
        this.info.set(`« ${item.document.fileName} » refusé : le motif est communiqué à l'assuré.`);
        this.afterAction();
      },
      error: (err) => {
        this.busyId.set(null);
        this.rejectTarget.set(null);
        this.actionError.set(documentErrorMessage(err, 'Impossible de refuser le document.'));
        this.fetch();
      },
    });
  }

  private afterAction(): void {
    if (this.items().length === 1 && this.pageIndex() > 0) this.pageIndex.update((p) => p - 1);
    this.fetch();
  }

  formatDate(value: string | null | undefined): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }
}
