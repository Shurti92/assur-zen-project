import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-page404',
  templateUrl: './page404.component.html',
})
export class Page404Component {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly isSessionExpired = this.route.snapshot.queryParamMap.get('reason') === 'session-expired';
  readonly isRepresentantDenied = this.route.snapshot.queryParamMap.get('reason') === 'representant-denied';
  readonly isAdminOnly = this.route.snapshot.queryParamMap.get('reason') === 'admin-only';

  goToDashboard(): void {
    void this.router.navigate(['/dashboard']);
  }

  goToLogin(): void {
    void this.router.navigate(['/login']);
  }
}
