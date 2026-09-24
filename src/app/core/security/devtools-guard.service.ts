import { Injectable, inject } from '@angular/core';

import { AuthService } from '../auth/auth.service';

/** Dissuasion demandée par le métier (clic droit + raccourcis DevTools bloqués sur toute
 * l'application, déconnexion après tentatives répétées). Ce n'est volontairement PAS traité comme
 * une mesure de sécurité réelle : le code JS livré au navigateur reste toujours inspectable par un
 * utilisateur déterminé (DevTools activables sans raccourci clavier, requêtes réseau observables
 * autrement, etc.). Le vrai rempart pour les données sensibles reste — comme partout ailleurs dans
 * l'app — l'autorisation côté backend, pas ce blocage côté client. */
@Injectable({ providedIn: 'root' })
export class DevtoolsGuardService {
  private readonly authService = inject(AuthService);

  /** Nombre de tentatives d'ouverture des DevTools tolérées avant déconnexion forcée. */
  private readonly maxAttempts = 3;
  private attempts = 0;

  init(): void {
    document.addEventListener('contextmenu', (event) => event.preventDefault());
    document.addEventListener('keydown', (event) => this.handleKeydown(event));
  }

  private handleKeydown(event: KeyboardEvent): void {
    if (!this.isDevtoolsShortcut(event)) return;
    event.preventDefault();
    event.stopPropagation();
    this.registerAttempt();
  }

  /** F12 (tous navigateurs), Ctrl/Cmd+Shift+I/J/C/K (inspecteur, console, sélecteur d'élément,
   * console Firefox), Ctrl/Cmd+U (affichage du code source). */
  private isDevtoolsShortcut(event: KeyboardEvent): boolean {
    const key = event.key.toUpperCase();
    if (key === 'F12') return true;

    const modifier = event.ctrlKey || event.metaKey;
    if (!modifier) return false;

    if (key === 'U') return true;
    if (event.shiftKey && ['I', 'J', 'C', 'K'].includes(key)) return true;

    return false;
  }

  private registerAttempt(): void {
    this.attempts += 1;
    if (this.attempts < this.maxAttempts) return;

    this.attempts = 0;
    if (this.authService.currentUser()) {
      this.authService.logout();
    }
  }
}
