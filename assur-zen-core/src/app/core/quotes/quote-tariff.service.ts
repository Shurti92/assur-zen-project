import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { QuoteTariff, QuoteTariffList, QuoteTariffRequestItem } from './models/quote-tariff.model';

/** Grille tarifaire des devis en ligne — /admin/quotes/tariffs (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class QuoteTariffService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/quotes/tariffs';

  list(): Observable<QuoteTariff[]> {
    return this.http.get<QuoteTariffList>(this.base).pipe(map((r) => r.items));
  }

  /** Crée ou met à jour des lignes (le total doit égaler la somme des composantes). */
  save(items: QuoteTariffRequestItem[]): Observable<QuoteTariff[]> {
    return this.http.put<QuoteTariffList>(this.base, { items }).pipe(map((r) => r.items));
  }
}
