import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  CreateMobileAccountRequest,
  MobileAccountDetail,
  MobileAccountPage,
} from './models/mobile-account.model';

/** Comptes clients de l'application mobile — /admin/mobile/accounts (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class MobileAccountService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/mobile/accounts';

  list(page: number, size: number, q?: string, active?: boolean): Observable<MobileAccountPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== undefined) params = params.set('active', active);
    return this.http.get<MobileAccountPage>(this.base, { params });
  }

  get(userId: number): Observable<MobileAccountDetail> {
    return this.http.get<MobileAccountDetail>(`${this.base}/${userId}`);
  }

  create(payload: CreateMobileAccountRequest): Observable<MobileAccountDetail> {
    return this.http.post<MobileAccountDetail>(this.base, payload);
  }

  /** Renvoie le SMS d'invitation (404 ACCOUNT_NOT_FOUND, 409 ACCOUNT_INACTIVE). */
  sendInvitation(userId: number): Observable<unknown> {
    return this.http.post(`${this.base}/${userId}/invitation`, {});
  }

  /** Désactiver coupe immédiatement les sessions du client. */
  setActive(userId: number, active: boolean, comment?: string): Observable<unknown> {
    return this.http.patch(`${this.base}/${userId}/active`, { active, comment: comment || undefined });
  }

  linkInsured(userId: number, insuredId: number): Observable<unknown> {
    return this.http.post(`${this.base}/${userId}/insureds`, { insuredId });
  }

  unlinkInsured(userId: number, insuredId: number): Observable<unknown> {
    return this.http.delete(`${this.base}/${userId}/insureds/${insuredId}`);
  }

  /** Nombre de demandes de suppression en attente (bandeau de synthèse). */
  pendingDeletionCount(): Observable<number> {
    return this.http
      .get<unknown[]>('/admin/mobile/deletion-requests', { params: { statut: 'EN_ATTENTE' } })
      .pipe(map((list) => list.length));
  }
}
