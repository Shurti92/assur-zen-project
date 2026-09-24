import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ProduitCommissionTauxRequest,
  ProduitCommissionTauxResponse,
} from './models/produit-commission-taux.model';

@Injectable({ providedIn: 'root' })
export class ProduitCommissionTauxService {
  constructor(private readonly http: HttpClient) {}

  /** GET /produits-commission-taux supports optional produitId/typePartenaire/partenaireId
   * filters but no pagination — fetch everything and filter client-side. */
  list(): Observable<ProduitCommissionTauxResponse[]> {
    return this.http.get<ProduitCommissionTauxResponse[]>('/produits-commission-taux');
  }

  create(payload: ProduitCommissionTauxRequest): Observable<ProduitCommissionTauxResponse> {
    return this.http.post<ProduitCommissionTauxResponse>('/produits-commission-taux', payload);
  }

  update(id: number, payload: ProduitCommissionTauxRequest): Observable<ProduitCommissionTauxResponse> {
    return this.http.put<ProduitCommissionTauxResponse>(`/produits-commission-taux/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/produits-commission-taux/${id}`);
  }
}
