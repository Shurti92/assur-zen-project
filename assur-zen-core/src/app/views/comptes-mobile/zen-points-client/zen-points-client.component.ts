import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { LoyaltyClient, LoyaltyService, loyaltyErrorMessage } from '../../../core/mobile-admin/loyalty.service';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

const MAX_REASON = 500;

/** Onglet « Zen Points » de la fiche compte mobile : solde, niveau, accord, historique, ajustement manuel. */
@Component({
  selector: 'app-zen-points-client',
  imports: [FormsModule, ButtonDirective, SpinnerComponent, AlertMessageComponent, ModalComponent],
  template: `
    @if (error(); as message) {
      <app-alert-message class="d-block mb-3" [message]="message" />
    }
    @if (info(); as message) {
      <div class="alert alert-success small py-2">{{ message }}</div>
    }

    @if (loading() && !data()) {
      <p class="dl-empty"><c-spinner size="sm" /> Chargement…</p>
    } @else if (notMember()) {
      <p class="dl-empty">Ce client n'a pas adhéré au programme Zen Points : aucun solde ni historique.</p>
    } @else if (data(); as d) {
      <div class="d-flex flex-wrap gap-3 mb-3">
        <div class="zpc-kpi"><span>Solde</span><b>{{ fmt(d.balance) }} pts</b></div>
        <div class="zpc-kpi">
          <span>Niveau</span><b>{{ d.level?.label || '—' }}</b>
          @if (d.level?.nextLevel; as next) {
            <small>{{ next.label }} dans {{ fmt(next.pointsToGo) }} pts</small>
          }
        </div>
        <div class="zpc-kpi">
          <span>Accord au programme</span>
          @if (d.accepted) {
            <b><span class="dl-chip dl-chip--success"><span class="dl-chip-dot"></span>Donné</span></b>
            <small>le {{ date(d.acceptedAt) }}</small>
          } @else {
            <b><span class="dl-chip dl-chip--neutral"><span class="dl-chip-dot"></span>Retiré</span></b>
            <small>{{ d.withdrawnAt ? 'le ' + date(d.withdrawnAt) : '' }}</small>
          }
        </div>
        <span class="flex-grow-1"></span>
        <div class="align-self-center">
          <button (click)="openAdjust()" [disabled]="!d.accepted" cButton class="dl-btn dl-btn-primary" type="button">Ajuster les points</button>
        </div>
      </div>
      @if (!d.accepted) {
        <div class="alert alert-warning small">
          Le client a retiré son accord{{ d.withdrawnAt ? ' le ' + date(d.withdrawnAt) : '' }} : l'ajustement est désactivé, ses données seront effacées après le délai de grâce.
        </div>
      }

      @if (d.history.items.length === 0) {
        <p class="dl-empty">Aucun mouvement de points.</p>
      } @else {
        <div class="dl-table-scroll">
          <table class="dl-table">
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">Motif (vu par le client)</th>
                <th scope="col">Origine</th>
                <th scope="col" class="text-end">Points</th>
              </tr>
            </thead>
            <tbody>
              @for (h of d.history.items; track h.id) {
                <tr>
                  <td class="dl-timestamp">{{ date(h.date) }}</td>
                  <td>{{ h.label }}</td>
                  <td>
                    @if (h.type === 'ADJUST') {
                      <span class="dl-chip dl-chip--warning"><span class="dl-chip-dot"></span>Ajustement admin</span>
                    } @else if (h.type === 'EXPIRE') {
                      <span class="dl-chip dl-chip--neutral"><span class="dl-chip-dot"></span>Expiration</span>
                    } @else {
                      <span class="dl-chip dl-chip--info"><span class="dl-chip-dot"></span>Règle</span>
                    }
                  </td>
                  <td class="text-end fw-semibold" [class.text-success]="h.points > 0" [class.text-danger]="h.points < 0">
                    {{ h.points > 0 ? '+' : '' }}{{ fmt(h.points) }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="d-flex align-items-center justify-content-between mt-2 small text-body-secondary">
          <span>{{ d.history.total }} mouvement(s)</span>
          <span class="d-flex align-items-center gap-2">
            <button (click)="go(page() - 1)" [disabled]="page() === 0 || loading()" cButton class="dl-btn" size="sm" type="button">Précédent</button>
            Page {{ page() + 1 }} / {{ pages() }}
            <button (click)="go(page() + 1)" [disabled]="page() + 1 >= pages() || loading()" cButton class="dl-btn" size="sm" type="button">Suivant</button>
          </span>
        </div>
      }
    }

    <app-modal [visible]="adjustVisible()" size="md" title="Ajuster les points de ce client" (closed)="adjustVisible.set(false)">
      @if (adjustError(); as message) {
        <app-alert-message class="d-block mb-3" [message]="message" />
      }
      <label class="form-label fw-semibold" for="zpc-points">Variation (positive ou négative)</label>
      <input [ngModel]="adjustPoints()" (ngModelChange)="adjustPoints.set($event)" id="zpc-points" type="number" class="form-control mb-2" placeholder="Ex. 100 ou -50" />
      <label class="form-label fw-semibold" for="zpc-reason">Motif (obligatoire, {{ max }} caractères max)</label>
      <textarea
        [ngModel]="adjustReason()"
        (ngModelChange)="adjustReason.set($event)"
        [maxlength]="max"
        id="zpc-reason"
        rows="3"
        class="form-control"
        placeholder="Ex. Geste commercial suite à un retard de traitement."
      ></textarea>
      <div class="small text-body-secondary text-end">{{ adjustReason().length }} / {{ max }}</div>
      <div class="alert alert-warning small mt-2 mb-0">
        Ce motif sera <b>affiché au client</b> dans son historique Zen Points. Rédigez-le pour lui, sans information interne.
      </div>
      @if (adjustPreview() !== null) {
        <div class="small text-body-secondary mt-2">Solde après ajustement : {{ fmt(adjustPreview()) }} pts (il ne peut pas devenir négatif).</div>
      }
      <div class="d-flex justify-content-end gap-2 mt-3 pt-3" style="border-top: 1px solid var(--cui-border-color)">
        <button (click)="adjustVisible.set(false)" cButton class="dl-btn" type="button">Annuler</button>
        <button (click)="confirmAdjust()" [disabled]="adjustBusy()" cButton class="dl-btn dl-btn-primary" type="button">
          @if (adjustBusy()) {
            <c-spinner size="sm" />
          }
          Confirmer l'ajustement
        </button>
      </div>
    </app-modal>
  `,
  styles: `
    .zpc-kpi {
      display: flex;
      flex-direction: column;
      min-width: 150px;
      padding: 10px 14px;
      border: 1px solid var(--cui-border-color);
      border-radius: 12px;
    }
    .zpc-kpi span {
      color: var(--cui-secondary-color);
      font-size: 11px;
      text-transform: uppercase;
    }
    .zpc-kpi b {
      font-size: 18px;
    }
    .zpc-kpi small {
      color: var(--cui-secondary-color);
    }
  `,
})
export class ZenPointsClientComponent {
  private readonly service = inject(LoyaltyService);

