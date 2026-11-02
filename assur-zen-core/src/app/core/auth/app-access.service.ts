import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';

import { AuthService } from './auth.service';
import { PartenaireService } from '../partenaire/partenaire.service';
import { TYPE_PROFILE_REPRESENTANT_ID } from '../partenaire/models/partenaire.model';

/** Résout si l'utilisateur connecté a le droit d'utiliser ce back-office Angular.
 *
 * REPRESENTANT est le profil de l'application mobile "Force de Vente" — un compte
 * REPRESENTANT ne doit jamais accéder à ce back-office. Contrairement au masquage de menu
 * (NavPermissionsService, qui échoue "ouvert" par défaut), ceci est un vrai contrôle
 * d'accès : la résolution est mise en cache par session (un seul appel réseau), et
 * `checkAccess()` attend le résultat avant de laisser passer — jamais d'affichage, même
 * bref, du back-office à un compte REPRESENTANT.
 *
 * Résolution : AuthUser.partenaireId -> GET /partenaires/{id} -> typeProfileId. Fonctionne
 * de façon fiable ici (contrairement au cas CO-FOUNDER) car REPRESENTANT est justement le
 * seul Type Profil dont la création de compte est automatique et liée à un partenaireId
 * (declencheCreationCompte: true) — voir docs/api-contracts/creation-comptes-utilisateurs.md.
 * Un compte sans partenaireId (back-office classique) ou une résolution qui échoue
 * (réseau, 403…) est autorisé par défaut : on ne verrouille pas un utilisateur légitime
 * sur un incident technique. */
@Injectable({ providedIn: 'root' })
export class AppAccessService {
  private readonly authService = inject(AuthService);
  private readonly partenaireService = inject(PartenaireService);

  private cachedForUserId: string | null = null;
  private cached$: Observable<boolean> | null = null;

  checkAccess(): Observable<boolean> {
    const user = this.authService.currentUser();
    if (!user) return of(true);

    if (this.cachedForUserId === user.id && this.cached$) {
      return this.cached$;
    }
    this.cachedForUserId = user.id;

    if (!user.partenaireId) {
      this.cached$ = of(true);
      return this.cached$;
    }

    this.cached$ = this.partenaireService.get(user.partenaireId).pipe(
      map((partenaire) => partenaire.typeProfileId !== TYPE_PROFILE_REPRESENTANT_ID),
      catchError(() => of(true)),
      shareReplay(1),
    );
    return this.cached$;
  }
}
