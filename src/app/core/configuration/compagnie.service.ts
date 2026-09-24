import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { CompagniePage, CompagnieRequest, CompagnieResponse } from './models/compagnie.model';

@Injectable({ providedIn: 'root' })
export class CompagnieService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, q?: string, active?: boolean): Observable<CompagniePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== undefined) params = params.set('active', active);
    return this.http.get<CompagniePage>('/compagnies', { params });
  }

  create(payload: CompagnieRequest): Observable<CompagnieResponse> {
    return this.http.post<CompagnieResponse>('/compagnies', payload);
  }

  update(id: number, payload: CompagnieRequest): Observable<CompagnieResponse> {
    return this.http.put<CompagnieResponse>(`/compagnies/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/compagnies/${id}`);
  }
}
