import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';

const DEFAULT_DESCRIPTION =
  "Cette fonctionnalité arrive bientôt — l'équipe y travaille activement. Revenez prochainement.";

/** Generic placeholder for routes whose backend doesn't exist yet — reads
 * `title`/`section`/`description` off the route's `data` so one component can
 * serve any number of "coming soon" routes (see the fidelisation module).
 * `description` lets a route explain the concept up front (e.g. what a
 * "référentiel" is) instead of showing the generic blurb. */
@Component({
  selector: 'app-coming-soon',
  templateUrl: './coming-soon.component.html',
  imports: [IconDirective],
})
export class ComingSoonComponent {
  private readonly route = inject(ActivatedRoute);

  readonly title = (this.route.snapshot.data['title'] as string) ?? 'Bientôt disponible';
  readonly section = this.route.snapshot.data['section'] as string | undefined;
  readonly description = (this.route.snapshot.data['description'] as string) ?? DEFAULT_DESCRIPTION;
}
