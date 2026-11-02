import { Component, effect, inject, input, signal } from '@angular/core';

import { AuditTrailService } from '../../core/audit-trail/audit-trail.service';
import { HistoriqueModificationResponse } from '../../core/audit-trail/models/audit-trail.model';
import { AUDIT_ACTION_LABEL, AUDIT_ACTION_VARIANT } from '../../views/audit-trail/audit-trail-status';

/**
 * Onglet "Historique" réutilisable pour n'importe quel écran de détail (Contrat, Sinistre,
 * Réclamation...). Consomme le vrai Audit Trail Engine backend (`GET /audit-logs/entity/{entityName}/{entityId}`,
 * confirmé via /v3/api-docs le 2026-08-09). Un 404 (entité pas encore couverte côté backend) est
 * traité comme "indisponible" plutôt que comme une erreur bruyante.
 *
 * Usage : <app-audit-history entiteNom="Contrat" [entiteId]="contrat().id" />
 */
@Component({
  selector: 'app-audit-history',
  templateUrl: './audit-history.component.html',
  imports: [],
})
export class AuditHistoryComponent {
  private readonly auditTrailService = inject(AuditTrailService);

  readonly entiteNom = input.required<string>();
  readonly entiteId = input.required<string | number>();

  readonly events = signal<HistoriqueModificationResponse[]>([]);
  readonly loading = signal(true);
  readonly unavailable = signal(false);

  readonly actionLabel = AUDIT_ACTION_LABEL;
  readonly actionVariant = AUDIT_ACTION_VARIANT;

  constructor() {
    effect(() => {
      const nom = this.entiteNom();
      const id = this.entiteId();
      this.fetch(nom, id);
    });
  }

  private fetch(entiteNom: string, entiteId: string | number): void {
    this.loading.set(true);
    this.unavailable.set(false);
    this.auditTrailService.getByEntity(entiteNom, entiteId).subscribe({
      next: (events) => {
        this.events.set(events);
        this.loading.set(false);
      },
      error: () => {
        // API pas encore livrée côté backend — état vide silencieux, pas une erreur affichée à l'utilisateur.
        this.events.set([]);
        this.unavailable.set(true);
        this.loading.set(false);
      },
    });
  }

  formatDateTime(value: string): string {
    return new Date(value).toLocaleString('fr-FR');
  }
}
