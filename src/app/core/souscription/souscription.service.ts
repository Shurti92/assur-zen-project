import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { InsuredRegistrationRequest, InsuredRegistrationResponse } from './models/souscription.model';

@Injectable({ providedIn: 'root' })
export class SouscriptionService {
  constructor(private readonly http: HttpClient) {}

  /** Registers an insured and one or more contracts in a single operation —
   * the backend also sends a welcome SMS and schedules expiry reminders. */
  submit(payload: InsuredRegistrationRequest): Observable<InsuredRegistrationResponse> {
    return this.http.post<InsuredRegistrationResponse>('/souscriptions', payload);
  }
}
