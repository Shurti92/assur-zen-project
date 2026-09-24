import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { TarifVignetteMotoRequest, TarifVignetteMotoResponse } from './models/vignette-tarif.model';

/** Grille tarifaire — réservé ADMIN côté backend (403 sinon), aucun guard de route dédié côté
 * frontend (convention du projet : le contrôle d'accès reste server-side, voir NavPermissionsService
 * pour le seul masquage de menu existant). Pas de pagination — 6 lignes au total. */
@Injectable({ providedIn: 'root' })
export class VignetteTarifService {
  constructor(private readonly http: HttpClient) {}

  list(): Observable<TarifVignetteMotoResponse[]> {
    return this.http.get<TarifVignetteMotoResponse[]>('/vignettes-moto/tarifs');
  }

  get(id: number): Observable<TarifVignetteMotoResponse> {
    return this.http.get<TarifVignetteMotoResponse>(`/vignettes-moto/tarifs/${id}`);
  }

  /** 409 si ce tarif chevauche un tarif déjà actif pour la même catégorie (âge + cylindrée). */
  create(payload: TarifVignetteMotoRequest): Observable<TarifVignetteMotoResponse> {
    return this.http.post<TarifVignetteMotoResponse>('/vignettes-moto/tarifs', payload);
  }

  /** N'affecte jamais les demandes déjà créées — leurs montants restent figés. */
  update(id: number, payload: TarifVignetteMotoRequest): Observable<TarifVignetteMotoResponse> {
    return this.http.put<TarifVignetteMotoResponse>(`/vignettes-moto/tarifs/${id}`, payload);
  }

  /** Pas d'endpoint de réactivation côté backend pour l'instant (pas demandé initialement). */
  desactiver(id: number): Observable<TarifVignetteMotoResponse> {
    return this.http.patch<TarifVignetteMotoResponse>(`/vignettes-moto/tarifs/${id}/desactiver`, {});
  }
}
