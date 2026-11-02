import { Injectable, effect, inject, signal } from '@angular/core';

import { AuthService } from './auth.service';
import { PartenaireService } from '../partenaire/partenaire.service';
import { TYPE_PROFILE_CO_FOUNDER_ID } from '../partenaire/models/partenaire.model';

/** Détermine si l'utilisateur connecté doit voir les menus Paramétrage/Administration.
 *
 * ⚠️ Limite backend connue (voir docs/api-contracts/user-type-profile-id.md) : aucune API
 * n'expose aujourd'hui le typeProfileId de l'utilisateur connecté. Ce service est prêt pour
 * le jour où le backend l'ajoutera sur `User` (réponse de connexion) — voir `user.typeProfileId`
 * ci-dessous, pas encore présent sur `AuthUser` tant que non livré. En attendant, un
 * contournement partiel existe via `AuthUser.partenaireId -> GET /partenaires/{id} ->
 * typeProfileId`, mais il ne fonctionne QUE pour un compte auto-créé depuis une fiche
 * Partenaire (aujourd'hui uniquement REPRESENTANT, qui a `declencheCreationCompte: true`) —
 * un compte CO-FOUNDER, forcément créé manuellement via Administration → Utilisateurs, n'a
 * jamais de partenaireId et ne peut donc pas être résolu par ce chemin. La restriction
 * CO-FOUNDER reste donc inopérante tant que le contrat n'est pas livré.
 *
 * Dans tous les cas (type inconnu, résolution impossible, erreur réseau…), l'accès reste
 * complet par défaut : c'est un masquage de menu, pas une garde de route, donc mieux vaut
 * afficher un peu trop que cacher à tort un accès légitime. */
@Injectable({ providedIn: 'root' })
export class NavPermissionsService {
  private readonly authService = inject(AuthService);
  private readonly partenaireService = inject(PartenaireService);

  private readonly canAccessAdminMenusSignal = signal(true);
  readonly canAccessAdminMenus = this.canAccessAdminMenusSignal.asReadonly();

  private resolvedForUserId: string | null = null;

  constructor() {
    effect(() => {
      const user = this.authService.currentUser();
      if (!user) {
        this.resolvedForUserId = null;
        this.canAccessAdminMenusSignal.set(true);
        return;
      }
      if (this.resolvedForUserId === user.id) return;
      this.resolvedForUserId = user.id;

      // Chemin direct — prêt dès que le backend expose ce champ (voir contrat).
      if (user.typeProfileId !== undefined) {
        this.canAccessAdminMenusSignal.set(user.typeProfileId !== TYPE_PROFILE_CO_FOUNDER_ID);
        return;
      }

      // Contournement partiel — ne résout correctement qu'un compte REPRESENTANT lié à un
      // partenaire ; un compte CO-FOUNDER n'a pas de partenaireId, donc reste en accès complet.
      if (!user.partenaireId) {
        this.canAccessAdminMenusSignal.set(true);
        return;
      }

      this.partenaireService.get(user.partenaireId).subscribe({
        next: (partenaire) => {
          this.canAccessAdminMenusSignal.set(partenaire.typeProfileId !== TYPE_PROFILE_CO_FOUNDER_ID);
        },
        error: () => {
          this.canAccessAdminMenusSignal.set(true);
        },
      });
    });
  }
}
