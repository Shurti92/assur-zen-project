import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';
import { Observable, catchError, forkJoin, from, map, of } from 'rxjs';

/**
 * Backend contract: GET /i18n/{langueCode}?scope= — see docs/api-contracts/i18n.md.
 * The backend now implements this endpoint but its dictionary is still empty
 * (`200 {}`) while translations are being populated. A plain "backend first,
 * local on error" strategy would treat that empty-but-successful response as
 * authoritative and render raw keys everywhere — so instead this always
 * merges both sources: the bundled src/assets/i18n/{lang}/{scope}.json acts
 * as a complete baseline, and any key the backend does provide overrides it.
 * As the backend dictionary fills in, its entries take over automatically
 * with no frontend change required.
 *
 * Transloco passes `lang` as "scope/lang" (e.g. "sinistres/fr") when a scope
 * is requested, or a bare lang (e.g. "fr") for the root/common dictionary.
 */
@Injectable({ providedIn: 'root' })
export class AppTranslocoHttpLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);

  getTranslation(langPath: string): Observable<Translation> {
    const [first, second] = langPath.split('/');
    const scope = second ? first : null;
    const lang = second ?? first;

    let params = new HttpParams();
    if (scope) params = params.set('scope', scope);

    const backend$ = this.http.get<Translation>(`/i18n/${lang.toUpperCase()}`, { params }).pipe(catchError(() => of({})));
    const local$ = this.loadLocalFallback(lang, scope ?? 'common');

    return forkJoin([local$, backend$]).pipe(map(([local, backend]) => ({ ...local, ...backend })));
  }

  private loadLocalFallback(lang: string, scope: string): Observable<Translation> {
    return from(
      fetch(`assets/i18n/${lang}/${scope}.json`).then((res) => (res.ok ? (res.json() as Promise<Translation>) : {})),
    ).pipe(catchError(() => of({})));
  }
}
