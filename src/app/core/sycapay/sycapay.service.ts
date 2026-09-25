import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import {
  SycaPayBalanceResponse,
  SycaPayDashboard,
  SycaPayStatusCounts,
  SycaPayTransaction,
  SycaPayTransactionFilter,
  SycaPayTransactionPage,
} from './models/sycapay.model';

/** Tous les endpoints sont ADMIN-only côté backend (même JWT bearer que le reste) — le vrai
 * rempart reste le 403 backend, cf. _nav.ts ADMIN_ONLY_NAV_NAMES pour le masquage de menu. */
@Injectable({ providedIn: 'root' })
export class SycaPayService {
  constructor(private readonly http: HttpClient) {}

  getBalance(refresh = false): Observable<SycaPayBalanceResponse> {
    const params = new HttpParams().set('refresh', refresh);
    return this.http.get<SycaPayBalanceResponse>('/sycapay/balance', { params });
  }

  getDashboard(): Observable<SycaPayDashboard> {
    return this.http.get<SycaPayDashboard>('/sycapay/dashboard');
  }

  getTransactions(page: number, size: number, filter?: SycaPayTransactionFilter): Observable<SycaPayTransactionPage> {
    let params = this.filterParams(filter).set('page', page).set('size', size);
    if (filter?.status) params = params.set('status', filter.status);
    return this.http.get<SycaPayTransactionPage>('/sycapay/transactions', { params });
  }

  /** Compteurs par statut pour les mêmes filtres que getTransactions — le status est ignoré. */
  getStatusCounts(filter?: Omit<SycaPayTransactionFilter, 'status'>): Observable<SycaPayStatusCounts> {
    return this.http.get<SycaPayStatusCounts>('/sycapay/transactions/status-counts', { params: this.filterParams(filter) });
  }

  private filterParams(filter?: SycaPayTransactionFilter): HttpParams {
    let params = new HttpParams();
    if (filter?.reference) params = params.set('reference', filter.reference);
    if (filter?.beneficiaryType) params = params.set('beneficiaryType', filter.beneficiaryType);
    if (filter?.commercialId !== undefined) params = params.set('commercialId', filter.commercialId);
    if (filter?.partenaireId !== undefined) params = params.set('partenaireId', filter.partenaireId);
    if (filter?.contractId !== undefined) params = params.set('contractId', filter.contractId);
    if (filter?.dateStart) params = params.set('dateStart', filter.dateStart);
    if (filter?.dateEnd) params = params.set('dateEnd', filter.dateEnd);
    return params;
  }

  /** Transaction du contrat (au plus une par contrat), null s'il n'y en a pas. */
  getContractTransaction(contractId: number): Observable<SycaPayTransaction | null> {
    return this.getTransactions(0, 1, { contractId }).pipe(map((page) => page.content[0] ?? null));
  }

  getTransaction(id: number): Observable<SycaPayTransaction> {
    return this.http.get<SycaPayTransaction>(`/sycapay/transactions/${id}`);
  }

  /** Relance getStatusByOrderID côté SycaPay pour une transaction PROCESSING/UNKNOWN. Si
   * SycaPay ne renvoie toujours rien d'exploitable, la transaction revient inchangée (pas
   * d'erreur HTTP) — à traiter côté appelant comme "toujours en attente, réessayer plus tard". */
  reconcile(id: number): Observable<SycaPayTransaction> {
    return this.http.post<SycaPayTransaction>(`/sycapay/transactions/${id}/reconcile`, {});
  }
}
