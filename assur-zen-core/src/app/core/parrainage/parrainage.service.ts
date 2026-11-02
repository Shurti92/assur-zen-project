import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CodeParrainageRequest,
  CodeParrainageResponse,
  ControleAntifraudeParrainageResponse,
  HistoriqueParrainageResponse,
  ParametreParrainageRequest,
  ParametreParrainageResponse,
  ParrainageContratRequest,
  ParrainageInscriptionRequest,
  ParrainageMotifRequest,
  ParrainagePage,
  ParrainageRequest,
  ParrainageResponse,
  ParrainageStatut,
  ParrainageValidationResponse,
  ProgrammeParrainageRequest,
  ProgrammeParrainageResponse,
  RecompenseParrainageResponse,
  RecompenseParrainageStatut,
  RegleRecompenseParrainageRequest,
  RegleRecompenseParrainageResponse,
} from './models/parrainage.model';

@Injectable({ providedIn: 'root' })
export class ParrainageService {
  constructor(private readonly http: HttpClient) {}

  // Programmes ---------------------------------------------------------------

  listProgrammes(): Observable<ProgrammeParrainageResponse[]> {
    return this.http.get<ProgrammeParrainageResponse[]>('/parrainage/programmes');
  }

  createProgramme(payload: ProgrammeParrainageRequest): Observable<ProgrammeParrainageResponse> {
    return this.http.post<ProgrammeParrainageResponse>('/parrainage/programmes', payload);
  }

  updateProgramme(id: number, payload: ProgrammeParrainageRequest): Observable<ProgrammeParrainageResponse> {
    return this.http.put<ProgrammeParrainageResponse>(`/parrainage/programmes/${id}`, payload);
  }

  activerProgramme(id: number): Observable<ProgrammeParrainageResponse> {
    return this.http.patch<ProgrammeParrainageResponse>(`/parrainage/programmes/${id}/activer`, {});
  }

  suspendreProgramme(id: number): Observable<ProgrammeParrainageResponse> {
    return this.http.patch<ProgrammeParrainageResponse>(`/parrainage/programmes/${id}/suspendre`, {});
  }

  cloturerProgramme(id: number): Observable<ProgrammeParrainageResponse> {
    return this.http.patch<ProgrammeParrainageResponse>(`/parrainage/programmes/${id}/cloturer`, {});
  }

  // Codes ----------------------------------------------------------------------

  listCodes(parrainId: number): Observable<CodeParrainageResponse[]> {
    return this.http.get<CodeParrainageResponse[]>('/parrainage/codes', { params: { parrainId } });
  }

  createCode(payload: CodeParrainageRequest): Observable<CodeParrainageResponse> {
    return this.http.post<CodeParrainageResponse>('/parrainage/codes', payload);
  }

  disableCode(id: number): Observable<CodeParrainageResponse> {
    return this.http.patch<CodeParrainageResponse>(`/parrainage/codes/${id}/disable`, {});
  }

  // Parrainages ------------------------------------------------------------

