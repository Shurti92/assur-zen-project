import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';

import {
  CallbackRequest,
  ContractRequest,
  DeletionRequest,
  PendingCounts,
} from './models/mobile-request.model';

/** File « Demandes mobile » — /admin/mobile/{callbacks,contract-requests,deletion-requests} (ADMIN). */
@Injectable({ providedIn: 'root' })
export class MobileRequestService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/mobile';

  /** Demandes en attente (null tant que non chargé) — alimente les pastilles du menu. */
  readonly pending = signal<PendingCounts | null>(null);
  readonly pendingTotal = computed(() => {
    const p = this.pending();
    return p ? p.callbacks + p.contractRequests + p.deletionRequests : 0;
  });

  private params(statut?: string): HttpParams {
    return statut ? new HttpParams().set('statut', statut) : new HttpParams();
  }

  listCallbacks(statut?: string): Observable<CallbackRequest[]> {
    return this.http.get<CallbackRequest[]>(`${this.base}/callbacks`, { params: this.params(statut) });
  }

  listContractRequests(statut?: string): Observable<ContractRequest[]> {
    return this.http.get<ContractRequest[]>(`${this.base}/contract-requests`, { params: this.params(statut) });
  }

  listDeletionRequests(statut?: string): Observable<DeletionRequest[]> {
    return this.http.get<DeletionRequest[]>(`${this.base}/deletion-requests`, { params: this.params(statut) });
  }

  processCallback(id: number, statut: 'RAPPELE' | 'ANNULE', comment?: string): Observable<unknown> {
    return this.http.post(`${this.base}/callbacks/${id}/process`, { statut, comment: comment || undefined });
  }

  processContractRequest(id: number, statut: 'TRAITEE' | 'REFUSEE', comment?: string): Observable<unknown> {
    return this.http.post(`${this.base}/contract-requests/${id}/process`, { statut, comment: comment || undefined });
  }

  /** APPROVE est irréversible : confirm:true est obligatoire côté gateway (sinon 400 CONFIRMATION_REQUIRED). */
  processDeletion(
    id: number,
    decision: 'APPROVE' | 'REJECT',
    comment?: string,
  ): Observable<unknown> {
    return this.http.post(`${this.base}/deletion-requests/${id}/process`, {
      decision,
      confirm: decision === 'APPROVE' ? true : undefined,
      comment: comment || undefined,
    });
  }

  /** Recompte les demandes EN_ATTENTE (3 appels légers) et met à jour les pastilles du menu. */
  refreshPending(): void {
    forkJoin({
      callbacks: this.listCallbacks('EN_ATTENTE').pipe(map((l) => l.length)),
      contractRequests: this.listContractRequests('EN_ATTENTE').pipe(map((l) => l.length)),
      deletionRequests: this.listDeletionRequests('EN_ATTENTE').pipe(map((l) => l.length)),
    }).subscribe({
      next: (counts) => this.pending.set(counts),
      // Silencieux : une pastille manquante ne doit pas gêner la navigation.
      error: () => this.pending.set(null),
    });
  }
}
