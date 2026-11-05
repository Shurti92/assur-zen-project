import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

export type VoyageSpace = 'AFRIQUE_OUEST' | 'ESPACE_SCHENGEN' | 'MONDE_ENTIER';

export const VOYAGE_SPACES: { code: VoyageSpace; label: string }[] = [
  { code: 'AFRIQUE_OUEST', label: "Afrique de l'Ouest" },
  { code: 'ESPACE_SCHENGEN', label: 'Schengen et Afrique du Nord' },
  { code: 'MONDE_ENTIER', label: 'Monde entier' },
];

export interface VoyageCountry {
  id: number;
  name: string;
  space: VoyageSpace;
  spaceLabel: string;
  active: boolean;
}

export interface VoyageCountryRequest {
  name: string;
  space: VoyageSpace;
  active: boolean;
}

export interface VoyageTariff {
  id: number;
  space: VoyageSpace;
  spaceLabel: string;
  ageMin: number;
  ageMax: number;
  /** null : tranche ouverte « plus de 180 jours (1 an) ». */
  maxDays: number | null;
  netPremium: number;
  accessories: number;
  taxes: number;
  totalPremium: number;
}

export interface VoyageTariffAmounts {
  netPremium: number;
  accessories: number;
  taxes: number;
  totalPremium: number;
}

export interface VoyageTariffCreate extends VoyageTariffAmounts {
  space: VoyageSpace;
  ageMin: number;
  ageMax: number;
  maxDays?: number;
}

export interface VoyageForm {
  spaces: { code: VoyageSpace; label: string }[];
  countries: { id: number; name: string; space: VoyageSpace; spaceLabel: string }[];
  minAge: number;
  maxAge: number;
  /** Plafond de durée (365 jours). */
  maxDurationDays?: number;
  /** Date du jour à Abidjan : pas de départ antérieur. */
  minDepartureDate?: string;
}

export interface VoyageSimulation {
  country: { id: number; name: string; space: VoyageSpace; spaceLabel: string };
  age: number;
  ageBand: string;
  durationDays: number;
  durationBand: string;
  netPremium: number;
  accessories: number;
  taxes: number;
  totalPremium: number;
}

/** Assurance voyage : pays de destination et grille (ADMIN, /admin/quotes/voyage), simulation publique. */
@Injectable({ providedIn: 'root' })
export class VoyageService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/quotes/voyage';

  countries(): Observable<VoyageCountry[]> {
    return this.http.get<{ items: VoyageCountry[] }>(`${this.base}/countries`).pipe(map((r) => r.items));
  }

  createCountry(payload: VoyageCountryRequest): Observable<VoyageCountry> {
    return this.http.post<VoyageCountry>(`${this.base}/countries`, payload);
  }

  updateCountry(id: number, payload: VoyageCountryRequest): Observable<VoyageCountry> {
    return this.http.put<VoyageCountry>(`${this.base}/countries/${id}`, payload);
  }

  deleteCountry(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/countries/${id}`);
  }

  tariffs(space?: VoyageSpace | null): Observable<VoyageTariff[]> {
    let params = new HttpParams();
    if (space) params = params.set('space', space);
    return this.http.get<{ items: VoyageTariff[] }>(`${this.base}/tariffs`, { params }).pipe(map((r) => r.items));
  }

  updateTariff(id: number, payload: VoyageTariffAmounts): Observable<VoyageTariff> {
    return this.http.put<VoyageTariff>(`${this.base}/tariffs/${id}`, payload);
  }

  createTariff(payload: VoyageTariffCreate): Observable<VoyageTariff> {
    return this.http.post<VoyageTariff>(`${this.base}/tariffs`, payload);
  }

  deleteTariff(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/tariffs/${id}`);
  }

  form(): Observable<VoyageForm> {
    return this.http.get<VoyageForm>('/public/quotes/voyage/form');
  }

  simulate(body: { countryId: number; age: number; durationDays: number }): Observable<VoyageSimulation> {
    return this.http.post<VoyageSimulation>('/public/quotes/voyage/simulate', body);
  }
}
