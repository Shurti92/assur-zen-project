import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

import { VignetteSuiviService } from './services/vignette-suivi.service';
import { SuiviPublicVignetteResponse } from './models/suivi-vignette.model';
import { SuiviHeaderComponent } from './components/suivi-header/suivi-header.component';
import { SuiviSkeletonComponent } from './components/suivi-skeleton/suivi-skeleton.component';
import { SuiviSummaryCardComponent } from './components/suivi-summary-card/suivi-summary-card.component';
import { SuiviTimelineComponent } from './components/suivi-timeline/suivi-timeline.component';
import { SuiviExceptionalStatusComponent } from './components/suivi-exceptional-status/suivi-exceptional-status.component';

type SuiviEtat = 'loading' | 'tokenAbsent' | 'tokenInvalid' | 'serverError' | 'success';

/** Mini-app PUBLIQUE dédiée, déployée séparément d'assur-zen-core (confirmé par le backend le
 * 2026-09-18 : /suivi/vignette et /suivi-sandbox/vignette ne sont pas un reverse-proxy vers la
 * SPA principale). Aucun routeur Angular : cette page n'a qu'un seul écran, le token voyage en
 * query string (`?token=...`) lu directement via `window.location.search`, jamais affiché ni
 * journalisé.
 *
 * 3 rendus réels seulement (confirmé par le backend) : succès (timeline ou bandeau exceptionnel
 * REJETEE/ANNULEE), lien invalide (404 générique, inconnu/expiré/révoqué jamais distingué),
 * erreur serveur (toute erreur ≠ 404). "Token absent" est un 4e état, purement local. */
@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.scss',
  imports: [SuiviHeaderComponent, SuiviSkeletonComponent, SuiviSummaryCardComponent, SuiviTimelineComponent, SuiviExceptionalStatusComponent],
})
export class App {
  private readonly vignetteSuiviService = inject(VignetteSuiviService);

  private readonly token = new URLSearchParams(window.location.search).get('token');

  readonly etat = signal<SuiviEtat>('loading');
  readonly result = signal<SuiviPublicVignetteResponse | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    if (!this.token) {
      this.etat.set('tokenAbsent');
      return;
    }
    this.etat.set('loading');
    this.vignetteSuiviService.suiviPublic(this.token).subscribe({
      next: (response) => {
        this.result.set(response);
        this.etat.set('success');
      },
      error: (err: unknown) => {
        const status = err instanceof HttpErrorResponse ? err.status : 0;
        this.etat.set(status === 404 ? 'tokenInvalid' : 'serverError');
      },
    });
  }
}
