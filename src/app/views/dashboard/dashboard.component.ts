import { Component, computed, inject, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { InsuranceWidgetsComponent } from './insurance-widgets/insurance-widgets.component';
import { KpiDashboardComponent } from '../kpi-dashboard/kpi-dashboard.component';

type View = 'contrats' | 'global' | 'vignettes' | 'sortiesFonds' | 'assures';

/** Le tableau de bord KPI (vignettes/sorties de fonds/assurés, réservé ADMIN) vit ici comme
 * onglets supplémentaires plutôt que sur sa propre route — un seul tableau de bord, une seule
 * entrée de menu. KpiDashboardComponent garde sa propre logique (fetch, cache par onglet, filtre
 * de dates) mais n'a plus son propre sélecteur d'onglet : il suit `kpiActiveView` ci-dessous. */
@Component({
  templateUrl: 'dashboard.component.html',
  styleUrls: ['dashboard.component.scss'],
  imports: [InsuranceWidgetsComponent, KpiDashboardComponent],
})
export class DashboardComponent {
  private readonly authService = inject(AuthService);

  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');
  readonly view = signal<View>('contrats');

  /** Vue par défaut inoffensive quand l'onglet actif est "Contrats" — app-kpi-dashboard n'est
   * alors pas affiché, la valeur exacte n'a aucune importance. */
  readonly kpiActiveView = computed(() => (this.view() === 'contrats' ? 'global' : this.view()) as 'global' | 'vignettes' | 'sortiesFonds' | 'assures');

  private static readonly TITLES: Record<View, string> = {
    contrats: 'Contrats',
    global: "Vue d'ensemble",
    vignettes: 'Vignettes moto',
    sortiesFonds: 'Sorties de fonds',
    assures: 'Assurés',
  };

  readonly title = computed(() => DashboardComponent.TITLES[this.view()]);

  setView(view: View): void {
    this.view.set(view);
  }
}
