import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { FundOutflowTypePage, FundOutflowTypeRequest, FundOutflowTypeResponse } from './models/fund-outflow-type.model';

@Injectable({ providedIn: 'root' })
export class FundOutflowTypeService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, q?: string, actif?: boolean): Observable<FundOutflowTypePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (actif !== undefined) params = params.set('actif', actif);
    return this.http.get<FundOutflowTypePage>('/fund-outflow-types', { params });
  }

  create(payload: FundOutflowTypeRequest): Observable<FundOutflowTypeResponse> {
    return this.http.post<FundOutflowTypeResponse>('/fund-outflow-types', payload);
  }

  update(id: number, payload: FundOutflowTypeRequest): Observable<FundOutflowTypeResponse> {
    return this.http.put<FundOutflowTypeResponse>(`/fund-outflow-types/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/fund-outflow-types/${id}`);
  }
}
