import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  QuoteCreateRequest,
  QuoteForm,
  QuoteList,
  QuoteSimulation,
  QuoteStatus,
  QuoteVehicleRequest,
  QuoteView,
  VtcQuoteCreateRequest,
  VtcQuoteForm,
  VtcVehicleRequest,
} from './models/quote.model';

/** Devis saisis au back-office (moto et automobile VTC) — /admin/quotes (rôles ADMIN et COMMERCIAL). */
@Injectable({ providedIn: 'root' })
export class QuoteService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/quotes';

  /** Un commercial ne voit que ses devis, un administrateur voit tous les devis. */
  list(status?: QuoteStatus | null, q?: string | null): Observable<QuoteView[]> {
    let params = new HttpParams();
    if (status) params = params.set('statut', status);
    if (q?.trim()) params = params.set('q', q.trim());
    return this.http.get<QuoteList>(this.base, { params }).pipe(map((r) => r.items));
  }

  get(reference: string): Observable<QuoteView> {
    return this.http.get<QuoteView>(`${this.base}/${encodeURIComponent(reference)}`);
  }

  create(request: QuoteCreateRequest): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${this.base}/moto`, request);
  }

  createVtc(request: VtcQuoteCreateRequest): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${this.base}/vtc`, request);
  }

  /** Accord du prospect (409 QUOTE_EXPIRED si le devis est périmé). */
  accept(reference: string): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${this.base}/${encodeURIComponent(reference)}/accept`, {});
  }

  /** Options du formulaire moto (types de véhicule, durées, date d'effet minimale). */
  form(): Observable<QuoteForm> {
    return this.http.get<QuoteForm>('/public/quotes/moto/form');
  }

  /** Calcul du prix moto sans aucune écriture. */
  simulate(vehicle: QuoteVehicleRequest): Observable<QuoteSimulation> {
    return this.http.post<QuoteSimulation>('/public/quotes/moto/simulate', vehicle);
  }

  /** Options du formulaire VTC (énergies, durées, marques, plateformes, bornes CV et places). */
  vtcForm(): Observable<VtcQuoteForm> {
    return this.http.get<VtcQuoteForm>('/public/quotes/vtc/form');
  }

  /** Calcul du prix VTC sans aucune écriture (sans remise : elle est estimée à l'écran). */
  simulateVtc(vehicle: VtcVehicleRequest): Observable<QuoteSimulation> {
    return this.http.post<QuoteSimulation>('/public/quotes/vtc/simulate', vehicle);
  }
}
