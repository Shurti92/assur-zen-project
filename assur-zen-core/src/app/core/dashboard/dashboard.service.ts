import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { DashboardBackOfficeKpis, DashboardPeriod } from './models/dashboard-kpis.model';
import {
  DashboardAssureResponse,
  DashboardGlobalResponse,
  DashboardSortieFondsResponse,
  DashboardVignetteResponse,
} from './models/kpi-dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  constructor(private readonly http: HttpClient) {}

  getKpis(period: DashboardPeriod): Observable<DashboardBackOfficeKpis> {
    const params = new HttpParams().set('period', period);
    return this.http.get<DashboardBackOfficeKpis>('/dashboard/kpis-back-office', { params });
  }

  // -- KPI Dashboard (vignettes / sorties de fonds / assurés / global) — réservé ADMIN (403 sinon) --

  getVignettesKpis(dateDebut: string, dateFin: string): Observable<DashboardVignetteResponse> {
    return this.http.get<DashboardVignetteResponse>('/dashboard/vignettes', { params: this.periodeParams(dateDebut, dateFin) });
  }

  getSortiesFondsKpis(dateDebut: string, dateFin: string): Observable<DashboardSortieFondsResponse> {
    return this.http.get<DashboardSortieFondsResponse>('/dashboard/sorties-fonds', { params: this.periodeParams(dateDebut, dateFin) });
  }

  getAssuresKpis(dateDebut: string, dateFin: string): Observable<DashboardAssureResponse> {
    return this.http.get<DashboardAssureResponse>('/dashboard/assures', { params: this.periodeParams(dateDebut, dateFin) });
  }

  getGlobalKpis(dateDebut: string, dateFin: string): Observable<DashboardGlobalResponse> {
    return this.http.get<DashboardGlobalResponse>('/dashboard/global', { params: this.periodeParams(dateDebut, dateFin) });
  }

  private periodeParams(dateDebut: string, dateFin: string): HttpParams {
    return new HttpParams().set('dateDebut', dateDebut).set('dateFin', dateFin);
  }
}
