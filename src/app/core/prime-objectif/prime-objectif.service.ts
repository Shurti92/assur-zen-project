import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  PrimeObjectifPage,
  PrimeObjectifResponse,
  PrimeObjectifSimulationResponse,
  PrimeObjectifStatut,
  PrimeObjectifStatutRequest,
} from './models/prime-objectif.model';

@Injectable({ providedIn: 'root' })
export class PrimeObjectifService {
  constructor(private readonly http: HttpClient) {}

  simulate(partenaireId: number): Observable<PrimeObjectifSimulationResponse> {
    return this.http.get<PrimeObjectifSimulationResponse>(
      `/partenaires/${partenaireId}/prime-objectif/simulation`
    );
  }

  list(
    page: number,
    size: number,
    filters?: { partenaireId?: number; statut?: PrimeObjectifStatut }
  ): Observable<PrimeObjectifPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters?.partenaireId) params = params.set('partenaireId', filters.partenaireId);
    if (filters?.statut) params = params.set('statut', filters.statut);
    return this.http.get<PrimeObjectifPage>('/primes-objectif', { params });
  }

  create(partenaireId: number): Observable<PrimeObjectifResponse> {
    return this.http.post<PrimeObjectifResponse>('/primes-objectif', { partenaireId });
  }

  updateStatut(id: number, payload: PrimeObjectifStatutRequest): Observable<PrimeObjectifResponse> {
    return this.http.patch<PrimeObjectifResponse>(`/primes-objectif/${id}/statut`, payload);
  }
}
