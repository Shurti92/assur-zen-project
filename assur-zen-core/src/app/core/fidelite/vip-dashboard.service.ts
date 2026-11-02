import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  VipMensuelItem,
  VipOverviewResponse,
  VipParDimensionItem,
  VipParNiveauItem,
  VipTopClientItem,
} from './models/vip-dashboard.model';

@Injectable({ providedIn: 'root' })
export class VipDashboardService {
  constructor(private readonly http: HttpClient) {}

  overview(): Observable<VipOverviewResponse> {
    return this.http.get<VipOverviewResponse>('/vip-dashboard/overview');
  }

  mensuel(): Observable<VipMensuelItem[]> {
    return this.http.get<VipMensuelItem[]>('/vip-dashboard/mensuel');
  }

  parCourtier(): Observable<VipParDimensionItem[]> {
    return this.http.get<VipParDimensionItem[]>('/vip-dashboard/par-courtier');
  }

  parGestionnaire(): Observable<VipParDimensionItem[]> {
    return this.http.get<VipParDimensionItem[]>('/vip-dashboard/par-gestionnaire');
  }

  parProduit(): Observable<VipParDimensionItem[]> {
    return this.http.get<VipParDimensionItem[]>('/vip-dashboard/par-produit');
  }

  parNiveau(): Observable<VipParNiveauItem[]> {
    return this.http.get<VipParNiveauItem[]>('/vip-dashboard/par-niveau');
  }

  topClients(limite?: number): Observable<VipTopClientItem[]> {
    let params = new HttpParams();
    if (limite !== undefined) params = params.set('limite', limite);
    return this.http.get<VipTopClientItem[]>('/vip-dashboard/top-clients', { params });
  }
}
