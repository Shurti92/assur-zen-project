import { Component } from '@angular/core';

/** Écran de chargement — remplace l'ancien spinner rond par un skeleton qui épouse la forme de
 * la carte récap + timeline, pour ne jamais laisser un écran vide pendant l'appel réseau (voir
 * cahier des charges, section 9 "Écran 1"). */
@Component({
  selector: 'app-suivi-skeleton',
  templateUrl: './suivi-skeleton.component.html',
  styleUrl: './suivi-skeleton.component.scss',
})
export class SuiviSkeletonComponent {}
