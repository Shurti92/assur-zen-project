import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, throwError } from 'rxjs';

import {
  PartnerCommissionFilter,
  PartnerCommissionRequest,
  PartnerCommissionResponse,
} from './models/partner-commission.model';

/** Commissions configurées par produit pour un partenaire (ou son type) ou un commercial —
 * /api-assur-zen/partner-commissions, livré le 2026-09-25 (commit backend c0e3013). */
@Injectable({ providedIn: 'root' })
export class PartnerCommissionService {
  private readonly http = inject(HttpClient);

  list(filter: PartnerCommissionFilter = {}): Observable<PartnerCommissionResponse[]> {
    let params = new HttpParams();
    if (filter.produitId !== undefined) params = params.set('produitId', filter.produitId);
    if (filter.beneficiaire) params = params.set('beneficiaire', filter.beneficiaire);
    if (filter.partenaireId !== undefined) params = params.set('partenaireId', filter.partenaireId);
    if (filter.commercialId !== undefined) params = params.set('commercialId', filter.commercialId);
    return this.http.get<PartnerCommissionResponse[]>('/partner-commissions', { params });
  }

  get(id: number): Observable<PartnerCommissionResponse> {
    return this.http.get<PartnerCommissionResponse>(`/partner-commissions/${id}`);
  }

  create(payload: PartnerCommissionRequest): Observable<PartnerCommissionResponse> {
    return this.http.post<PartnerCommissionResponse>('/partner-commissions', payload);
  }

  update(id: number, payload: PartnerCommissionRequest): Observable<PartnerCommissionResponse> {
    return this.http.put<PartnerCommissionResponse>(`/partner-commissions/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/partner-commissions/${id}`);
  }

  /** Commission configurée pour ce partenaire précis, sinon pour son type. null si aucune
   * configuration (le backend répond 404) — les autres erreurs sont propagées. */
  configuredForPartner(productId: number, partnerId: number, date?: string): Observable<number | null> {
    return this.configuredAmount(`/partner-commissions/by-product/${productId}/partner/${partnerId}`, date);
  }

  /** Commission configurée pour ce commercial précis. null si aucune (404) : il faut alors
   * retomber sur GET /produits-commission-commercial?produitId=. */
  configuredForCommercial(productId: number, commercialId: number, date?: string): Observable<number | null> {
    return this.configuredAmount(`/partner-commissions/by-product/${productId}/commercial/${commercialId}`, date);
  }

  private configuredAmount(url: string, date?: string): Observable<number | null> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<PartnerCommissionResponse>(url, { params }).pipe(
      map((config) => config.montant),
      catchError((err: HttpErrorResponse) => (err.status === 404 ? of(null) : throwError(() => err))),
    );
  }
}
