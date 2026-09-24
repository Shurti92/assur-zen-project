import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ProfileDetails, ProfileRecord } from './models/profile-details.model';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  constructor(private readonly http: HttpClient) {}

  /** GET /profiles has no userId filter — callers must find their own record in the list. */
  list(): Observable<ProfileRecord[]> {
    return this.http.get<ProfileRecord[]>('/profiles');
  }

  get(id: number): Observable<ProfileRecord> {
    return this.http.get<ProfileRecord>(`/profiles/${id}`);
  }

  update(id: number, payload: ProfileDetails): Observable<ProfileRecord> {
    return this.http.put<ProfileRecord>(`/profiles/${id}`, payload);
  }
}
