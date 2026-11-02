import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CrossSellAffectationRequest,
  CrossSellHistoryResponse,
  CrossSellOpportunityPage,
  CrossSellOpportunityRequest,
  CrossSellOpportunityResponse,
  CrossSellParameterRequest,
  CrossSellParameterResponse,
  CrossSellRuleConditionRequest,
  CrossSellRuleConditionResponse,
  CrossSellRuleRequest,
  CrossSellRuleResponse,
  CrossSellStatutRequest,
} from './models/cross-sell.model';

export interface CrossSellOpportunityListOptions {
  statut?: string;
  priorite?: string;
  commercialId?: number;
  insuredId?: number;
}

@Injectable({ providedIn: 'root' })
export class CrossSellService {
  constructor(private readonly http: HttpClient) {}

  listOpportunites(page: number, size: number, options?: CrossSellOpportunityListOptions): Observable<CrossSellOpportunityPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.statut) params = params.set('statut', options.statut);
    if (options?.priorite) params = params.set('priorite', options.priorite);
    if (options?.commercialId !== undefined) params = params.set('commercialId', options.commercialId);
    if (options?.insuredId !== undefined) params = params.set('insuredId', options.insuredId);
    return this.http.get<CrossSellOpportunityPage>('/cross-sell/opportunites', { params });
  }

  /** Fetches every opportunity in one request for client-side filtering. */
  listAllOpportunites(): Observable<CrossSellOpportunityPage> {
    return this.listOpportunites(0, 1000);
  }

  getOpportunite(id: number): Observable<CrossSellOpportunityResponse> {
    return this.http.get<CrossSellOpportunityResponse>(`/cross-sell/opportunites/${id}`);
  }

  createOpportunite(payload: CrossSellOpportunityRequest): Observable<CrossSellOpportunityResponse> {
    return this.http.post<CrossSellOpportunityResponse>('/cross-sell/opportunites', payload);
  }

  /** Auto-generates opportunities by running the active rules — for one insured if given, otherwise the whole portfolio. */
  genererOpportunites(insuredId?: number): Observable<CrossSellOpportunityResponse[]> {
    let params = new HttpParams();
    if (insuredId !== undefined) params = params.set('insuredId', insuredId);
    return this.http.post<CrossSellOpportunityResponse[]>('/cross-sell/opportunites/generer', {}, { params });
  }

  changerStatut(id: number, payload: CrossSellStatutRequest): Observable<CrossSellOpportunityResponse> {
    return this.http.patch<CrossSellOpportunityResponse>(`/cross-sell/opportunites/${id}/statut`, payload);
  }

  affecter(id: number, payload: CrossSellAffectationRequest): Observable<CrossSellOpportunityResponse> {
    return this.http.patch<CrossSellOpportunityResponse>(`/cross-sell/opportunites/${id}/affectation`, payload);
  }

  getHistorique(id: number): Observable<CrossSellHistoryResponse[]> {
    return this.http.get<CrossSellHistoryResponse[]>(`/cross-sell/opportunites/${id}/historique`);
  }

  listRegles(): Observable<CrossSellRuleResponse[]> {
    return this.http.get<CrossSellRuleResponse[]>('/cross-sell/regles');
  }

  getRegle(id: number): Observable<CrossSellRuleResponse> {
    return this.http.get<CrossSellRuleResponse>(`/cross-sell/regles/${id}`);
  }

  createRegle(payload: CrossSellRuleRequest): Observable<CrossSellRuleResponse> {
    return this.http.post<CrossSellRuleResponse>('/cross-sell/regles', payload);
  }

  updateRegle(id: number, payload: CrossSellRuleRequest): Observable<CrossSellRuleResponse> {
    return this.http.put<CrossSellRuleResponse>(`/cross-sell/regles/${id}`, payload);
  }

  deleteRegle(id: number): Observable<void> {
    return this.http.delete<void>(`/cross-sell/regles/${id}`);
  }

  dupliquerRegle(id: number): Observable<CrossSellRuleResponse> {
    return this.http.post<CrossSellRuleResponse>(`/cross-sell/regles/${id}/dupliquer`, {});
  }

  listConditions(regleId: number): Observable<CrossSellRuleConditionResponse[]> {
    return this.http.get<CrossSellRuleConditionResponse[]>(`/cross-sell/regles/${regleId}/conditions`);
  }

  addCondition(regleId: number, payload: CrossSellRuleConditionRequest): Observable<CrossSellRuleConditionResponse> {
    return this.http.post<CrossSellRuleConditionResponse>(`/cross-sell/regles/${regleId}/conditions`, payload);
  }

  listParametres(): Observable<CrossSellParameterResponse[]> {
    return this.http.get<CrossSellParameterResponse[]>('/cross-sell/parametres');
  }

  updateParametre(payload: CrossSellParameterRequest): Observable<CrossSellParameterResponse> {
    return this.http.put<CrossSellParameterResponse>('/cross-sell/parametres', payload);
  }
}
