import { Component, input } from '@angular/core';

import { VignetteMotoStatut } from '../../models/suivi-vignette.model';
import { STATUT_VARIANT } from '../../statut/suivi-statut.util';

@Component({
  selector: 'app-suivi-status-badge',
  templateUrl: './suivi-status-badge.component.html',
})
export class SuiviStatusBadgeComponent {
  readonly statut = input.required<VignetteMotoStatut>();
  readonly label = input.required<string>();

  readonly variantClass = (): string => `dl-chip dl-chip--${STATUT_VARIANT[this.statut()]}`;
}
