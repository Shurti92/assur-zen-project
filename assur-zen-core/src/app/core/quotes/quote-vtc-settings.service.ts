import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/** Paramétrage du devis automobile VTC — /admin/quotes/vtc/* (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class QuoteVtcSettingsService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/quotes/vtc';

  brands(): Observable<string[]> {
    return this.http.get<{ brands: string[] }>(`${this.base}/brands`).pipe(map((r) => r.brands));
  }

  saveBrands(brands: string[]): Observable<string[]> {
    return this.http.put<{ brands: string[] }>(`${this.base}/brands`, { brands }).pipe(map((r) => r.brands));
  }

  platforms(): Observable<string[]> {
    return this.http.get<{ platforms: string[] }>(`${this.base}/platforms`).pipe(map((r) => r.platforms));
  }

  savePlatforms(platforms: string[]): Observable<string[]> {
    return this.http
      .put<{ platforms: string[] }>(`${this.base}/platforms`, { platforms })
      .pipe(map((r) => r.platforms));
  }

  maxDiscountPercent(): Observable<number> {
    return this.http.get<{ maxDiscountPercent: number }>(`${this.base}/settings`).pipe(map((r) => r.maxDiscountPercent));
  }

  saveMaxDiscountPercent(maxDiscountPercent: number): Observable<number> {
    return this.http
      .put<{ maxDiscountPercent: number }>(`${this.base}/settings`, { maxDiscountPercent })
      .pipe(map((r) => r.maxDiscountPercent));
  }
}
