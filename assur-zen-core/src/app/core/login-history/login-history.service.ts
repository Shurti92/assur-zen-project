import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { LoginHistoryFilter, LoginHistoryPage, LoginHistoryStatsResponse } from './models/login-history.model';

@Injectable({ providedIn: 'root' })
export class LoginHistoryService {
  constructor(private readonly http: HttpClient) {}

  private buildParams(page?: number, size?: number, filters?: LoginHistoryFilter): HttpParams {
    let params = new HttpParams();
    if (page !== undefined) params = params.set('page', page);
    if (size !== undefined) params = params.set('size', size);
    if (filters) {
      for (const [key, value] of Object.entries(filters)) {
        if (value !== undefined && value !== null && value !== '') {
          params = params.set(key, String(value));
        }
      }
    }
    return params;
  }

  search(page: number, size: number, filters?: LoginHistoryFilter): Observable<LoginHistoryPage> {
    return this.http.get<LoginHistoryPage>('/historique-connexions', { params: this.buildParams(page, size, filters) });
  }

  getStatistics(filters?: LoginHistoryFilter): Observable<LoginHistoryStatsResponse> {
    return this.http.get<LoginHistoryStatsResponse>('/historique-connexions/statistiques', {
      params: this.buildParams(undefined, undefined, filters),
    });
  }
}
