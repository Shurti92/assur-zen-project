import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CampagneDestinataireResponse,
  CampagneLancementRequest,
  CampagneMarketingRequest,
  CampagneMarketingResponse,
  CampagnePage,
  CampagneStatut,
  CampagneType,
} from './models/campagne.model';

export interface CampagneListOptions {
  statut?: CampagneStatut;
  type?: CampagneType;
}

@Injectable({ providedIn: 'root' })
export class CampagneService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, options?: CampagneListOptions): Observable<CampagnePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.statut) params = params.set('statut', options.statut);
    if (options?.type) params = params.set('type', options.type);
    return this.http.get<CampagnePage>('/campagnes', { params });
  }

  /** Small dataset — pages fetch everything once and filter/aggregate client-side,
   * same approach as ContractService.listAll(). */
  listAll(): Observable<CampagnePage> {
    return this.list(0, 1000);
  }

  get(id: number): Observable<CampagneMarketingResponse> {
    return this.http.get<CampagneMarketingResponse>(`/campagnes/${id}`);
  }

  create(payload: CampagneMarketingRequest): Observable<CampagneMarketingResponse> {
    return this.http.post<CampagneMarketingResponse>('/campagnes', payload);
  }

  update(id: number, payload: CampagneMarketingRequest): Observable<CampagneMarketingResponse> {
    return this.http.put<CampagneMarketingResponse>(`/campagnes/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/campagnes/${id}`);
  }

  lancer(id: number, payload?: CampagneLancementRequest): Observable<CampagneMarketingResponse> {
    return this.http.post<CampagneMarketingResponse>(`/campagnes/${id}/lancer`, payload ?? {});
  }

  annuler(id: number): Observable<CampagneMarketingResponse> {
    return this.http.post<CampagneMarketingResponse>(`/campagnes/${id}/annuler`, {});
  }

  listDestinataires(id: number): Observable<CampagneDestinataireResponse[]> {
    return this.http.get<CampagneDestinataireResponse[]>(`/campagnes/${id}/destinataires`);
  }
}
