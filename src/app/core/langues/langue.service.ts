import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { LangueRequest, LangueResponse } from './models/langue.model';

@Injectable({ providedIn: 'root' })
export class LangueService {
  constructor(private readonly http: HttpClient) {}

  list(): Observable<LangueResponse[]> {
    return this.http.get<LangueResponse[]>('/langues');
  }

  create(payload: LangueRequest): Observable<LangueResponse> {
    return this.http.post<LangueResponse>('/langues', payload);
  }

  update(id: number, payload: LangueRequest): Observable<LangueResponse> {
    return this.http.put<LangueResponse>(`/langues/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/langues/${id}`);
  }
}
