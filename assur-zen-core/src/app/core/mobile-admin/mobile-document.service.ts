import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';

import { MobileContractDocument, MobilePendingDocumentPage } from './models/mobile-document.model';

const ERROR_MESSAGES: Record<string, string> = {
  CONTRACT_NOT_FOUND: 'Contrat introuvable.',
  DOCUMENT_NOT_PENDING: "Ce document n'est plus à valider (déjà traité).",
  FICHIER_TROP_VOLUMINEUX: 'Fichier trop volumineux (5 Mo maximum).',
  VALIDATION_FAILED: 'Champ manquant ou invalide (type, fichier ou motif).',
};

/** Message lisible pour une erreur du contrat documents. */
export function documentErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const code = err.error?.code as string | undefined;
    if (code && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code];
    if (err.status === 403) return 'Accès réservé aux administrateurs.';
    if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
  }
  return fallback;
}

/** Documents des contrats — /admin/mobile (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class MobileDocumentService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/mobile';

  /** Nombre de documents à valider (pastille du menu). null = inconnu. */
  readonly pendingCount = signal<number | null>(null);

  list(contractId: number): Observable<MobileContractDocument[]> {
    return this.http
      .get<{ items: MobileContractDocument[] }>(`${this.base}/contracts/${contractId}/documents`)
      .pipe(map((r) => r.items));
  }

  upload(contractId: number, type: string, file: File): Observable<MobileContractDocument> {
    const form = new FormData();
    form.append('type', type);
    form.append('file', file);
    return this.http.post<MobileContractDocument>(`${this.base}/contracts/${contractId}/documents`, form);
  }

  remove(contractId: number, documentId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/contracts/${contractId}/documents/${documentId}`);
  }

  validate(contractId: number, documentId: number): Observable<MobileContractDocument> {
    return this.http.post<MobileContractDocument>(`${this.base}/contracts/${contractId}/documents/${documentId}/validate`, {});
  }

  reject(contractId: number, documentId: number, reason: string): Observable<MobileContractDocument> {
    return this.http.post<MobileContractDocument>(`${this.base}/contracts/${contractId}/documents/${documentId}/reject`, { reason });
  }

  pending(page: number, size: number): Observable<MobilePendingDocumentPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<MobilePendingDocumentPage>(`${this.base}/contract-documents/pending`, { params });
  }

  /** Recompte les documents à valider pour la pastille du menu (silencieux en cas d'erreur). */
  refreshPending(): void {
    this.pending(0, 1).subscribe({
      next: (p) => this.pendingCount.set(p.totalElements),
      error: () => this.pendingCount.set(null),
    });
  }

  /** Ouvre le fichier (PDF ou image) dans un nouvel onglet, sans téléchargement. */
  openFile(contractId: number, documentId: number): Observable<void> {
    return this.http
      .get(`${this.base}/contracts/${contractId}/documents/${documentId}/file`, { responseType: 'blob' })
      .pipe(
        map((blob) => {
          const url = URL.createObjectURL(blob);
          window.open(url, '_blank', 'noopener');
          setTimeout(() => URL.revokeObjectURL(url), 60_000);
        }),
      );
  }
}
