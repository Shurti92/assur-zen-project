import { HttpErrorResponse, HttpEventType, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';

import { LogService } from '../logging/log.service';

/** Jamais persistés en clair dans les logs applicatifs (localStorage, écran "Logs Applicatif" —
 * accessible à tout utilisateur connecté, exportable). Le seul cas concret à ce jour est le token
 * de suivi public de vignette moto (`?token=...`, voir `VignetteMotoService.suiviPublic()`), mais
 * la liste couvre aussi tout token d'auth qui passerait un jour en query plutôt qu'en header. */
const SENSITIVE_QUERY_PARAMS = ['token', 'accessToken', 'refreshToken'];

/** req.urlWithParams est une URL relative ("/vignettes-moto/...?token=abc") — pas de `new URL()`
 * sans base ici, on découpe simplement sur le premier "?". */
function sanitizeUrlForLogging(urlWithParams: string): string {
  const [path, query] = urlWithParams.split('?');
  if (!query) return urlWithParams;
  const params = new URLSearchParams(query);
  let redacted = false;
  for (const key of SENSITIVE_QUERY_PARAMS) {
    if (params.has(key)) {
      params.set(key, '***');
      redacted = true;
    }
  }
  return redacted ? `${path}?${params.toString()}` : urlWithParams;
}

export const loggingInterceptor: HttpInterceptorFn = (req, next) => {
  const logService = inject(LogService);
  const startedAt = performance.now();
  const loggedUrl = sanitizeUrlForLogging(req.urlWithParams);

  return next(req).pipe(
    tap({
      next: (event) => {
        if (event.type === HttpEventType.Response) {
          const durationMs = Math.round(performance.now() - startedAt);
          logService.debug('http', `${req.method} ${loggedUrl} → ${event.status} (${durationMs}ms)`);
        }
      },
      error: (error: unknown) => {
        const durationMs = Math.round(performance.now() - startedAt);
        const status = error instanceof HttpErrorResponse ? error.status : 'network error';
        logService.warn('http', `${req.method} ${loggedUrl} → ${status} (${durationMs}ms)`);
      },
    })
  );
};
