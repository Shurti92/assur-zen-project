import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { VillePartenaireRequest, VillePartenaireResponse } from './models/ville-partenaire.model';

@Injectable({ providedIn: 'root' })
export class VillePartenaireService {
  constructor(private readonly http: HttpClient) {}

  /** GET /ville-partenaires has no query/pagination parameters — returns the full list. */
  list(): Observable<VillePartenaireResponse[]> {
    return this.http.get<VillePartenaireResponse[]>('/ville-partenaires');
  }

  create(payload: VillePartenaireRequest): Observable<VillePartenaireResponse> {
    return this.http.post<VillePartenaireResponse>('/ville-partenaires', payload);
  }

  update(id: number, payload: VillePartenaireRequest): Observable<VillePartenaireResponse> {
    return this.http.put<VillePartenaireResponse>(`/ville-partenaires/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/ville-partenaires/${id}`);
  }
}
