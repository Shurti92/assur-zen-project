import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

interface QuoteBrandList {
  brands: string[];
}

/** Marques proposées dans le devis moto de l'application mobile — /admin/quotes/moto/brands (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class QuoteBrandService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/quotes/moto/brands';

  list(): Observable<string[]> {
    return this.http.get<QuoteBrandList>(this.base).pipe(map((r) => r.brands));
  }

  /** Remplace toute la liste (ordre conservé, doublons retirés par la gateway) ; liste vide = saisie libre. */
  save(brands: string[]): Observable<string[]> {
    return this.http.put<QuoteBrandList>(this.base, { brands }).pipe(map((r) => r.brands));
  }
}
