import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ProduitPage, ProduitRequest, ProduitResponse } from './models/produit.model';

@Injectable({ providedIn: 'root' })
export class ProduitService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, q?: string, active?: boolean, productTypeCode?: string): Observable<ProduitPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== undefined) params = params.set('active', active);
    if (productTypeCode) params = params.set('productTypeCode', productTypeCode);
    return this.http.get<ProduitPage>('/produits', { params });
  }

  create(payload: ProduitRequest): Observable<ProduitResponse> {
    return this.http.post<ProduitResponse>('/produits', payload);
  }

  update(id: number, payload: ProduitRequest): Observable<ProduitResponse> {
    return this.http.put<ProduitResponse>(`/produits/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/produits/${id}`);
  }

  /** Exporte les produits (et filtres courants) au format CSV. */
  export(q?: string, active?: boolean): Observable<Blob> {
    let params = new HttpParams();
    if (q) params = params.set('q', q);
    if (active !== undefined) params = params.set('active', active);
    return this.http.get('/produits/export', { params, responseType: 'blob' });
  }
}
