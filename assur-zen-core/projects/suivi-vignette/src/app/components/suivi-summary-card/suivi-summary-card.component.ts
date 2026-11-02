import { Component, input, signal } from '@angular/core';

import { SuiviPublicVignetteResponse } from '../../models/suivi-vignette.model';
import { SuiviStatusBadgeComponent } from '../suivi-status-badge/suivi-status-badge.component';

const COPY_FEEDBACK_DURATION_MS = 2000;

@Component({
  selector: 'app-suivi-summary-card',
  templateUrl: './suivi-summary-card.component.html',
  styleUrl: './suivi-summary-card.component.scss',
  imports: [SuiviStatusBadgeComponent],
})
export class SuiviSummaryCardComponent {
  readonly suivi = input.required<SuiviPublicVignetteResponse>();

  readonly copied = signal(false);
  private copyResetTimer?: ReturnType<typeof setTimeout>;

  formatDate(value: string): string {
    return new Date(value).toLocaleString('fr-FR', { dateStyle: 'long', timeStyle: 'short' });
  }

  async copyReference(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.suivi().reference);
      this.copied.set(true);
      clearTimeout(this.copyResetTimer);
      this.copyResetTimer = setTimeout(() => this.copied.set(false), COPY_FEEDBACK_DURATION_MS);
    } catch {
      // Presse-papiers indisponible — la référence reste affichée à l'écran.
    }
  }
}
