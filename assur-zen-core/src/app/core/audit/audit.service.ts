import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { AuditPage } from '../../views/audit/audit.model';

@Injectable({ providedIn: 'root' })
export class AuditService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number): Observable<AuditPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<AuditPage>('/audit', { params });
  }
}
