import { ErrorHandler, Injectable, inject } from '@angular/core';

import { LogService } from './log.service';

@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly logService = inject(LogService);

  handleError(error: unknown): void {
    const message = error instanceof Error ? error.message : String(error);
    const details = error instanceof Error ? error.stack : undefined;
    this.logService.error('error', message, details);
    console.error(error);
  }
}
