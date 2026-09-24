import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { InsuredResponse } from '../insured/models/insured.model';
import {
  SegmentMembreResponse,
  SegmentMembresRequest,
  SegmentPage,
  SegmentPatchRequest,
  SegmentRequest,
  SegmentResponse,
  SegmentRuleRequest,
  SegmentRuleResponse,
  SegmentType,
} from './models/segment.model';

export interface SegmentListOptions {
  type?: SegmentType;
  active?: boolean;
}

@Injectable({ providedIn: 'root' })
export class SegmentService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, options?: SegmentListOptions): Observable<SegmentPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.type) params = params.set('type', options.type);
    if (options?.active != null) params = params.set('active', options.active);
    return this.http.get<SegmentPage>('/segments', { params });
  }

  /** Small dataset — pages fetch everything once and filter/aggregate client-side. */
  listAll(): Observable<SegmentPage> {
    return this.list(0, 1000);
  }

  get(id: number): Observable<SegmentResponse> {
    return this.http.get<SegmentResponse>(`/segments/${id}`);
  }

  create(payload: SegmentRequest): Observable<SegmentResponse> {
    return this.http.post<SegmentResponse>('/segments', payload);
  }

  update(id: number, payload: SegmentRequest): Observable<SegmentResponse> {
    return this.http.put<SegmentResponse>(`/segments/${id}`, payload);
  }

  patch(id: number, payload: SegmentPatchRequest): Observable<SegmentResponse> {
    return this.http.patch<SegmentResponse>(`/segments/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/segments/${id}`);
  }

  enable(id: number): Observable<SegmentResponse> {
    return this.http.patch<SegmentResponse>(`/segments/${id}/enable`, {});
  }

  disable(id: number): Observable<SegmentResponse> {
    return this.http.patch<SegmentResponse>(`/segments/${id}/disable`, {});
  }

  resolve(id: number): Observable<InsuredResponse[]> {
    return this.http.get<InsuredResponse[]>(`/segments/${id}/resolve`);
  }

  listRules(id: number): Observable<SegmentRuleResponse[]> {
    return this.http.get<SegmentRuleResponse[]>(`/segments/${id}/rules`);
  }

  addRule(id: number, payload: SegmentRuleRequest): Observable<SegmentRuleResponse> {
    return this.http.post<SegmentRuleResponse>(`/segments/${id}/rules`, payload);
  }

  updateRule(id: number, ruleId: number, payload: SegmentRuleRequest): Observable<SegmentRuleResponse> {
    return this.http.put<SegmentRuleResponse>(`/segments/${id}/rules/${ruleId}`, payload);
  }

  deleteRule(id: number, ruleId: number): Observable<void> {
    return this.http.delete<void>(`/segments/${id}/rules/${ruleId}`);
  }

  listMembres(id: number): Observable<SegmentMembreResponse[]> {
    return this.http.get<SegmentMembreResponse[]>(`/segments/${id}/membres`);
  }

  replaceMembres(id: number, payload: SegmentMembresRequest): Observable<SegmentMembreResponse[]> {
    return this.http.put<SegmentMembreResponse[]>(`/segments/${id}/membres`, payload);
  }
}
