import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { HistoriqueModificationPage } from '../audit-trail/models/audit-trail.model';
import {
  CancelFundOutflowRequest,
  CreateFundOutflowRequest,
  DisburseFundOutflowRequest,
  FundOutflowDisbursementResponse,
  FundOutflowPage,
  FundOutflowResponse,
  FundOutflowSearchFilters,
  RejectFundOutflowRequest,
  UpdateFundOutflowRequest,
} from './models/fund-outflow.model';

@Injectable({ providedIn: 'root' })
export class FundOutflowService {
  constructor(private readonly http: HttpClient) {}

  create(payload: CreateFundOutflowRequest): Observable<FundOutflowResponse> {
    return this.http.post<FundOutflowResponse>('/fund-outflows', payload);
  }

  get(id: number): Observable<FundOutflowResponse> {
    return this.http.get<FundOutflowResponse>(`/fund-outflows/${id}`);
  }

  search(page: number, size: number, filters?: FundOutflowSearchFilters): Observable<FundOutflowPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters) {
      for (const [key, value] of Object.entries(filters)) {
        if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
      }
    }
    return this.http.get<FundOutflowPage>('/fund-outflows/search', { params });
  }

  pendingApprovals(page: number, size: number): Observable<FundOutflowPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<FundOutflowPage>('/fund-outflows/pending-approvals', { params });
  }

  /** Modification possible uniquement à l'état DRAFT — le backend refuse sinon. */
  update(id: number, payload: UpdateFundOutflowRequest): Observable<FundOutflowResponse> {
    return this.http.put<FundOutflowResponse>(`/fund-outflows/${id}`, payload);
  }

  /** Suppression possible uniquement en DRAFT/CANCELLED/REJECTED. */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/fund-outflows/${id}`);
  }

  submit(id: number): Observable<FundOutflowResponse> {
    return this.http.post<FundOutflowResponse>(`/fund-outflows/${id}/submit`, {});
  }

  review(id: number): Observable<FundOutflowResponse> {
    return this.http.post<FundOutflowResponse>(`/fund-outflows/${id}/review`, {});
  }

  approve(id: number): Observable<FundOutflowResponse> {
    return this.http.post<FundOutflowResponse>(`/fund-outflows/${id}/approve`, {});
  }

  authorize(id: number): Observable<FundOutflowResponse> {
    return this.http.post<FundOutflowResponse>(`/fund-outflows/${id}/authorize`, {});
  }

  reject(id: number, payload: RejectFundOutflowRequest): Observable<FundOutflowResponse> {
    return this.http.post<FundOutflowResponse>(`/fund-outflows/${id}/reject`, payload);
  }

  cancel(id: number, payload: CancelFundOutflowRequest): Observable<FundOutflowResponse> {
    return this.http.post<FundOutflowResponse>(`/fund-outflows/${id}/cancel`, payload);
  }

  /** Depuis DISBURSED/REPAID/CANCELLED/REJECTED uniquement. */
  close(id: number): Observable<FundOutflowResponse> {
    return this.http.post<FundOutflowResponse>(`/fund-outflows/${id}/close`, {});
  }

  /** Uniquement possible depuis AUTHORIZED. idempotencyKey optionnelle (header dédié) évite un
   * double décaissement si la requête est rejouée. */
  disburse(id: number, payload: DisburseFundOutflowRequest, idempotencyKey?: string): Observable<FundOutflowDisbursementResponse> {
    const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined;
    return this.http.post<FundOutflowDisbursementResponse>(`/fund-outflows/${id}/disburse`, payload, { headers });
  }

  getHistory(id: number, page: number, size: number): Observable<HistoriqueModificationPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<HistoriqueModificationPage>(`/fund-outflows/${id}/history`, { params });
  }
}
