import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ParrainageDashboardOverviewResponse,
  ParrainageMensuelItem,
  ParrainageParStatutItem,
  ParrainageTopParrainItem,
} from './models/parrainage-dashboard.model';

@Injectable({ providedIn: 'root' })
export class ParrainageDashboardService {
  constructor(private readonly http: HttpClient) {}

  overview(): Observable<ParrainageDashboardOverviewResponse> {
    return this.http.get<ParrainageDashboardOverviewResponse>('/parrainage-dashboard');
  }

  topParrains(): Observable<ParrainageTopParrainItem[]> {
    return this.http.get<ParrainageTopParrainItem[]>('/parrainage-dashboard/top-parrains');
  }

  parStatut(): Observable<ParrainageParStatutItem[]> {
    return this.http.get<ParrainageParStatutItem[]>('/parrainage-dashboard/par-statut');
  }

  evolution(): Observable<ParrainageMensuelItem[]> {
    return this.http.get<ParrainageMensuelItem[]>('/parrainage-dashboard/evolution');
  }
}
