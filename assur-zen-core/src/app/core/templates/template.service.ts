import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CommunicationTemplatePatchRequest,
  CommunicationTemplateRequest,
  CommunicationTemplateResponse,
  TemplateCanal,
  TemplateCategoryRequest,
  TemplateCategoryResponse,
  TemplatePage,
  TemplatePreviewRequest,
  TemplatePreviewResponse,
  TemplateStatut,
  TemplateVariableRequest,
  TemplateVariableResponse,
} from './models/template.model';

export interface TemplateListOptions {
  canal?: TemplateCanal;
  statut?: TemplateStatut;
  categorieId?: number;
  active?: boolean;
}

@Injectable({ providedIn: 'root' })
export class TemplateService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, options?: TemplateListOptions): Observable<TemplatePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.canal) params = params.set('canal', options.canal);
    if (options?.statut) params = params.set('statut', options.statut);
    if (options?.categorieId != null) params = params.set('categorieId', options.categorieId);
    if (options?.active != null) params = params.set('active', options.active);
    return this.http.get<TemplatePage>('/templates', { params });
  }

  /** Small dataset — pages fetch everything once and filter/aggregate client-side. */
  listAll(): Observable<TemplatePage> {
    return this.list(0, 1000);
  }

  get(id: number): Observable<CommunicationTemplateResponse> {
    return this.http.get<CommunicationTemplateResponse>(`/templates/${id}`);
  }

  create(payload: CommunicationTemplateRequest): Observable<CommunicationTemplateResponse> {
    return this.http.post<CommunicationTemplateResponse>('/templates', payload);
  }

  update(id: number, payload: CommunicationTemplateRequest): Observable<CommunicationTemplateResponse> {
    return this.http.put<CommunicationTemplateResponse>(`/templates/${id}`, payload);
  }

  patch(id: number, payload: CommunicationTemplatePatchRequest): Observable<CommunicationTemplateResponse> {
    return this.http.patch<CommunicationTemplateResponse>(`/templates/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/templates/${id}`);
  }

  enable(id: number): Observable<CommunicationTemplateResponse> {
    return this.http.patch<CommunicationTemplateResponse>(`/templates/${id}/enable`, {});
  }

  disable(id: number): Observable<CommunicationTemplateResponse> {
    return this.http.patch<CommunicationTemplateResponse>(`/templates/${id}/disable`, {});
  }

  dupliquer(id: number): Observable<CommunicationTemplateResponse> {
    return this.http.post<CommunicationTemplateResponse>(`/templates/${id}/dupliquer`, {});
  }

  apercu(id: number, payload: TemplatePreviewRequest): Observable<TemplatePreviewResponse> {
    return this.http.post<TemplatePreviewResponse>(`/templates/${id}/apercu`, payload);
  }

  listCategories(): Observable<TemplateCategoryResponse[]> {
    return this.http.get<TemplateCategoryResponse[]>('/template-categories');
  }

  createCategory(payload: TemplateCategoryRequest): Observable<TemplateCategoryResponse> {
    return this.http.post<TemplateCategoryResponse>('/template-categories', payload);
  }

  listVariables(): Observable<TemplateVariableResponse[]> {
    return this.http.get<TemplateVariableResponse[]>('/template-variables');
  }

  createVariable(payload: TemplateVariableRequest): Observable<TemplateVariableResponse> {
    return this.http.post<TemplateVariableResponse>('/template-variables', payload);
  }
}
