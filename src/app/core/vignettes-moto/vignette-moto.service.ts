import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { GedDocumentPage, GedDocumentResponse } from '../ged/models/ged-document.model';
import {
  ChangerStatutDemandeVignetteMotoRequest,
  DemandeVignetteMotoPage,
  DemandeVignetteMotoRequest,
  DemandeVignetteMotoResponse,
  HistoriqueDemandeVignetteMotoResponse,
  LienSuiviVignetteResponse,
  PayerVignetteCommissionsRequest,
  RejeterDemandeVignetteMotoRequest,
  RevoquerLienSuiviRequest,
  SimulationTarifVignetteMotoRequest,
  SimulationTarifVignetteMotoResponse,
  SuiviPublicVignetteResponse,
  VignetteCommissionPage,
  VignetteCommissionResponse,
  VignetteCommissionStatut,
  VignetteMotoDocumentTypeCode,
  VignetteMotoStatut,
} from './models/vignette-moto.model';

@Injectable({ providedIn: 'root' })
export class VignetteMotoService {
  constructor(private readonly http: HttpClient) {}

  /** Aucune création — juste un calcul, pour afficher le détail du tarif avant de créer la demande. */
  simulerTarification(payload: SimulationTarifVignetteMotoRequest): Observable<SimulationTarifVignetteMotoResponse> {
    return this.http.post<SimulationTarifVignetteMotoResponse>('/vignettes-moto/tarification/simuler', payload);
  }

