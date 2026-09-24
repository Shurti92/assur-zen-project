import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { InsuredFicheResponse, InsuredPage, InsuredRequest, InsuredResponse, TypeClient } from './models/insured.model';

@Injectable({ providedIn: 'root' })
export class InsuredService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, q?: string, typeClient?: TypeClient): Observable<InsuredPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (typeClient) params = params.set('typeClient', typeClient);
    return this.http.get<InsuredPage>('/insureds', { params });
  }

  get(id: number): Observable<InsuredResponse> {
    return this.http.get<InsuredResponse>(`/insureds/${id}`);
  }

  create(payload: InsuredRequest): Observable<InsuredResponse> {
    return this.http.post<InsuredResponse>('/insureds', payload);
  }

  update(id: number, payload: InsuredRequest): Observable<InsuredResponse> {
    return this.http.put<InsuredResponse>(`/insureds/${id}`, payload);
  }

  /** Fiche de sinistralité consolidée (contrats + sinistres + stats), optionnellement
   * filtrée par exercice comptable. */
  getFicheSinistralite(id: number, exerciceComptableId?: number): Observable<InsuredFicheResponse> {
    let params = new HttpParams();
    if (exerciceComptableId !== undefined) params = params.set('exerciceComptableId', exerciceComptableId);
    return this.http.get<InsuredFicheResponse>(`/insureds/${id}/fiche-sinistralite`, { params });
  }

  /** Export CSV de la liste globale des assurés. */
  export(): Observable<Blob> {
    return this.http.get('/insureds/export', { responseType: 'blob' });
  }
}
