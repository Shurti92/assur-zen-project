import { Component } from '@angular/core';

/** Bandeau de tête — visible dans tous les états. Icône inline (pas cIcon/CoreUI ici : cette
 * mini-app évite volontairement toute dépendance à la bibliothèque d'icônes de la SPA
 * authentifiée, pour rester légère). */
@Component({
  selector: 'app-suivi-header',
  templateUrl: './suivi-header.component.html',
  styleUrl: './suivi-header.component.scss',
})
export class SuiviHeaderComponent {}
