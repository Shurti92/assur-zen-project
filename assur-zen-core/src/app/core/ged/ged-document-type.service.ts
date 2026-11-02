import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { GedDocumentTypePage, GedDocumentTypeRequest, GedDocumentTypeResponse } from './models/ged-document-type.model';

@Injectable({ providedIn: 'root' })
export class GedDocumentTypeService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, q?: string, actif?: boolean): Observable<GedDocumentTypePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (actif !== undefined) params = params.set('actif', actif);
    return this.http.get<GedDocumentTypePage>('/ged/document-types', { params });
  }

  create(payload: GedDocumentTypeRequest): Observable<GedDocumentTypeResponse> {
    return this.http.post<GedDocumentTypeResponse>('/ged/document-types', payload);
  }

  update(id: number, payload: GedDocumentTypeRequest): Observable<GedDocumentTypeResponse> {
    return this.http.put<GedDocumentTypeResponse>(`/ged/document-types/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/ged/document-types/${id}`);
  }
}
