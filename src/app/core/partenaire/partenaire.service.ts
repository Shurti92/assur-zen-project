import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ContractPage } from '../contracts/models/contract.model';
import {
  ContractAvenantSummaryPage,
  HistoriqueContratPage,
  PartenaireDocumentResponse,
  PartenaireDocumentType,
  PartenairePage,
  PartenaireRequest,
  PartenaireResponse,
  TypePartenaire,
} from './models/partenaire.model';

@Injectable({ providedIn: 'root' })
export class PartenaireService {
  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, q?: string, active?: boolean, type?: TypePartenaire): Observable<PartenairePage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== undefined) params = params.set('active', active);
    if (type) params = params.set('type', type);
    return this.http.get<PartenairePage>('/partenaires', { params });
  }

  /** Fetches every partner in one request — used for client-side aggregation
   * (e.g. objective roll-ups) since the dataset here is small. */
  listAll(): Observable<PartenairePage> {
    return this.list(0, 1000);
  }

  get(id: number): Observable<PartenaireResponse> {
    return this.http.get<PartenaireResponse>(`/partenaires/${id}`);
  }

  create(payload: PartenaireRequest): Observable<PartenaireResponse> {
    return this.http.post<PartenaireResponse>('/partenaires', payload);
  }

  update(id: number, payload: PartenaireRequest): Observable<PartenaireResponse> {
    return this.http.put<PartenaireResponse>(`/partenaires/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/partenaires/${id}`);
  }

  /** Ajuste uniquement l'objectif de production, sans repasser par update() (PUT complet) —
   * évite d'écraser un autre champ si le formulaire n'a pas l'état le plus récent. */
  setObjectif(id: number, objectifProduction: number): Observable<PartenaireResponse> {
    return this.http.patch<PartenaireResponse>(`/partenaires/${id}/objectif`, { objectifProduction });
  }

  /** Contrats de ce partenaire. Par défaut, `inclureAnciensNonRenouveles` étant omis, le backend
   * masque les contrats signés/échus/non renouvelés depuis plus de 2 mois. */
  getContracts(
    id: number,
    page: number,
    size: number,
    search?: string,
    status?: string,
    inclureAnciensNonRenouveles?: boolean,
  ): Observable<ContractPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search) params = params.set('search', search);
    if (status) params = params.set('status', status);
    if (inclureAnciensNonRenouveles !== undefined) {
      params = params.set('inclureAnciensNonRenouveles', inclureAnciensNonRenouveles);
    }
    return this.http.get<ContractPage>(`/partenaires/${id}/contracts`, { params });
  }

  /** Contrats signés de ce partenaire dont la prime encaissée n'a pas encore été reversée
   * à la compagnie. */
  getPrimeNonReversee(id: number, page: number, size: number): Observable<ContractPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ContractPage>(`/partenaires/${id}/contracts/prime-non-reversee`, { params });
  }

  /** Timeline chronologique paginée (avenants + audit confondus) de tous les contrats
   * de ce partenaire. */
  getHistorique(id: number, page: number, size: number): Observable<HistoriqueContratPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<HistoriqueContratPage>(`/partenaires/${id}/contracts/historique`, { params });
  }

  /** Contrats de ce partenaire ayant subi au moins un avenant, résumé agrégé par contrat. */
  getAvenants(id: number, page: number, size: number): Observable<ContractAvenantSummaryPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ContractAvenantSummaryPage>(`/partenaires/${id}/contracts/avenants`, { params });
  }

  /** Export CSV de la liste globale des partenaires. */
  export(): Observable<Blob> {
    return this.http.get('/partenaires/export', { responseType: 'blob' });
  }

  /** Documents joints (contrat apporteur, CNI, ...) — voir docs/api-contracts/partenaire-documents.md.
   * Endpoint pas encore livré côté backend au moment de l'écriture. */
  listDocuments(id: number): Observable<PartenaireDocumentResponse[]> {
    return this.http.get<PartenaireDocumentResponse[]>(`/partenaires/${id}/documents`);
  }

  uploadDocument(id: number, type: PartenaireDocumentType, file: File): Observable<PartenaireDocumentResponse> {
    const formData = new FormData();
    formData.append('type', type);
    formData.append('file', file);
    return this.http.post<PartenaireDocumentResponse>(`/partenaires/${id}/documents`, formData);
  }

  downloadDocument(id: number, documentId: number): Observable<Blob> {
    return this.http.get(`/partenaires/${id}/documents/${documentId}`, { responseType: 'blob' });
  }

  /** Pas encore livré côté backend au moment de l'écriture (voir docs/api-contracts/partenaire-documents.md
   * §DELETE) — préparé côté front en avance, comme le reste de ce module. */
  deleteDocument(id: number, documentId: number): Observable<void> {
    return this.http.delete<void>(`/partenaires/${id}/documents/${documentId}`);
  }
}
