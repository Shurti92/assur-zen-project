import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AuditChangesResponse,
  AuditLogFilter,
  HistoriqueModificationPage,
  HistoriqueModificationResponse,
  HistoriqueModificationStatistiquesResponse,
} from './models/audit-trail.model';

@Injectable({ providedIn: 'root' })
export class AuditTrailService {
  constructor(private readonly http: HttpClient) {}

  private buildParams(page?: number, size?: number, filters?: AuditLogFilter): HttpParams {
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

  search(page: number, size: number, filters?: AuditLogFilter): Observable<HistoriqueModificationPage> {
    return this.http.get<HistoriqueModificationPage>('/audit-logs', { params: this.buildParams(page, size, filters) });
  }

  getById(id: number): Observable<HistoriqueModificationResponse> {
    return this.http.get<HistoriqueModificationResponse>(`/audit-logs/${id}`);
  }

  getChanges(id: number): Observable<AuditChangesResponse> {
    return this.http.get<AuditChangesResponse>(`/audit-logs/${id}/changes`);
  }

  getByEntity(entityName: string, entityId: string | number): Observable<HistoriqueModificationResponse[]> {
    return this.http.get<HistoriqueModificationResponse[]>(`/audit-logs/entity/${entityName}/${entityId}`);
  }

  getByUser(userId: number): Observable<HistoriqueModificationResponse[]> {
    return this.http.get<HistoriqueModificationResponse[]>(`/audit-logs/user/${userId}`);
  }

  getByCorrelationId(correlationId: string): Observable<HistoriqueModificationResponse[]> {
    return this.http.get<HistoriqueModificationResponse[]>(`/audit-logs/correlation/${correlationId}`);
  }

  getStatistics(): Observable<HistoriqueModificationStatistiquesResponse> {
    return this.http.get<HistoriqueModificationStatistiquesResponse>('/audit-logs/statistics');
  }

  exportCsv(filters?: AuditLogFilter): Observable<Blob> {
    return this.http.get('/audit-logs/export', { params: this.buildParams(undefined, undefined, filters), responseType: 'blob' });
  }
}
