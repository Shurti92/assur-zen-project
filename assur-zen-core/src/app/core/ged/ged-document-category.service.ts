import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  GedDocumentCategoryPage,
  GedDocumentCategoryRequest,
  GedDocumentCategoryResponse,
} from './models/ged-document-category.model';

@Injectable({ providedIn: 'root' })
export class GedDocumentCategoryService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, q?: string, actif?: boolean): Observable<GedDocumentCategoryPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (actif !== undefined) params = params.set('actif', actif);
    return this.http.get<GedDocumentCategoryPage>('/ged/document-categories', { params });
  }

  create(payload: GedDocumentCategoryRequest): Observable<GedDocumentCategoryResponse> {
    return this.http.post<GedDocumentCategoryResponse>('/ged/document-categories', payload);
  }

  update(id: number, payload: GedDocumentCategoryRequest): Observable<GedDocumentCategoryResponse> {
    return this.http.put<GedDocumentCategoryResponse>(`/ged/document-categories/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/ged/document-categories/${id}`);
  }
}
