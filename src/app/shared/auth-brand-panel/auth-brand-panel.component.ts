import { Component } from '@angular/core';
import { IconDirective } from '@coreui/icons-angular';

/** Left-hand brand panel shared by the public auth pages (login, OTP…) — each
 * page projects its own headline/subtitle/feature copy so the visual identity
 * stays identical while the content stays specific to that page's job. */
@Component({
  selector: 'app-auth-brand-panel',
  templateUrl: './auth-brand-panel.component.html',
  styleUrl: './auth-brand-panel.component.scss',
  imports: [IconDirective],
})
export class AuthBrandPanelComponent {}
