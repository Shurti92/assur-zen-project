import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-page500',
  templateUrl: './page500.component.html',
})
export class Page500Component {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  private readonly isInvalidUrlReason = this.route.snapshot.queryParamMap.get('reason') === 'invalid-url';

  // invalidUrlGuard no longer clears the session (see auth.guard.ts) — a signed-in user
  // landing here just hit a broken link, they don't need to log back in.
  readonly isInvalidUrl = this.isInvalidUrlReason && !this.authService.isAuthenticated();
  readonly isBrokenLink = this.isInvalidUrlReason && this.authService.isAuthenticated();

  retry(): void {
    window.location.reload();
  }

  goToDashboard(): void {
    void this.router.navigate(['/dashboard']);
  }

  goToLogin(): void {
    void this.router.navigate(['/login']);
  }
}
