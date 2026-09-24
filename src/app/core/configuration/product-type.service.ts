import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ProductTypePage,
  ProductTypeRequest,
  ProductTypeResponse,
  ProductTypeSummaryResponse,
} from './models/product-type.model';
import { ProduitResponse } from './models/produit.model';

export interface ProductTypeListOptions {
  q?: string;
  actif?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ProductTypeService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, options?: ProductTypeListOptions): Observable<ProductTypePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.q) params = params.set('q', options.q);
    if (options?.actif !== undefined) params = params.set('actif', options.actif);
    return this.http.get<ProductTypePage>('/product-types', { params });
  }

  listActive(): Observable<ProductTypeSummaryResponse[]> {
    return this.http.get<ProductTypeSummaryResponse[]>('/product-types/active');
  }

  get(id: number): Observable<ProductTypeResponse> {
    return this.http.get<ProductTypeResponse>(`/product-types/${id}`);
  }

  create(payload: ProductTypeRequest): Observable<ProductTypeResponse> {
    return this.http.post<ProductTypeResponse>('/product-types', payload);
  }

  update(id: number, payload: ProductTypeRequest): Observable<ProductTypeResponse> {
    return this.http.put<ProductTypeResponse>(`/product-types/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/product-types/${id}`);
  }

  enable(id: number): Observable<ProductTypeResponse> {
    return this.http.patch<ProductTypeResponse>(`/product-types/${id}/enable`, {});
  }

  disable(id: number): Observable<ProductTypeResponse> {
    return this.http.patch<ProductTypeResponse>(`/product-types/${id}/disable`, {});
  }

  getProducts(id: number): Observable<ProduitResponse[]> {
    return this.http.get<ProduitResponse[]>(`/product-types/${id}/products`);
  }
}
