import { Component, computed, input } from '@angular/core';

import { SuiviPublicVignetteResponse } from '../../models/suivi-vignette.model';

@Component({
  selector: 'app-suivi-exceptional-status',
  templateUrl: './suivi-exceptional-status.component.html',
  styleUrl: './suivi-exceptional-status.component.scss',
})
export class SuiviExceptionalStatusComponent {
  readonly suivi = input.required<SuiviPublicVignetteResponse>();

  readonly titre = computed(() =>
    this.suivi().statut === 'ANNULEE' ? 'Cette demande a été annulée' : "Votre demande n'a pas pu aboutir"
  );
}
