import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CommercialCommissionPaymentPage,
  CommercialCommissionPaymentRequest,
  CommercialCommissionPaymentResponse,
  CommercialCommissionSummary,
  ContractCommercialCommission,
} from './models/commercial-commission.model';

@Injectable({ providedIn: 'root' })
export class CommercialCommissionService {
  constructor(private readonly http: HttpClient) {}

  getCommercialsSummary(): Observable<CommercialCommissionSummary[]> {
    return this.http.get<CommercialCommissionSummary[]>('/commercial-commissions/commercials');
  }

  getCommercialContracts(commercialId: number, status?: string): Observable<ContractCommercialCommission[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<ContractCommercialCommission[]>(
      `/commercial-commissions/commercials/${commercialId}/contracts`,
      { params },
    );
  }

  getPayments(page: number, size: number, commercialId?: number): Observable<CommercialCommissionPaymentPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (commercialId !== undefined) params = params.set('commercialId', commercialId);
    return this.http.get<CommercialCommissionPaymentPage>('/commercial-commissions/payments', { params });
  }

  pay(payload: CommercialCommissionPaymentRequest): Observable<CommercialCommissionPaymentResponse[]> {
    return this.http.post<CommercialCommissionPaymentResponse[]>('/commercial-commissions/pay', payload);
  }
}
