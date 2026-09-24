import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { FraisLivraisonZoneRequest, FraisLivraisonZoneResponse } from './models/vignette-livraison.model';

/** Zones de livraison (ABIDJAN, HORS_ABIDJAN...) — réservé ADMIN pour la création/modification,
 * mais GET .../livraisons doit rester accessible à tout compte authentifié : c'est cette liste
 * qui alimente le select "zone de livraison" du parcours de demande. Pas de pagination — 2 lignes
 * au total à date. */
@Injectable({ providedIn: 'root' })
export class VignetteLivraisonService {
  constructor(private readonly http: HttpClient) {}

  list(): Observable<FraisLivraisonZoneResponse[]> {
    return this.http.get<FraisLivraisonZoneResponse[]>('/vignettes-moto/livraisons');
  }

  get(id: number): Observable<FraisLivraisonZoneResponse> {
    return this.http.get<FraisLivraisonZoneResponse>(`/vignettes-moto/livraisons/${id}`);
  }

  create(payload: FraisLivraisonZoneRequest): Observable<FraisLivraisonZoneResponse> {
    return this.http.post<FraisLivraisonZoneResponse>('/vignettes-moto/livraisons', payload);
  }

  update(id: number, payload: FraisLivraisonZoneRequest): Observable<FraisLivraisonZoneResponse> {
    return this.http.put<FraisLivraisonZoneResponse>(`/vignettes-moto/livraisons/${id}`, payload);
  }

  /** Pas d'endpoint de réactivation côté backend pour l'instant. */
  desactiver(id: number): Observable<FraisLivraisonZoneResponse> {
    return this.http.patch<FraisLivraisonZoneResponse>(`/vignettes-moto/livraisons/${id}/desactiver`, {});
  }
}
