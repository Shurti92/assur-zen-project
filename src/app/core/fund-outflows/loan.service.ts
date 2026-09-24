import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreateLoanRepaymentRequest,
  EarlyRepaymentRequest,
  LoanBalanceResponse,
  LoanInstallmentResponse,
  LoanPage,
  LoanRepaymentResponse,
  LoanResponse,
  LoanSearchFilters,
} from './models/loan.model';

@Injectable({ providedIn: 'root' })
export class LoanService {
  constructor(private readonly http: HttpClient) {}

  search(page: number, size: number, filters?: LoanSearchFilters): Observable<LoanPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters) {
      for (const [key, value] of Object.entries(filters)) {
        if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
      }
    }
    return this.http.get<LoanPage>('/loans/search', { params });
  }

  overdue(page: number, size: number): Observable<LoanPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<LoanPage>('/loans/overdue', { params });
  }

  get(id: number): Observable<LoanResponse> {
    return this.http.get<LoanResponse>(`/loans/${id}`);
  }

  getInstallments(id: number): Observable<LoanInstallmentResponse[]> {
    return this.http.get<LoanInstallmentResponse[]>(`/loans/${id}/installments`);
  }

  getRepayments(id: number): Observable<LoanRepaymentResponse[]> {
    return this.http.get<LoanRepaymentResponse[]>(`/loans/${id}/repayments`);
  }

  /** Allocation FIFO automatique sur les échéances les plus anciennes — le backend refuse
   * (400) un montant supérieur au solde restant, toujours recalculé côté serveur. */
  createRepayment(id: number, payload: CreateLoanRepaymentRequest): Observable<LoanRepaymentResponse> {
    return this.http.post<LoanRepaymentResponse>(`/loans/${id}/repayments`, payload);
  }

  getBalance(id: number): Observable<LoanBalanceResponse> {
    return this.http.get<LoanBalanceResponse>(`/loans/${id}/balance`);
  }

  /** Solde le prêt avant terme — clôture automatique du prêt et de la sortie de fonds. */
  earlyRepayment(id: number, payload: EarlyRepaymentRequest): Observable<LoanRepaymentResponse> {
    return this.http.post<LoanRepaymentResponse>(`/loans/${id}/early-repayment`, payload);
  }
}