  /** GET /vignettes-moto/demandes — seul filtre exposé côté backend : statut (pas de recherche
   * par référence/nom/période à ce stade, confirmé sur le contrat OpenAPI). */
  search(page: number, size: number, statut?: VignetteMotoStatut): Observable<DemandeVignetteMotoPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (statut) params = params.set('statut', statut);
    return this.http.get<DemandeVignetteMotoPage>('/vignettes-moto/demandes', { params });
  }

  get(id: number): Observable<DemandeVignetteMotoResponse> {
    return this.http.get<DemandeVignetteMotoResponse>(`/vignettes-moto/demandes/${id}`);
  }

  /** Statut initial BROUILLON — les montants sont calculés et figés immédiatement, ils ne
   * bougeront plus jamais même si la grille tarifaire change ensuite. */
  create(payload: DemandeVignetteMotoRequest): Observable<DemandeVignetteMotoResponse> {
    return this.http.post<DemandeVignetteMotoResponse>('/vignettes-moto/demandes', payload);
  }

  /** BROUILLON -> EN_ATTENTE_PAIEMENT. 409 si un document obligatoire manque (message backend
   * listant précisément les typeCode manquants — à afficher tel quel, il est déjà clair). */
  soumettre(id: number): Observable<DemandeVignetteMotoResponse> {
    return this.http.post<DemandeVignetteMotoResponse>(`/vignettes-moto/demandes/${id}/soumettre`, {});
  }

  /** Tant que la demande n'est pas LIVREE. */
  annuler(id: number): Observable<DemandeVignetteMotoResponse> {
    return this.http.patch<DemandeVignetteMotoResponse>(`/vignettes-moto/demandes/${id}/annuler`, {});
  }

  /** Uniquement depuis BROUILLON ou EN_ATTENTE_PAIEMENT. */
  rejeter(id: number, payload: RejeterDemandeVignetteMotoRequest): Observable<DemandeVignetteMotoResponse> {
    return this.http.patch<DemandeVignetteMotoResponse>(`/vignettes-moto/demandes/${id}/rejeter`, payload);
  }

  // -- Documents (réutilise le module GED — même schéma GedDocumentResponse/Page) --

  listDocuments(id: number, page: number, size: number): Observable<GedDocumentPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<GedDocumentPage>(`/vignettes-moto/demandes/${id}/documents`, { params });
  }

  /** typeCode voyage en query param, le fichier seul dans le corps multipart — même convention
   * que GedDocumentService.upload(). */
  uploadDocument(id: number, typeCode: VignetteMotoDocumentTypeCode, file: File): Observable<GedDocumentResponse> {
    const params = new HttpParams().set('typeCode', typeCode);
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<GedDocumentResponse>(`/vignettes-moto/demandes/${id}/documents`, formData, { params });
  }

  /** Endpoint scopé introduit le 2026-09-12 (durcissement IDOR, voir
   * docs/api-contracts/securisation-idor-2026-09-12.md, section "Correction du 2026-09-12") — à
   * utiliser à la place du générique GedDocumentService.download(), qui est resté ADMIN-only.
   * Vérifie côté backend que documentId appartient bien à la demande {id} (404 sinon, même si le
   * document existe par ailleurs). */
  downloadDocument(id: number, documentId: number): Observable<Blob> {
    return this.http.get(`/vignettes-moto/demandes/${id}/documents/${documentId}/download`, { responseType: 'blob' });
  }

  // -- Suivi d'évolution + lien SMS (livré 2026-09-17) — endpoints admin/gestionnaire --

  historique(id: number): Observable<HistoriqueDemandeVignetteMotoResponse[]> {
    return this.http.get<HistoriqueDemandeVignetteMotoResponse[]>(`/vignettes-moto/demandes/${id}/historique`);
  }

  /** ADMIN uniquement. 409 si la transition demandée n'est pas autorisée par la matrice de
   * statuts côté backend — afficher le message backend tel quel, comme rejeter()/soumettre(). */
  changerStatut(id: number, payload: ChangerStatutDemandeVignetteMotoRequest): Observable<DemandeVignetteMotoResponse> {
    return this.http.post<DemandeVignetteMotoResponse>(`/vignettes-moto/demandes/${id}/statut`, payload);
  }

  /** ADMIN uniquement — envoie aussi un SMS au client avec le nouveau lien (côté backend). */
  regenererLienSuivi(id: number): Observable<LienSuiviVignetteResponse> {
    return this.http.post<LienSuiviVignetteResponse>(`/vignettes-moto/demandes/${id}/lien-suivi/regeneration`, {});
  }

  /** ADMIN uniquement — motif obligatoire (ex. compromission). */
  revoquerLienSuivi(id: number, payload: RevoquerLienSuiviRequest): Observable<void> {
    return this.http.post<void>(`/vignettes-moto/demandes/${id}/lien-suivi/revocation`, payload);
  }

  /** Endpoint PUBLIC (aucune authentification) — appelé depuis la page de suivi client, jamais
   * depuis le back-office. 404 générique si le token est inconnu, expiré ou révoqué (volontaire
   * côté sécurité, ne jamais essayer de différencier ces 3 cas côté UI). */
  suiviPublic(token: string): Observable<SuiviPublicVignetteResponse> {
    const params = new HttpParams().set('token', token);
    return this.http.get<SuiviPublicVignetteResponse>('/vignettes-moto/demandes/suivi-public', { params });
  }

  // -- Commission apporteur (confirmé 2026-09-19) — endpoints ADMIN-only --

  listCommissions(page: number, size: number, statut?: VignetteCommissionStatut): Observable<VignetteCommissionPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (statut) params = params.set('statut', statut);
    return this.http.get<VignetteCommissionPage>('/vignettes-moto/commissions', { params });
  }

  listCommissionsByPartenaire(partenaireId: number): Observable<VignetteCommissionResponse[]> {
    return this.http.get<VignetteCommissionResponse[]>(`/vignettes-moto/commissions/partenaires/${partenaireId}`);
  }

  listCommissionsByCommercial(commercialId: number): Observable<VignetteCommissionResponse[]> {
    return this.http.get<VignetteCommissionResponse[]>(`/vignettes-moto/commissions/commerciaux/${commercialId}`);
  }

  /** Marque payé + SMS à l'apporteur. Renvoie les commissions mises à jour (statut="paid"). */
  payerCommissions(payload: PayerVignetteCommissionsRequest): Observable<VignetteCommissionResponse[]> {
    return this.http.post<VignetteCommissionResponse[]>('/vignettes-moto/commissions/payer', payload);
  }
}
