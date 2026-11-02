import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { UserFilter, UserPage, UserProfileRequest, UserProfileResponse, UserSessionPage } from './models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, filter: UserFilter = {}): Observable<UserPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter.q) params = params.set('q', filter.q);
    if (filter.active !== undefined) params = params.set('active', filter.active);
    if (filter.roles) params = params.set('roles', filter.roles);
    if (filter.typeProfileId) params = params.set('typeProfileId', filter.typeProfileId);
    if (filter.villePartenaireId) params = params.set('villePartenaireId', filter.villePartenaireId);
    return this.http.get<UserPage>('/users', { params });
  }

  getById(id: number): Observable<UserProfileResponse> {
    return this.http.get<UserProfileResponse>(`/users/${id}`);
  }

  /** Crée le compte ET son profil en une seule opération. */
  create(payload: UserProfileRequest): Observable<UserProfileResponse> {
    return this.http.post<UserProfileResponse>('/users', payload);
  }

  update(id: number, payload: UserProfileRequest): Observable<UserProfileResponse> {
    return this.http.put<UserProfileResponse>(`/users/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/users/${id}`);
  }

  setStatus(id: number, active: boolean): Observable<UserProfileResponse> {
    return this.http.patch<UserProfileResponse>(`/users/${id}/status`, { active });
  }

  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http.patch<void>(`/users/${id}/reset-password`, { newPassword });
  }

  /** Régénère un mot de passe temporaire ET l'envoie par SMS en un seul appel côté backend — le
   * mot de passe n'est jamais renvoyé dans la réponse HTTP (voir UserProfileResponse.notificationEnvoyee
   * pour confirmer l'envoi). À utiliser pour "Renvoyer les accès" : générer le mot de passe côté
   * frontend puis appeler resetPassword() séparément créait un vrai risque de désynchronisation
   * (le mot de passe affiché à l'admin pouvait différer de celui effectivement en base si un autre
   * appel survenait entre les deux). */
  resendAccess(id: number): Observable<UserProfileResponse> {
    return this.http.post<UserProfileResponse>(`/users/${id}/resend-access`, {});
  }

  getLoginHistory(id: number, page: number, size: number): Observable<UserSessionPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<UserSessionPage>(`/users/${id}/login-history`, { params });
  }

  /** Rôles valides — liste dynamique côté backend, jamais codée en dur côté frontend. */
  getRoles(): Observable<string[]> {
    return this.http.get<string[]>('/users/roles');
  }

  /** Exporte les utilisateurs (et filtres courants) au format CSV. */
  export(filter: UserFilter = {}): Observable<Blob> {
    let params = new HttpParams();
    if (filter.q) params = params.set('q', filter.q);
    if (filter.active !== undefined) params = params.set('active', filter.active);
    if (filter.roles) params = params.set('roles', filter.roles);
    if (filter.typeProfileId) params = params.set('typeProfileId', filter.typeProfileId);
    if (filter.villePartenaireId) params = params.set('villePartenaireId', filter.villePartenaireId);
    return this.http.get('/users/export', { params, responseType: 'blob' });
  }
}
