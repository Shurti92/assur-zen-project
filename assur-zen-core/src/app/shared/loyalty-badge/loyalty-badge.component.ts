import { Component, computed, input } from '@angular/core';

/** Pastille de niveau Zen Points (Silver, Gold…). N'affiche rien si le niveau est absent (non adhérent, accord retiré, Bronze). */
@Component({
  selector: 'app-loyalty-badge',
  template: `
    @if (label(); as text) {
      <span class="lb" [class.lb--silver]="tone() === 'silver'" [class.lb--gold]="tone() === 'gold'" [title]="'Niveau Zen Points : ' + text">{{ text }}</span>
    }
  `,
  styles: `
    :host {
      display: inline;
    }
    .lb {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin-left: 8px;
      padding: 1px 8px;
      border-radius: 99px;
      background: #e2e8f0;
      color: #334155;
      font-size: 11px;
      font-weight: 700;
      vertical-align: middle;
      white-space: nowrap;
    }
    .lb::before {
      content: '★';
      font-size: 10px;
    }
    .lb--silver {
      background: #e5e7eb;
      color: #4b5563;
    }
    .lb--gold {
      background: #fef08a;
      color: #854d0e;
    }
  `,
})
export class LoyaltyBadgeComponent {
  readonly level = input<string | null | undefined>(null);

  readonly label = computed(() => (this.level() ?? '').trim());
  readonly tone = computed(() => {
    const l = this.label().toLowerCase();
    if (l === 'silver' || l === 'argent') return 'silver';
    if (l === 'gold' || l === 'or') return 'gold';
    return 'other';
  });
}
