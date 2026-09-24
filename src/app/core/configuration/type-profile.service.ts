import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { TypeProfileRequest, TypeProfileResponse } from './models/type-profile.model';

@Injectable({ providedIn: 'root' })
export class TypeProfileService {
  constructor(private readonly http: HttpClient) {}

  /** GET /type-profiles has no query/pagination parameters — returns the full list. */
  list(): Observable<TypeProfileResponse[]> {
    return this.http.get<TypeProfileResponse[]>('/type-profiles');
  }

  create(payload: TypeProfileRequest): Observable<TypeProfileResponse> {
    return this.http.post<TypeProfileResponse>('/type-profiles', payload);
  }

  update(id: number, payload: TypeProfileRequest): Observable<TypeProfileResponse> {
    return this.http.put<TypeProfileResponse>(`/type-profiles/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/type-profiles/${id}`);
  }
}
