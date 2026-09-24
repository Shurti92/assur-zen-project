import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  GedDocumentLinkRequest,
  GedDocumentLinkResponse,
  GedDocumentPage,
  GedDocumentResponse,
  GedDocumentSearchFilters,
  GedDocumentUploadMeta,
  GedDocumentVersionResponse,
  RejectGedDocumentRequest,
  UpdateGedDocumentRequest,
} from './models/ged-document.model';

@Injectable({ providedIn: 'root' })
export class GedDocumentService {
  constructor(private readonly http: HttpClient) {}

  search(page: number, size: number, filters?: GedDocumentSearchFilters): Observable<GedDocumentPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters) {
      for (const [key, value] of Object.entries(filters)) {
        if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
      }
    }
    return this.http.get<GedDocumentPage>('/ged/documents/search', { params });
  }

  get(id: number): Observable<GedDocumentResponse> {
    return this.http.get<GedDocumentResponse>(`/ged/documents/${id}`);
  }

  /** Dépôt d'un nouveau document — les métadonnées voyagent en query params, le fichier seul
   * dans le corps multipart (confirmé sur le schéma OpenAPI réel, pas une convention devinée). */
  upload(file: File, meta: GedDocumentUploadMeta): Observable<GedDocumentResponse> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(meta)) {
      if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
    }
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<GedDocumentResponse>('/ged/documents', formData, { params });
  }

  updateMetadata(id: number, payload: UpdateGedDocumentRequest): Observable<GedDocumentResponse> {
    return this.http.put<GedDocumentResponse>(`/ged/documents/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/ged/documents/${id}`);
  }

  download(id: number): Observable<Blob> {
    return this.http.get(`/ged/documents/${id}/download`, { responseType: 'blob' });
  }

  downloadVersion(id: number, versionId: number): Observable<Blob> {
    return this.http.get(`/ged/documents/${id}/versions/${versionId}/download`, { responseType: 'blob' });
  }

  listVersions(id: number): Observable<GedDocumentVersionResponse[]> {
    return this.http.get<GedDocumentVersionResponse[]>(`/ged/documents/${id}/versions`);
  }

  /** Une version n'écrase jamais la précédente — les anciennes restent toujours téléchargeables. */
  addVersion(id: number, file: File): Observable<GedDocumentVersionResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<GedDocumentVersionResponse>(`/ged/documents/${id}/versions`, formData);
  }

  /** Uniquement possible depuis le statut UPLOADED — 409 sinon. */
  validate(id: number): Observable<GedDocumentResponse> {
    return this.http.post<GedDocumentResponse>(`/ged/documents/${id}/validate`, {});
  }

  reject(id: number, payload: RejectGedDocumentRequest): Observable<GedDocumentResponse> {
    return this.http.post<GedDocumentResponse>(`/ged/documents/${id}/reject`, payload);
  }

  archive(id: number): Observable<GedDocumentResponse> {
    return this.http.post<GedDocumentResponse>(`/ged/documents/${id}/archive`, {});
  }

  listLinks(id: number): Observable<GedDocumentLinkResponse[]> {
    return this.http.get<GedDocumentLinkResponse[]>(`/ged/documents/${id}/links`);
  }

  createLink(id: number, payload: GedDocumentLinkRequest): Observable<GedDocumentLinkResponse> {
    return this.http.post<GedDocumentLinkResponse>(`/ged/documents/${id}/links`, payload);
  }

  deleteLink(id: number, linkId: number): Observable<void> {
    return this.http.delete<void>(`/ged/documents/${id}/links/${linkId}`);
  }

  // -- routes de commodité "documents d'une entité" --

  listByClient(clientId: number, page: number, size: number): Observable<GedDocumentPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<GedDocumentPage>(`/ged/clients/${clientId}/documents`, { params });
  }

  listByContract(contractId: number, page: number, size: number): Observable<GedDocumentPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<GedDocumentPage>(`/ged/contracts/${contractId}/documents`, { params });
  }

  listByVehicle(vehicleId: number, page: number, size: number): Observable<GedDocumentPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<GedDocumentPage>(`/ged/vehicles/${vehicleId}/documents`, { params });
  }

  listByFleet(fleetId: number, page: number, size: number): Observable<GedDocumentPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<GedDocumentPage>(`/ged/fleets/${fleetId}/documents`, { params });
  }

  /** Aucune route de commodité /ged/sinistres/{id}/documents livrée côté backend — passe
   * directement par search(). entityType 'CLAIM' testé et confirmé accepté en direct. */
  listByClaim(claimId: number, page: number, size: number): Observable<GedDocumentPage> {
    return this.search(page, size, { entityType: 'CLAIM', entityId: claimId });
  }
}
