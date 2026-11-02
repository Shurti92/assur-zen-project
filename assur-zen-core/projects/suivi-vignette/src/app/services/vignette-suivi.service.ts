import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { SuiviPublicVignetteResponse } from '../models/suivi-vignette.model';

/** Un seul appel, jamais authentifié — pas d'intercepteur, pas de gestion de session : cette
 * mini-app n'a aucun des mécanismes d'assur-zen-core (JWT, refresh token, logs applicatifs). Le
 * token n'est jamais journalisé ni stocké au-delà de la variable passée à `suiviPublic()`. */
@Injectable({ providedIn: 'root' })
export class VignetteSuiviService {
  constructor(private readonly http: HttpClient) {}

  suiviPublic(token: string): Observable<SuiviPublicVignetteResponse> {
    const params = new HttpParams().set('token', token);
    return this.http.get<SuiviPublicVignetteResponse>(`${environment.apiUrl}/vignettes-moto/demandes/suivi-public`, { params });
  }
}
