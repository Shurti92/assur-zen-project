import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  Popup,
  PopupLimits,
  PopupList,
  PopupMediaUpload,
  PopupProduct,
  PopupRequest,
} from './models/mobile-popup.model';

/** Administration des pop-up publicitaires — /admin/promotions/popups (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class MobilePopupService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/promotions/popups';

  list(): Observable<PopupList> {
    return this.http.get<PopupList>(this.base);
  }

  create(payload: PopupRequest): Observable<Popup> {
    return this.http.post<Popup>(this.base, payload);
  }

  update(id: string, payload: PopupRequest): Observable<Popup> {
    return this.http.put<Popup>(`${this.base}/${encodeURIComponent(id)}`, payload);
  }

  setActive(id: string, active: boolean): Observable<Popup> {
    return this.http.patch<Popup>(`${this.base}/${encodeURIComponent(id)}/active`, { active });
  }

  /** Suppression définitive (les compteurs sont perdus : pour les garder, désactiver). */
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`);
  }

  limits(): Observable<PopupLimits> {
    return this.http.get<PopupLimits>(`${this.base}/limits`);
  }

  /** Téléverse le média (ou l'image d'attente) ; l'URL renvoyée se met dans mediaUrl / posterUrl. */
  upload(file: File, kind: 'MEDIA' | 'POSTER'): Observable<PopupMediaUpload> {
    const body = new FormData();
    body.append('file', file);
    body.append('kind', kind);
    return this.http.post<PopupMediaUpload>(`${this.base}/media`, body);
  }

  /** Produits ciblables : catalogue mobile public (items[].id et title). */
  products(): Observable<PopupProduct[]> {
    return this.http
      .get<{ items: PopupProduct[] }>('/public/products')
      .pipe(map((r) => (r.items ?? []).map((p) => ({ id: p.id, title: p.title }))));
  }
}
