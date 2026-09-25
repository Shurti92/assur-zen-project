import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ContractFractionneSummaryResponse,
  ContractMessageResponse,
  ContractPage,
  ContractRequest,
  ContractResponse,
  EcheanceResponse,
  PaiementPieceJointeResponse,
  PaiementRequest,
  PaiementResponse,
} from './models/contract.model';

export interface ContractListOptions {
  q?: string;
  status?: string;
  insuredId?: number;
}

export interface ContractExportOptions {
  q?: string;
  status?: string;
  commissionStatus?: string;
  productId?: number;
  insuredId?: number;
  salesRepId?: number;
}

@Injectable({ providedIn: 'root' })
export class ContractService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, options?: ContractListOptions): Observable<ContractPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.q) params = params.set('q', options.q);
    if (options?.status) params = params.set('status', options.status);
    if (options?.insuredId !== undefined) params = params.set('insuredId', options.insuredId);
    return this.http.get<ContractPage>('/contracts', { params });
  }

  get(id: number): Observable<ContractResponse> {
    return this.http.get<ContractResponse>(`/contracts/${id}`);
  }

  create(payload: ContractRequest): Observable<ContractResponse> {
    return this.http.post<ContractResponse>('/contracts', payload);
  }

  /** Remplacement complet : tout champ omis est remis à null côté backend (dates, notes,
   * véhicule, contrat précédent…) — partir de toContractRequest() et n'écraser que les champs
   * modifiés. Contrat signé : prime/commissions/partenaire/compagnie inchangés, sinon 400
   * (passer par un avenant). */
  update(id: number, payload: ContractRequest): Observable<ContractResponse> {
    return this.http.put<ContractResponse>(`/contracts/${id}`, payload);
  }

  /** Fetches every contract (optionally filtered by status) in one request — used by
   * pages that need full client-side filtering/aggregation rather than server-side
   * pagination, since the dataset here is small. */
  listAll(status?: string): Observable<ContractPage> {
    return this.list(0, 1000, status ? { status } : undefined);
  }

  /** Backward-compatible alias used for per-partner realized-production totals. */
  listSigned(): Observable<ContractPage> {
    return this.listAll('signed');
  }

  /** Signed contracts past their échéance that were never renewed — same
   * "fetch everything once, filter/paginate client-side" approach as listAll(). */
  listNonRenouveles(): Observable<ContractPage> {
    return this.http.get<ContractPage>('/contracts/non-renouveles', {
      params: new HttpParams().set('page', 0).set('size', 1000),
    });
  }

  /** Signed contracts whose échéance falls within the next `days` (defaults to
   * the backend's own default, currently 30). */
  listEcheanceProche(days?: number): Observable<ContractPage> {
    let params = new HttpParams().set('page', 0).set('size', 1000);
    if (days !== undefined) params = params.set('days', days);
    return this.http.get<ContractPage>('/contracts/echeance-proche', { params });
  }

  exportCsv(options?: ContractExportOptions): Observable<Blob> {
    let params = new HttpParams();
    if (options?.q) params = params.set('q', options.q);
    if (options?.status) params = params.set('status', options.status);
    if (options?.commissionStatus) params = params.set('commissionStatus', options.commissionStatus);
    if (options?.productId !== undefined) params = params.set('productId', options.productId);
    if (options?.insuredId !== undefined) params = params.set('insuredId', options.insuredId);
    if (options?.salesRepId !== undefined) params = params.set('salesRepId', options.salesRepId);
    return this.http.get('/contracts/export', { params, responseType: 'blob' });
  }

  listFractionnes(): Observable<ContractFractionneSummaryResponse[]> {
    return this.http.get<ContractFractionneSummaryResponse[]>('/contracts/fractionnes');
  }

  getEcheances(contractId: number): Observable<EcheanceResponse[]> {
    return this.http.get<EcheanceResponse[]>(`/contracts/${contractId}/echeances`);
  }

  listPaiements(contractId: number): Observable<PaiementResponse[]> {
    return this.http.get<PaiementResponse[]>(`/contracts/${contractId}/paiements`);
  }

  createPaiement(contractId: number, payload: PaiementRequest): Observable<PaiementResponse> {
    return this.http.post<PaiementResponse>(`/contracts/${contractId}/paiements`, payload);
  }

  /** Justificatif joint à un paiement — voir docs/api-contracts/paiement-piece-jointe.md.
   * Endpoint pas encore livré côté backend au moment de l'écriture. */
  listPaiementPiecesJointes(contractId: number, paiementId: number): Observable<PaiementPieceJointeResponse[]> {
    return this.http.get<PaiementPieceJointeResponse[]>(`/contracts/${contractId}/paiements/${paiementId}/pieces-jointes`);
  }

  uploadPaiementPieceJointe(contractId: number, paiementId: number, file: File): Observable<PaiementPieceJointeResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<PaiementPieceJointeResponse>(`/contracts/${contractId}/paiements/${paiementId}/pieces-jointes`, formData);
  }

  downloadPaiementPieceJointe(contractId: number, paiementId: number, pieceId: number): Observable<Blob> {
    return this.http.get(`/contracts/${contractId}/paiements/${paiementId}/pieces-jointes/${pieceId}`, { responseType: 'blob' });
  }

  getMessages(contractId: number): Observable<ContractMessageResponse[]> {
    return this.http.get<ContractMessageResponse[]>(`/contracts/${contractId}/messages`);
  }

  /** Contrats signés, tous partenaires confondus, dont la prime encaissée n'a pas encore été
   * reversée à la compagnie — réservé aux administrateurs. */
  listPrimeNonReversee(page: number, size: number): Observable<ContractPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ContractPage>('/contracts/prime-non-reversee', { params });
  }

  /** Marque la prime d'un contrat comme reversée par le partenaire à la compagnie. */
  reverserPrime(contractId: number): Observable<ContractResponse> {
    return this.http.post<ContractResponse>(`/contracts/${contractId}/prime/reverser`, {});
  }
}
