import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { AvenantRequest, AvenantResponse } from './models/avenant.model';

@Injectable({ providedIn: 'root' })
export class AvenantService {
  constructor(private readonly http: HttpClient) {}

  /** GET /contracts/{id}/avenants — liste chronologique, pas de pagination. */
  listByContract(contractId: number): Observable<AvenantResponse[]> {
    return this.http.get<AvenantResponse[]>(`/contracts/${contractId}/avenants`);
  }

  /** POST /contracts/{id}/avenants — réservé aux administrateurs côté backend. */
  create(contractId: number, payload: AvenantRequest): Observable<AvenantResponse> {
    return this.http.post<AvenantResponse>(`/contracts/${contractId}/avenants`, payload);
  }
}
