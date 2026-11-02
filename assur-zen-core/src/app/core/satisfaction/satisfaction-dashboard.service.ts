import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ReclamationStatistiquesResponse } from '../reclamations/models/reclamation.model';
import {
  NpsBreakdownResponse,
  RepartitionNotesResponse,
  SatisfactionMensuelleResponse,
  SatisfactionOverviewResponse,
  SatisfactionParDimensionItem,
} from './models/satisfaction-dashboard.model';

@Injectable({ providedIn: 'root' })
export class SatisfactionDashboardService {
  constructor(private readonly http: HttpClient) {}

  overview(): Observable<SatisfactionOverviewResponse> {
    return this.http.get<SatisfactionOverviewResponse>('/satisfaction-dashboard/overview');
  }

  nps(): Observable<NpsBreakdownResponse> {
    return this.http.get<NpsBreakdownResponse>('/satisfaction-dashboard/nps');
  }

  mensuel(): Observable<SatisfactionMensuelleResponse[]> {
    return this.http.get<SatisfactionMensuelleResponse[]>('/satisfaction-dashboard/mensuel');
  }

  parCourtier(): Observable<SatisfactionParDimensionItem[]> {
    return this.http.get<SatisfactionParDimensionItem[]>('/satisfaction-dashboard/par-courtier');
  }

  parGestionnaire(): Observable<SatisfactionParDimensionItem[]> {
    return this.http.get<SatisfactionParDimensionItem[]>('/satisfaction-dashboard/par-gestionnaire');
  }

  parProduit(): Observable<SatisfactionParDimensionItem[]> {
    return this.http.get<SatisfactionParDimensionItem[]>('/satisfaction-dashboard/par-produit');
  }

  repartitionNotes(): Observable<RepartitionNotesResponse> {
    return this.http.get<RepartitionNotesResponse>('/satisfaction-dashboard/repartition-notes');
  }

  reclamationsStats(): Observable<ReclamationStatistiquesResponse> {
    return this.http.get<ReclamationStatistiquesResponse>('/satisfaction-dashboard/reclamations');
  }

  exportCsv(): Observable<Blob> {
    return this.http.get('/satisfaction-dashboard/export/csv', { responseType: 'blob' });
  }

  exportPdf(): Observable<Blob> {
    return this.http.get('/satisfaction-dashboard/export/pdf', { responseType: 'blob' });
  }
}
