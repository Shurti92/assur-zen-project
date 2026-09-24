import { HttpInterceptorFn } from '@angular/common/http';

import { environment } from '../../../environments/environment';

const isAbsoluteUrl = (url: string): boolean => /^https?:\/\//i.test(url);

export const apiPrefixInterceptor: HttpInterceptorFn = (req, next) => {
  // Actuator calls (e.g. MonitoringService) build their own base from
  // environment.actuatorUrl rather than environment.apiUrl — leave those untouched.
  if (isAbsoluteUrl(req.url) || req.url.startsWith(environment.actuatorUrl)) {
    return next(req);
  }

  const apiUrl = environment.apiUrl.replace(/\/$/, '');
  const path = req.url.replace(/^\//, '');

  return next(req.clone({ url: `${apiUrl}/${path}` }));
};
