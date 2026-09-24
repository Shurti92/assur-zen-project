import { Component, computed, input, output } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';
import { AlertComponent } from '@coreui/angular';

export type AlertMessageVariant = 'danger' | 'warning' | 'success' | 'info';

const ICON_BY_VARIANT: Record<AlertMessageVariant, string> = {
  danger: 'cilXCircle',
  warning: 'cilWarning',
  success: 'cilCheckCircle',
  info: 'cilInfo',
};

@Component({
  selector: 'app-alert-message',
  templateUrl: './alert-message.component.html',
  imports: [AlertComponent, IconDirective],
})
export class AlertMessageComponent {
  readonly variant = input<AlertMessageVariant>('danger');
  readonly message = input.required<string>();
  readonly dismissible = input(false);

  readonly dismissed = output<void>();

  readonly iconName = computed(() => ICON_BY_VARIANT[this.variant()]);
}