  readonly userId = input.required<number>();

  readonly max = MAX_REASON;
  readonly data = signal<LoyaltyClient | null>(null);
  readonly loading = signal(false);
  readonly notMember = signal(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  readonly page = signal(0);

  readonly adjustVisible = signal(false);
  readonly adjustPoints = signal<number | null>(null);
  readonly adjustReason = signal('');
  readonly adjustBusy = signal(false);
  readonly adjustError = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.userId();
      untracked(() => {
        this.data.set(null);
        this.notMember.set(false);
        this.info.set(null);
        this.page.set(0);
        this.load(id, 0);
      });
    });
  }

  private load(id: number, page: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.client(id, page, 20).subscribe({
      next: (c) => {
        if (id !== this.userId()) return;
        this.data.set(c);
        this.page.set(page);
        this.loading.set(false);
      },
      error: (err) => {
        if (id !== this.userId()) return;
        this.loading.set(false);
        if (err?.status === 404 && err.error?.code === 'LOYALTY_MEMBER_NOT_FOUND') this.notMember.set(true);
        else this.error.set(loyaltyErrorMessage(err, 'Impossible de charger les Zen Points du client.'));
      },
    });
  }

  go(page: number): void {
    if (page < 0 || page >= this.pages()) return;
    this.load(this.userId(), page);
  }

  pages(): number {
    const d = this.data();
    return d ? Math.max(1, Math.ceil(d.history.total / d.history.size)) : 1;
  }

  fmt(n: number | null | undefined): string {
    return (n ?? 0).toLocaleString('fr-FR');
  }

  date(iso: string | null | undefined): string {
    return iso ? new Date(iso).toLocaleDateString('fr-FR') : '—';
  }

  adjustPreview(): number | null {
    const d = this.data();
    const p = this.adjustPoints();
    return d && p !== null && Number.isInteger(p) ? d.balance + p : null;
  }

  openAdjust(): void {
    this.adjustPoints.set(null);
    this.adjustReason.set('');
    this.adjustError.set(null);
    this.adjustVisible.set(true);
  }

  confirmAdjust(): void {
    const points = this.adjustPoints();
    const reason = this.adjustReason().trim();
    if (points === null || !Number.isInteger(points) || points === 0 || Math.abs(points) > 100000) {
      this.adjustError.set('Saisissez une variation entière non nulle (100 000 au plus).');
      return;
    }
    if (!reason) {
      this.adjustError.set('Le motif est obligatoire.');
      return;
    }
    this.adjustBusy.set(true);
    this.adjustError.set(null);
    this.service.adjust(this.userId(), points, reason).subscribe({
      next: (c) => {
        this.adjustBusy.set(false);
        this.adjustVisible.set(false);
        this.data.set(c);
        this.page.set(0);
        this.info.set(`Ajustement de ${points > 0 ? '+' : ''}${points} pts enregistré.`);
      },
      error: (err) => {
        this.adjustBusy.set(false);
        this.adjustError.set(loyaltyErrorMessage(err, "Impossible d'enregistrer l'ajustement."));
      },
    });
  }
}
