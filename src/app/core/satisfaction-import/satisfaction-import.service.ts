import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  SatisfactionImportErrorResponse,
  SatisfactionImportMappingProfileRequest,
  SatisfactionImportMappingProfileResponse,
  SatisfactionImportMappingRequest,
  SatisfactionImportMappingResponse,
  SatisfactionImportMappingValueRequest,
  SatisfactionImportMappingValueResponse,
  SatisfactionImportPage,
  SatisfactionImportReportResponse,
  SatisfactionImportResponse,
  SatisfactionImportStatut,
  SatisfactionImportUploadOptions,
} from './models/satisfaction-import.model';

export interface SatisfactionImportListOptions {
  statut?: SatisfactionImportStatut;
  mappingProfileId?: number;
  source?: string;
}

@Injectable({ providedIn: 'root' })
export class SatisfactionImportService {
  constructor(private readonly http: HttpClient) {}

  listProfiles(): Observable<SatisfactionImportMappingProfileResponse[]> {
    return this.http.get<SatisfactionImportMappingProfileResponse[]>('/satisfaction-mapping-profiles');
  }

  createProfile(payload: SatisfactionImportMappingProfileRequest): Observable<SatisfactionImportMappingProfileResponse> {
    return this.http.post<SatisfactionImportMappingProfileResponse>('/satisfaction-mapping-profiles', payload);
  }

  listMappings(profileId: number): Observable<SatisfactionImportMappingResponse[]> {
    return this.http.get<SatisfactionImportMappingResponse[]>(`/satisfaction-mapping-profiles/${profileId}/mappings`);
  }

  addMapping(profileId: number, payload: SatisfactionImportMappingRequest): Observable<SatisfactionImportMappingResponse> {
    return this.http.post<SatisfactionImportMappingResponse>(`/satisfaction-mapping-profiles/${profileId}/mappings`, payload);
  }

  updateMapping(
    profileId: number,
    mappingId: number,
    payload: SatisfactionImportMappingRequest
  ): Observable<SatisfactionImportMappingResponse> {
    return this.http.put<SatisfactionImportMappingResponse>(`/satisfaction-mapping-profiles/${profileId}/mappings/${mappingId}`, payload);
  }

  listMappingValues(profileId: number, mappingId: number): Observable<SatisfactionImportMappingValueResponse[]> {
    return this.http.get<SatisfactionImportMappingValueResponse[]>(
      `/satisfaction-mapping-profiles/${profileId}/mappings/${mappingId}/values`
    );
  }

  addMappingValue(
    profileId: number,
    mappingId: number,
    payload: SatisfactionImportMappingValueRequest
  ): Observable<SatisfactionImportMappingValueResponse> {
    return this.http.post<SatisfactionImportMappingValueResponse>(
      `/satisfaction-mapping-profiles/${profileId}/mappings/${mappingId}/values`,
      payload
    );
  }

  list(page: number, size: number, options?: SatisfactionImportListOptions): Observable<SatisfactionImportPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.statut) params = params.set('statut', options.statut);
    if (options?.mappingProfileId != null) params = params.set('mappingProfileId', options.mappingProfileId);
    if (options?.source) params = params.set('source', options.source);
    return this.http.get<SatisfactionImportPage>('/satisfaction-imports', { params });
  }

  /** History table — small dataset, fetch once client-side. */
  listAll(): Observable<SatisfactionImportPage> {
    return this.list(0, 200);
  }

  get(id: number): Observable<SatisfactionImportResponse> {
    return this.http.get<SatisfactionImportResponse>(`/satisfaction-imports/${id}`);
  }

  upload(file: File, options: SatisfactionImportUploadOptions): Observable<SatisfactionImportResponse> {
    const formData = new FormData();
    formData.append('file', file);

    let params = new HttpParams().set('mappingProfileId', options.mappingProfileId);
    if (options.surveyId) params = params.set('surveyId', options.surveyId);
    if (options.source) params = params.set('source', options.source);
    if (options.delimiter) params = params.set('delimiter', options.delimiter);
    if (options.encoding) params = params.set('encoding', options.encoding);
    if (options.hasHeader != null) params = params.set('hasHeader', options.hasHeader);
    if (options.duplicateStrategy) params = params.set('duplicateStrategy', options.duplicateStrategy);
    if (options.executeImmediately != null) params = params.set('executeImmediately', options.executeImmediately);

    return this.http.post<SatisfactionImportResponse>('/satisfaction-imports', formData, { params });
  }

  retry(id: number): Observable<SatisfactionImportResponse> {
    return this.http.post<SatisfactionImportResponse>(`/satisfaction-imports/${id}/retry`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/satisfaction-imports/${id}`);
  }

  getReport(id: number): Observable<SatisfactionImportReportResponse> {
    return this.http.get<SatisfactionImportReportResponse>(`/satisfaction-imports/${id}/report`);
  }

  getErrors(id: number): Observable<SatisfactionImportErrorResponse[]> {
    return this.http.get<SatisfactionImportErrorResponse[]>(`/satisfaction-imports/${id}/errors`);
  }
}
