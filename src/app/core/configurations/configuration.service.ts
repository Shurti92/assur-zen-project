import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { HistoriqueModificationResponse } from '../audit-trail/models/audit-trail.model';
import {
  ConfigurationFilter,
  ConfigurationPage,
  DynamicConfigurationRequest,
  DynamicConfigurationResponse,
} from './models/configuration.model';

@Injectable({ providedIn: 'root' })
export class ConfigurationService {
  constructor(private readonly http: HttpClient) {}

  search(page: number, size: number, filters?: ConfigurationFilter): Observable<ConfigurationPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters) {
      for (const [key, value] of Object.entries(filters)) {
        if (value !== undefined && value !== null && value !== '') {
          params = params.set(key, String(value));
        }
      }
    }
    return this.http.get<ConfigurationPage>('/configurations', { params });
  }

  getByKey(key: string): Observable<DynamicConfigurationResponse> {
    return this.http.get<DynamicConfigurationResponse>(`/configurations/${key}`);
  }

  create(payload: DynamicConfigurationRequest): Observable<DynamicConfigurationResponse> {
    return this.http.post<DynamicConfigurationResponse>('/configurations', payload);
  }

  update(key: string, payload: DynamicConfigurationRequest): Observable<DynamicConfigurationResponse> {
    return this.http.put<DynamicConfigurationResponse>(`/configurations/${key}`, payload);
  }

  delete(key: string): Observable<void> {
    return this.http.delete<void>(`/configurations/${key}`);
  }

  activate(key: string): Observable<DynamicConfigurationResponse> {
    return this.http.patch<DynamicConfigurationResponse>(`/configurations/${key}/activate`, {});
  }

  deactivate(key: string): Observable<DynamicConfigurationResponse> {
    return this.http.patch<DynamicConfigurationResponse>(`/configurations/${key}/deactivate`, {});
  }

  refresh(key: string): Observable<DynamicConfigurationResponse> {
    return this.http.post<DynamicConfigurationResponse>(`/configurations/${key}/refresh`, {});
  }

  refreshAll(): Observable<void> {
    return this.http.post<void>('/configurations/refresh', {});
  }

  getModules(): Observable<string[]> {
    return this.http.get<string[]>('/configurations/modules');
  }

  getCategories(): Observable<string[]> {
    return this.http.get<string[]>('/configurations/categories');
  }

  getHistory(key: string): Observable<HistoriqueModificationResponse[]> {
    return this.http.get<HistoriqueModificationResponse[]>(`/configurations/${key}/history`);
  }

  restoreVersion(key: string, historiqueId: number): Observable<DynamicConfigurationResponse> {
    return this.http.post<DynamicConfigurationResponse>(`/configurations/${key}/restore/${historiqueId}`, {});
  }
}
