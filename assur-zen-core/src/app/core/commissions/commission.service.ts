import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CommissionPaymentPage,
  CommissionPaymentRequest,
  CommissionPaymentResponse,
  ContractCommission,
  PartialPaymentRequestResponse,
  PartialPaymentRequestStatus,
  PartialPaymentStatusResponse,
  PartnerCommissionSummary,
  PaymentRequestValidationItem,
} from './models/commission.model';

@Injectable({ providedIn: 'root' })
export class CommissionService {
  constructor(private readonly http: HttpClient) {}

  getPartnersSummary(): Observable<PartnerCommissionSummary[]> {
    return this.http.get<PartnerCommissionSummary[]>('/commissions/partners');
  }

  getPartnerContracts(partenaireId: number, status?: string): Observable<ContractCommission[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<ContractCommission[]>(`/commissions/partners/${partenaireId}/contracts`, { params });
  }

  getPayments(page: number, size: number, partenaireId?: number): Observable<CommissionPaymentPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (partenaireId !== undefined) params = params.set('partenaireId', partenaireId);
    return this.http.get<CommissionPaymentPage>('/commissions/payments', { params });
  }

  pay(payload: CommissionPaymentRequest): Observable<CommissionPaymentResponse[]> {
    return this.http.post<CommissionPaymentResponse[]>('/commissions/pay', payload);
  }

  /** Solde en attente, quota journalier restant et historique des demandes de versement
   * anticipé d'un partenaire (2026-08-22). */
  getPartialPaymentStatus(partenaireId: number): Observable<PartialPaymentStatusResponse> {
    return this.http.get<PartialPaymentStatusResponse>(`/partenaires/${partenaireId}/commissions/partial-payment-requests`);
  }

  /** Demandes de paiement de TOUS les bénéficiaires (partenaires et commerciaux), plus récentes
   * d'abord — status absent = toutes. */
  getPaymentRequestsForValidation(status?: PartialPaymentRequestStatus): Observable<PaymentRequestValidationItem[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<PaymentRequestValidationItem[]>('/commissions/partial-payment-requests', { params });
  }

  /** Valide (admin/finance) une demande de versement anticipé en attente — déclenche le
   * versement effectif. */
  validatePartialPaymentRequest(id: number): Observable<PartialPaymentRequestResponse> {
    return this.http.post<PartialPaymentRequestResponse>(`/commissions/partial-payment-requests/${id}/valider`, {});
  }
}
