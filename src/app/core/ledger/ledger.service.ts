import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  BalanceCompteResponse,
  CompteComptableResponse,
  EcriturePage,
  EcritureComptableResponse,
  GrandLivreLineResponse,
  TypeOperation,
} from './models/ledger.model';

export interface EcritureListOptions {
  contractId?: number;
  typeOperation?: TypeOperation;
}

@Injectable({ providedIn: 'root' })
export class LedgerService {
  constructor(private readonly http: HttpClient) {}

  listEcritures(page: number, size: number, options?: EcritureListOptions): Observable<EcriturePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.contractId !== undefined) params = params.set('contractId', options.contractId);
    if (options?.typeOperation) params = params.set('typeOperation', options.typeOperation);
    return this.http.get<EcriturePage>('/ledger/ecritures', { params });
  }

  /** Fetches every écriture (with embedded lignes) in one request — used for
   * client-side aggregation (dashboard KPIs/charts) since there is no dedicated
   * summary endpoint and the dataset here is small. */
  listAllEcritures(): Observable<EcriturePage> {
    return this.listEcritures(0, 1000);
  }

  getEcriture(id: number): Observable<EcritureComptableResponse> {
    return this.http.get<EcritureComptableResponse>(`/ledger/ecritures/${id}`);
  }

  /** GET /ledger/comptes has no query/pagination parameters — returns the full plan comptable. */
  listComptes(): Observable<CompteComptableResponse[]> {
    return this.http.get<CompteComptableResponse[]>('/ledger/comptes');
  }

  grandLivre(compteId: number): Observable<GrandLivreLineResponse[]> {
    return this.http.get<GrandLivreLineResponse[]>(`/ledger/comptes/${compteId}/grand-livre`);
  }

  /** GET /ledger/balance has no query/pagination parameters — returns the full balance. */
  balance(): Observable<BalanceCompteResponse[]> {
    return this.http.get<BalanceCompteResponse[]>('/ledger/balance');
  }
}
