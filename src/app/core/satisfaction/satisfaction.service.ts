import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  EnqueteContexte,
  EnquetePage,
  EnqueteReponseRequest,
  EnqueteSatisfactionRequest,
  EnqueteSatisfactionResponse,
  EnqueteStatut,
} from './models/satisfaction.model';

export interface EnqueteListOptions {
  statut?: EnqueteStatut;
  contexte?: EnqueteContexte;
  insuredId?: number;
}

@Injectable({ providedIn: 'root' })
export class SatisfactionService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, options?: EnqueteListOptions): Observable<EnquetePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.statut) params = params.set('statut', options.statut);
    if (options?.contexte) params = params.set('contexte', options.contexte);
    if (options?.insuredId !== undefined) params = params.set('insuredId', options.insuredId);
    return this.http.get<EnquetePage>('/enquetes-satisfaction', { params });
  }

  /** Small dataset — pages fetch everything once and filter/aggregate client-side. */
  listAll(): Observable<EnquetePage> {
    return this.list(0, 1000);
  }

  get(id: number): Observable<EnqueteSatisfactionResponse> {
    return this.http.get<EnqueteSatisfactionResponse>(`/enquetes-satisfaction/${id}`);
  }

  envoyer(payload: EnqueteSatisfactionRequest): Observable<EnqueteSatisfactionResponse> {
    return this.http.post<EnqueteSatisfactionResponse>('/enquetes-satisfaction', payload);
  }

  repondre(id: number, payload: EnqueteReponseRequest): Observable<EnqueteSatisfactionResponse> {
    return this.http.patch<EnqueteSatisfactionResponse>(`/enquetes-satisfaction/${id}/reponse`, payload);
  }
}
