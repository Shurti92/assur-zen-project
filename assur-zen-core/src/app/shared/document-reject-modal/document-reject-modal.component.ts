import { Component, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { ModalComponent } from '../modal/modal.component';

const MAX_REASON = 500;

/** Fenêtre « Refuser le document » : motif obligatoire (visible par l'assuré). */
@Component({
  selector: 'app-document-reject-modal',
  imports: [FormsModule, ButtonDirective, SpinnerComponent, ModalComponent],
  template: `
    <app-modal [visible]="visible()" size="md" title="Refuser le document" (closed)="cancelled.emit()">
      <p class="small text-body-secondary">{{ subtitle() }}</p>
      <label class="form-label fw-semibold" for="drm-reason">Motif (obligatoire, montré à l'assuré)</label>
      <textarea
        [ngModel]="reason()"
        (ngModelChange)="reason.set($event)"
        [maxlength]="max"
        class="form-control"
        id="drm-reason"
        rows="3"
        placeholder="Ex. photo illisible, merci de renvoyer un scan net."
      ></textarea>
      <div class="small text-body-secondary text-end">{{ reason().length }} / {{ max }}</div>
      <div class="d-flex justify-content-end gap-2 mt-3 pt-3" style="border-top: 1px solid var(--cui-border-color)">
        <button (click)="cancelled.emit()" cButton class="dl-btn" type="button">Annuler</button>
        <button (click)="confirmed.emit(reason().trim())" [disabled]="busy() || !reason().trim()" cButton color="danger" class="dl-btn" type="button">
          @if (busy()) {
            <c-spinner size="sm" />
          }
          Refuser
        </button>
      </div>
    </app-modal>
  `,
})
export class DocumentRejectModalComponent {
  readonly visible = input(false);
  readonly subtitle = input('');
  readonly busy = input(false);
  readonly confirmed = output<string>();
  readonly cancelled = output<void>();

  readonly max = MAX_REASON;
  readonly reason = signal('');

  constructor() {
    effect(() => {
      if (this.visible()) this.reason.set('');
    });
  }
}
