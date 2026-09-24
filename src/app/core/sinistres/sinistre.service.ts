import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  SinistrePage,
  SinistrePhotoResponse,
  SinistreRequest,
  SinistreResponse,
  SinistreSuiviRequest,
  SinistreSuiviResponse,
} from './models/sinistre.model';

export interface SinistreListOptions {
  q?: string;
  status?: string;
}

@Injectable({ providedIn: 'root' })
export class SinistreService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, options?: SinistreListOptions): Observable<SinistrePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.q) params = params.set('q', options.q);
    if (options?.status) params = params.set('status', options.status);
    return this.http.get<SinistrePage>('/sinistres', { params });
  }

  declare(payload: SinistreRequest): Observable<SinistreResponse> {
    return this.http.post<SinistreResponse>('/sinistres', payload);
  }

  getById(id: number): Observable<SinistreResponse> {
    return this.http.get<SinistreResponse>(`/sinistres/${id}`);
  }

  /** Met à jour les informations descriptives d'un sinistre — le statut se change via `addSuivi()`. */
  update(id: number, payload: SinistreRequest): Observable<SinistreResponse> {
    return this.http.put<SinistreResponse>(`/sinistres/${id}`, payload);
  }

  getSuivis(sinistreId: number): Observable<SinistreSuiviResponse[]> {
    return this.http.get<SinistreSuiviResponse[]>(`/sinistres/${sinistreId}/suivis`);
  }

  /** Advances the claim to a new status — the backend also sends an SMS
   * notification to the insured when this succeeds. */
  addSuivi(sinistreId: number, payload: SinistreSuiviRequest): Observable<SinistreSuiviResponse> {
    return this.http.post<SinistreSuiviResponse>(`/sinistres/${sinistreId}/suivis`, payload);
  }

  listPhotos(sinistreId: number): Observable<SinistrePhotoResponse[]> {
    return this.http.get<SinistrePhotoResponse[]>(`/sinistres/${sinistreId}/photos`);
  }

  uploadPhoto(sinistreId: number, file: File): Observable<SinistrePhotoResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<SinistrePhotoResponse>(`/sinistres/${sinistreId}/photos`, formData);
  }

  downloadPhoto(sinistreId: number, photoId: number): Observable<Blob> {
    return this.http.get(`/sinistres/${sinistreId}/photos/${photoId}`, { responseType: 'blob' });
  }
}
