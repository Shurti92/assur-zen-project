import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CompteFidelitePage,
  CompteFideliteResponse,
  EchangeStatut,
  FidelisationAjustementRequest,
  FidelisationHistoriqueResponse,
  GestionnaireDedieRequest,
  PalierFideliteRequest,
  PalierFideliteResponse,
  RecompenseEchangeRequest,
  RecompenseEchangeResponse,
  RecompenseRequest,
  RecompenseResponse,
  StatutVip,
  StatutVipRequest,
} from './models/fidelite.model';

export interface CompteListOptions {
  statutVip?: StatutVip;
  palierId?: number;
}

@Injectable({ providedIn: 'root' })
export class FideliteService {
  constructor(private readonly http: HttpClient) {}

  listPaliers(): Observable<PalierFideliteResponse[]> {
    return this.http.get<PalierFideliteResponse[]>('/fidelite/paliers');
  }

  getPalier(id: number): Observable<PalierFideliteResponse> {
    return this.http.get<PalierFideliteResponse>(`/fidelite/paliers/${id}`);
  }

  createPalier(payload: PalierFideliteRequest): Observable<PalierFideliteResponse> {
    return this.http.post<PalierFideliteResponse>('/fidelite/paliers', payload);
  }

  updatePalier(id: number, payload: PalierFideliteRequest): Observable<PalierFideliteResponse> {
    return this.http.put<PalierFideliteResponse>(`/fidelite/paliers/${id}`, payload);
  }

  deactivatePalier(id: number): Observable<void> {
    return this.http.delete<void>(`/fidelite/paliers/${id}`);
  }

  listRecompenses(): Observable<RecompenseResponse[]> {
    return this.http.get<RecompenseResponse[]>('/fidelite/recompenses');
  }

  getRecompense(id: number): Observable<RecompenseResponse> {
    return this.http.get<RecompenseResponse>(`/fidelite/recompenses/${id}`);
  }

  createRecompense(payload: RecompenseRequest): Observable<RecompenseResponse> {
    return this.http.post<RecompenseResponse>('/fidelite/recompenses', payload);
  }

  updateRecompense(id: number, payload: RecompenseRequest): Observable<RecompenseResponse> {
    return this.http.put<RecompenseResponse>(`/fidelite/recompenses/${id}`, payload);
  }

  deleteRecompense(id: number): Observable<void> {
    return this.http.delete<void>(`/fidelite/recompenses/${id}`);
  }

  echangerRecompense(payload: RecompenseEchangeRequest): Observable<RecompenseEchangeResponse> {
    return this.http.post<RecompenseEchangeResponse>('/fidelite/echanges', payload);
  }

  traiterEchange(id: number, statut: EchangeStatut): Observable<RecompenseEchangeResponse> {
    return this.http.patch<RecompenseEchangeResponse>(`/fidelite/echanges/${id}`, null, { params: { statut } });
  }

  getCompte(insuredId: number): Observable<CompteFideliteResponse> {
    return this.http.get<CompteFideliteResponse>(`/fidelite/comptes/${insuredId}`);
  }

  adherer(insuredId: number): Observable<CompteFideliteResponse> {
    return this.http.post<CompteFideliteResponse>(`/fidelite/comptes/${insuredId}/adherer`, {});
  }

  getHistorique(insuredId: number): Observable<FidelisationHistoriqueResponse[]> {
    return this.http.get<FidelisationHistoriqueResponse[]>(`/fidelite/comptes/${insuredId}/historique`);
  }

  ajuster(payload: FidelisationAjustementRequest): Observable<FidelisationHistoriqueResponse> {
    return this.http.post<FidelisationHistoriqueResponse>('/fidelite/ajustements', payload);
  }

  listComptes(page: number, size: number, options?: CompteListOptions): Observable<CompteFidelitePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.statutVip) params = params.set('statutVip', options.statutVip);
    if (options?.palierId !== undefined) params = params.set('palierId', options.palierId);
    return this.http.get<CompteFidelitePage>('/fidelite/comptes', { params });
  }

  updateStatutVip(insuredId: number, payload: StatutVipRequest): Observable<CompteFideliteResponse> {
    return this.http.patch<CompteFideliteResponse>(`/fidelite/comptes/${insuredId}/statut-vip`, payload);
  }

  assignGestionnaire(insuredId: number, payload: GestionnaireDedieRequest): Observable<CompteFideliteResponse> {
    return this.http.patch<CompteFideliteResponse>(`/fidelite/comptes/${insuredId}/gestionnaire`, payload);
  }

  recalculerScore(insuredId: number): Observable<CompteFideliteResponse> {
    return this.http.post<CompteFideliteResponse>(`/fidelite/comptes/${insuredId}/recalculer-score`, {});
  }
}
