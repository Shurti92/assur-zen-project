import { Component, computed, input } from '@angular/core';

import { SuiviPublicVignetteResponse } from '../../models/suivi-vignette.model';
import {
  SUIVI_PUBLIC_ETAPES,
  SUIVI_PUBLIC_ETAPE_LIBELLE_SECOURS,
  SuiviPublicEtapeState,
  suiviPublicEtapeState,
} from '../../statut/suivi-statut.util';

interface TimelineRow {
  state: SuiviPublicEtapeState;
  libelle: string;
  date: string | null;
}

@Component({
  selector: 'app-suivi-timeline',
  templateUrl: './suivi-timeline.component.html',
  styleUrl: './suivi-timeline.component.scss',
})
export class SuiviTimelineComponent {
  readonly suivi = input.required<SuiviPublicVignetteResponse>();

  readonly rows = computed<TimelineRow[]>(() => {
    const suivi = this.suivi();
    return SUIVI_PUBLIC_ETAPES.map((etape) => {
      const historiqueEntry = suivi.historique.find((entry) => entry.statut === etape);
      return {
        state: suiviPublicEtapeState(etape, suivi.statut),
        libelle: historiqueEntry?.libelle ?? SUIVI_PUBLIC_ETAPE_LIBELLE_SECOURS[etape],
        date: historiqueEntry?.date ?? null,
      };
    });
  });

  formatDate(value: string): string {
    return new Date(value).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
  }
}
