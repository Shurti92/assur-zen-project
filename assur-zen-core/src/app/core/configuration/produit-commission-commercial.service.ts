import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ProduitCommissionCommercialRequest,
  ProduitCommissionCommercialResponse,
} from './models/produit-commission-commercial.model';

@Injectable({ providedIn: 'root' })
export class ProduitCommissionCommercialService {
  constructor(private readonly http: HttpClient) {}

  list(produitId?: number): Observable<ProduitCommissionCommercialResponse[]> {
    let params = new HttpParams();
    if (produitId !== undefined) params = params.set('produitId', produitId);
    return this.http.get<ProduitCommissionCommercialResponse[]>('/produits-commission-commercial', { params });
  }

  create(payload: ProduitCommissionCommercialRequest): Observable<ProduitCommissionCommercialResponse> {
    return this.http.post<ProduitCommissionCommercialResponse>('/produits-commission-commercial', payload);
  }

  update(id: number, payload: ProduitCommissionCommercialRequest): Observable<ProduitCommissionCommercialResponse> {
    return this.http.put<ProduitCommissionCommercialResponse>(`/produits-commission-commercial/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/produits-commission-commercial/${id}`);
  }
}
