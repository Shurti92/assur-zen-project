import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ExerciceComptableRequest,
  ExerciceComptableResponse,
  MoisComptableResponse,
} from './models/exercice-comptable.model';

@Injectable({ providedIn: 'root' })
export class ExerciceComptableService {
  constructor(private readonly http: HttpClient) {}

  /** GET /exercices-comptables has no query/pagination parameters — returns the full list. */
  list(): Observable<ExerciceComptableResponse[]> {
    return this.http.get<ExerciceComptableResponse[]>('/exercices-comptables');
  }

  listAvailable(): Observable<ExerciceComptableResponse[]> {
    return this.http.get<ExerciceComptableResponse[]>('/exercices-comptables/actifs');
  }

  create(payload: ExerciceComptableRequest): Observable<ExerciceComptableResponse> {
    return this.http.post<ExerciceComptableResponse>('/exercices-comptables', payload);
  }

  update(id: number, payload: ExerciceComptableRequest): Observable<ExerciceComptableResponse> {
    return this.http.put<ExerciceComptableResponse>(`/exercices-comptables/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/exercices-comptables/${id}`);
  }

  setObjectif(id: number, objectifGlobal: number): Observable<ExerciceComptableResponse> {
    return this.http.patch<ExerciceComptableResponse>(`/exercices-comptables/${id}/objectif`, { objectifGlobal });
  }

  cloturer(id: number): Observable<ExerciceComptableResponse> {
    return this.http.post<ExerciceComptableResponse>(`/exercices-comptables/${id}/cloturer`, {});
  }

  reouvrir(id: number): Observable<ExerciceComptableResponse> {
    return this.http.post<ExerciceComptableResponse>(`/exercices-comptables/${id}/reouvrir`, {});
  }

  /** `mois` est déjà inclus dans `list()`/`get()` — cet appel ne sert qu'à rafraîchir uniquement
   * les mois d'un exercice après une action, sans recharger toute la liste. */
  listMois(exerciceId: number): Observable<MoisComptableResponse[]> {
    return this.http.get<MoisComptableResponse[]>(`/exercices-comptables/${exerciceId}/mois`);
  }

  cloturerMois(exerciceId: number, moisId: number): Observable<MoisComptableResponse> {
    return this.http.post<MoisComptableResponse>(
      `/exercices-comptables/${exerciceId}/mois/${moisId}/cloturer`,
      {}
    );
  }

  reouvrirMois(exerciceId: number, moisId: number): Observable<MoisComptableResponse> {
    return this.http.post<MoisComptableResponse>(
      `/exercices-comptables/${exerciceId}/mois/${moisId}/reouvrir`,
      {}
    );
  }
}
