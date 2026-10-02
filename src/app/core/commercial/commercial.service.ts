import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CommercialBonusPaymentRequest,
  CommercialBonusPaymentResponse,
  CommercialFilter,
  CommercialManagementDashboardResponse,
  CommercialMonthlyObjectiveRequest,
  CommercialMonthlyObjectiveResponse,
  CommercialMonthlyObjectiveRevisionRequest,
  CommercialObjectiveRequest,
  CommercialObjectiveResponse,
  CommercialPage,
  CommercialPayrollEntry,
  CommercialPerformanceResponse,
  CommercialPerformanceStatusRequest,
  CommercialRankingEntry,
  CommercialRequest,
  CommercialResponse,
  CommercialStatusRequest,
  CommercialUpdateRequest,
} from './models/commercial.model';

@Injectable({ providedIn: 'root' })
export class CommercialService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, filters?: CommercialFilter): Observable<CommercialPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters?.statut) params = params.set('statut', filters.statut);
    if (filters?.villePartenaireId) params = params.set('villePartenaireId', filters.villePartenaireId);
    return this.http.get<CommercialPage>('/commercials', { params });
  }

  get(id: number): Observable<CommercialResponse> {
    return this.http.get<CommercialResponse>(`/commercials/${id}`);
  }

  create(payload: CommercialRequest): Observable<CommercialResponse> {
    return this.http.post<CommercialResponse>('/commercials', payload);
  }

  update(id: number, payload: CommercialUpdateRequest): Observable<CommercialResponse> {
    return this.http.put<CommercialResponse>(`/commercials/${id}`, payload);
  }

  updateStatut(id: number, payload: CommercialStatusRequest): Observable<CommercialResponse> {
    return this.http.patch<CommercialResponse>(`/commercials/${id}/statut`, payload);
  }

  listObjectives(commercialId: number): Observable<CommercialObjectiveResponse[]> {
    return this.http.get<CommercialObjectiveResponse[]>(`/commercials/${commercialId}/objectives`);
  }

  createObjective(
    commercialId: number,
    payload: CommercialObjectiveRequest,
  ): Observable<CommercialObjectiveResponse> {
    return this.http.post<CommercialObjectiveResponse>(`/commercials/${commercialId}/objectives`, payload);
  }

  updateObjective(
    commercialId: number,
    objectiveId: number,
    payload: CommercialObjectiveRequest,
  ): Observable<CommercialObjectiveResponse> {
    return this.http.put<CommercialObjectiveResponse>(
      `/commercials/${commercialId}/objectives/${objectiveId}`,
      payload,
    );
  }

  listMonthlyObjectives(commercialId: number): Observable<CommercialMonthlyObjectiveResponse[]> {
    return this.http.get<CommercialMonthlyObjectiveResponse[]>(
      `/commercials/${commercialId}/monthly-objectives`,
    );
  }

  createMonthlyObjective(
    commercialId: number,
    payload: CommercialMonthlyObjectiveRequest,
  ): Observable<CommercialMonthlyObjectiveResponse> {
    return this.http.post<CommercialMonthlyObjectiveResponse>(
      `/commercials/${commercialId}/monthly-objectives`,
      payload,
    );
  }

  /** Révision d'un objectif mensuel déjà défini — motif obligatoire, tracé par l'audit fonctionnel. */
  reviseMonthlyObjective(
    commercialId: number,
    objectiveId: number,
    payload: CommercialMonthlyObjectiveRevisionRequest,
  ): Observable<CommercialMonthlyObjectiveResponse> {
    return this.http.put<CommercialMonthlyObjectiveResponse>(
      `/commercials/${commercialId}/monthly-objectives/${objectiveId}`,
      payload,
    );
  }

  listPerformance(commercialId: number): Observable<CommercialPerformanceResponse[]> {
    return this.http.get<CommercialPerformanceResponse[]>(`/commercials/${commercialId}/performance`);
  }

  recalculatePerformance(commercialId: number, moisComptableId: number): Observable<CommercialPerformanceResponse> {
    return this.http.post<CommercialPerformanceResponse>(
      `/commercials/${commercialId}/performance/${moisComptableId}/recalculate`,
      {},
    );
  }

  updatePerformanceStatut(
    commercialId: number,
    moisComptableId: number,
    payload: CommercialPerformanceStatusRequest,
  ): Observable<CommercialPerformanceResponse> {
    return this.http.patch<CommercialPerformanceResponse>(
      `/commercials/${commercialId}/performance/${moisComptableId}/statut`,
      payload,
    );
  }

  /** Paiement réel du bonus validé (virement SycaPay, ou enregistrement hors application si SycaPay
   * est désactivé) — toujours lire `sycapayStatus` de la réponse. */
  payBonus(
    commercialId: number,
    moisComptableId: number,
    payload: CommercialBonusPaymentRequest,
  ): Observable<CommercialBonusPaymentResponse> {
    return this.http.post<CommercialBonusPaymentResponse>(
      `/commercials/${commercialId}/performance/${moisComptableId}/bonus/pay`,
      payload,
    );
  }

  /** Historique des paiements de bonus (payés, en cours, échoués), tous commerciaux ou un seul. */
  bonusPayments(commercialId?: number): Observable<CommercialPerformanceResponse[]> {
    let params = new HttpParams();
    if (commercialId) params = params.set('commercialId', commercialId);
    return this.http.get<CommercialPerformanceResponse[]>('/commercial-performance/bonus-payments', { params });
  }

  managementDashboard(moisComptableId?: number): Observable<CommercialManagementDashboardResponse> {
    let params = new HttpParams();
    if (moisComptableId) params = params.set('moisComptableId', moisComptableId);
    return this.http.get<CommercialManagementDashboardResponse>('/commercial-performance/dashboard', { params });
  }

  ranking(moisComptableId?: number): Observable<CommercialRankingEntry[]> {
    let params = new HttpParams();
    if (moisComptableId) params = params.set('moisComptableId', moisComptableId);
    return this.http.get<CommercialRankingEntry[]>('/commercial-performance/ranking', { params });
  }

  exportPerformance(filters?: { commercialId?: number; moisComptableId?: number }): Observable<Blob> {
    let params = new HttpParams();
    if (filters?.commercialId) params = params.set('commercialId', filters.commercialId);
    if (filters?.moisComptableId) params = params.set('moisComptableId', filters.moisComptableId);
    return this.http.get('/commercial-performance/export', { params, responseType: 'blob' });
  }

  payroll(moisComptableId: number): Observable<CommercialPayrollEntry[]> {
    const params = new HttpParams().set('moisComptableId', moisComptableId);
    return this.http.get<CommercialPayrollEntry[]>('/commercial-payroll', { params });
  }
}
