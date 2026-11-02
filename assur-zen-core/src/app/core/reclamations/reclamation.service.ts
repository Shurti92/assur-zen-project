import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ReclamationAffectationRequest,
  ReclamationClotureRequest,
  ReclamationCommentaireRequest,
  ReclamationCommentaireResponse,
  ReclamationPage,
  ReclamationPieceJointeResponse,
  ReclamationRequest,
  ReclamationResponse,
  ReclamationStatistiquesResponse,
  ReclamationStatutRequest,
} from './models/reclamation.model';

export interface ReclamationListOptions {
  statut?: string;
  type?: string;
  priorite?: string;
  canal?: string;
  gestionnaireId?: number;
  insuredId?: number;
}

@Injectable({ providedIn: 'root' })
export class ReclamationService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, options?: ReclamationListOptions): Observable<ReclamationPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (options?.statut) params = params.set('statut', options.statut);
    if (options?.type) params = params.set('type', options.type);
    if (options?.priorite) params = params.set('priorite', options.priorite);
    if (options?.canal) params = params.set('canal', options.canal);
    if (options?.gestionnaireId !== undefined) params = params.set('gestionnaireId', options.gestionnaireId);
    if (options?.insuredId !== undefined) params = params.set('insuredId', options.insuredId);
    return this.http.get<ReclamationPage>('/reclamations', { params });
  }

  /** Fetches every complaint in one request for client-side filtering/pagination. */
  listAll(): Observable<ReclamationPage> {
    return this.list(0, 1000);
  }

  get(id: number): Observable<ReclamationResponse> {
    return this.http.get<ReclamationResponse>(`/reclamations/${id}`);
  }

  create(payload: ReclamationRequest): Observable<ReclamationResponse> {
    return this.http.post<ReclamationResponse>('/reclamations', payload);
  }

  update(id: number, payload: ReclamationRequest): Observable<ReclamationResponse> {
    return this.http.put<ReclamationResponse>(`/reclamations/${id}`, payload);
  }

  /** Suppression physique, réservée à la purge d'un test/doublon — rejetée en 409 si la
   * réclamation a un commentaire, une pièce jointe, ou a été créée automatiquement (NPS bas). */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/reclamations/${id}`);
  }

  changerStatut(id: number, payload: ReclamationStatutRequest): Observable<ReclamationResponse> {
    return this.http.patch<ReclamationResponse>(`/reclamations/${id}/statut`, payload);
  }

  affecter(id: number, payload: ReclamationAffectationRequest): Observable<ReclamationResponse> {
    return this.http.patch<ReclamationResponse>(`/reclamations/${id}/affectation`, payload);
  }

  cloturer(id: number, payload: ReclamationClotureRequest): Observable<ReclamationResponse> {
    return this.http.post<ReclamationResponse>(`/reclamations/${id}/cloture`, payload);
  }

  listCommentaires(id: number): Observable<ReclamationCommentaireResponse[]> {
    return this.http.get<ReclamationCommentaireResponse[]>(`/reclamations/${id}/commentaires`);
  }

  addCommentaire(id: number, payload: ReclamationCommentaireRequest): Observable<ReclamationCommentaireResponse> {
    return this.http.post<ReclamationCommentaireResponse>(`/reclamations/${id}/commentaires`, payload);
  }

  listPiecesJointes(id: number): Observable<ReclamationPieceJointeResponse[]> {
    return this.http.get<ReclamationPieceJointeResponse[]>(`/reclamations/${id}/pieces-jointes`);
  }

  uploadPieceJointe(id: number, file: File): Observable<ReclamationPieceJointeResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ReclamationPieceJointeResponse>(`/reclamations/${id}/pieces-jointes`, formData);
  }

  downloadPieceJointe(id: number, pieceId: number): Observable<Blob> {
    return this.http.get(`/reclamations/${id}/pieces-jointes/${pieceId}`, { responseType: 'blob' });
  }

  statistiques(): Observable<ReclamationStatistiquesResponse> {
    return this.http.get<ReclamationStatistiquesResponse>('/reclamations/statistiques');
  }
}