  listParrainages(
    page: number,
    size: number,
    filters?: { statut?: ParrainageStatut; programmeId?: number; parrainId?: number },
  ): Observable<ParrainagePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters?.statut) params = params.set('statut', filters.statut);
    if (filters?.programmeId) params = params.set('programmeId', filters.programmeId);
    if (filters?.parrainId) params = params.set('parrainId', filters.parrainId);
    return this.http.get<ParrainagePage>('/parrainage/parrainages', { params });
  }

  getParrainage(id: number): Observable<ParrainageResponse> {
    return this.http.get<ParrainageResponse>(`/parrainage/parrainages/${id}`);
  }

  createParrainage(payload: ParrainageRequest): Observable<ParrainageResponse> {
    return this.http.post<ParrainageResponse>('/parrainage/parrainages', payload);
  }

  inviter(id: number): Observable<ParrainageResponse> {
    return this.http.post<ParrainageResponse>(`/parrainage/parrainages/${id}/inviter`, {});
  }

  inscrire(id: number, payload: ParrainageInscriptionRequest): Observable<ParrainageResponse> {
    return this.http.post<ParrainageResponse>(`/parrainage/parrainages/${id}/inscription`, payload);
  }

  rattacherContrat(id: number, payload: ParrainageContratRequest): Observable<ParrainageResponse> {
    return this.http.post<ParrainageResponse>(`/parrainage/parrainages/${id}/souscription`, payload);
  }

  valider(id: number): Observable<ParrainageValidationResponse> {
    return this.http.post<ParrainageValidationResponse>(`/parrainage/parrainages/${id}/valider`, {});
  }

  expirer(id: number): Observable<ParrainageResponse> {
    return this.http.post<ParrainageResponse>(`/parrainage/parrainages/${id}/expirer`, {});
  }

  annulerParrainage(id: number, payload: ParrainageMotifRequest): Observable<ParrainageResponse> {
    return this.http.post<ParrainageResponse>(`/parrainage/parrainages/${id}/annuler`, payload);
  }

  getHistorique(id: number): Observable<HistoriqueParrainageResponse[]> {
    return this.http.get<HistoriqueParrainageResponse[]>(`/parrainage/parrainages/${id}/historique`);
  }

  getControlesAntifraude(id: number): Observable<ControleAntifraudeParrainageResponse[]> {
    return this.http.get<ControleAntifraudeParrainageResponse[]>(`/parrainage/parrainages/${id}/controles-antifraude`);
  }

  runControleAntifraude(id: number): Observable<ControleAntifraudeParrainageResponse> {
    return this.http.post<ControleAntifraudeParrainageResponse>(`/parrainage/parrainages/${id}/controles-antifraude`, {});
  }

  // Règles de récompense ------------------------------------------------------

  listRegles(programmeId?: number): Observable<RegleRecompenseParrainageResponse[]> {
    const params = programmeId ? new HttpParams().set('programmeId', programmeId) : undefined;
    return this.http.get<RegleRecompenseParrainageResponse[]>('/parrainage/regles-recompense', { params });
  }

  createRegle(payload: RegleRecompenseParrainageRequest): Observable<RegleRecompenseParrainageResponse> {
    return this.http.post<RegleRecompenseParrainageResponse>('/parrainage/regles-recompense', payload);
  }

  updateRegle(id: number, payload: RegleRecompenseParrainageRequest): Observable<RegleRecompenseParrainageResponse> {
    return this.http.put<RegleRecompenseParrainageResponse>(`/parrainage/regles-recompense/${id}`, payload);
  }

  deleteRegle(id: number): Observable<void> {
    return this.http.delete<void>(`/parrainage/regles-recompense/${id}`);
  }

  // Récompenses ------------------------------------------------------------

  listRecompenses(filters?: {
    statut?: RecompenseParrainageStatut;
    parrainageId?: number;
    beneficiaireId?: number;
  }): Observable<RecompenseParrainageResponse[]> {
    let params = new HttpParams();
    if (filters?.statut) params = params.set('statut', filters.statut);
    if (filters?.parrainageId) params = params.set('parrainageId', filters.parrainageId);
    if (filters?.beneficiaireId) params = params.set('beneficiaireId', filters.beneficiaireId);
    return this.http.get<RecompenseParrainageResponse[]>('/parrainage/recompenses', { params });
  }

  approuverRecompense(id: number): Observable<RecompenseParrainageResponse> {
    return this.http.post<RecompenseParrainageResponse>(`/parrainage/recompenses/${id}/approuver`, {});
  }

  attribuerRecompense(id: number): Observable<RecompenseParrainageResponse> {
    return this.http.post<RecompenseParrainageResponse>(`/parrainage/recompenses/${id}/attribuer`, {});
  }

  reverserRecompense(id: number, payload: ParrainageMotifRequest): Observable<RecompenseParrainageResponse> {
    return this.http.post<RecompenseParrainageResponse>(`/parrainage/recompenses/${id}/reverser`, payload);
  }

  annulerRecompense(id: number, payload: ParrainageMotifRequest): Observable<RecompenseParrainageResponse> {
    return this.http.post<RecompenseParrainageResponse>(`/parrainage/recompenses/${id}/annuler`, payload);
  }

  // Paramètres ------------------------------------------------------------

  listParametres(): Observable<ParametreParrainageResponse[]> {
    return this.http.get<ParametreParrainageResponse[]>('/parrainage/parametres');
  }

  upsertParametre(payload: ParametreParrainageRequest): Observable<ParametreParrainageResponse> {
    return this.http.put<ParametreParrainageResponse>('/parrainage/parametres', payload);
  }
}
