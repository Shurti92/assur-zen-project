import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterOutlet } from '@angular/router';
import { NgScrollbar } from 'ngx-scrollbar';
import { filter } from 'rxjs';

import { INavData } from '@coreui/angular';
import {
  ContainerComponent,
  ShadowOnScrollDirective,
  SidebarBrandComponent,
  SidebarComponent,
  SidebarHeaderComponent,
  SidebarNavComponent
} from '@coreui/angular';
import { TranslocoService } from '@jsverse/transloco';

import { AuthService } from '../../core/auth/auth.service';
import { NavPermissionsService } from '../../core/auth/nav-permissions.service';
import { DefaultFooterComponent, DefaultHeaderComponent } from './';
import { getNavItems } from './_nav';

function isOverflown(element: HTMLElement) {
  return (
    element.scrollHeight > element.clientHeight ||
    element.scrollWidth > element.clientWidth
  );
}

@Component({
  selector: 'app-dashboard',
  templateUrl: './default-layout.component.html',
  styleUrls: ['./default-layout.component.scss'],
  imports: [
    SidebarComponent,
    SidebarHeaderComponent,
    SidebarBrandComponent,
    SidebarNavComponent,
    ContainerComponent,
    DefaultFooterComponent,
    DefaultHeaderComponent,
    NgScrollbar,
    RouterOutlet,
    RouterLink,
    ShadowOnScrollDirective
  ]
})
export class DefaultLayoutComponent {
  private readonly authService = inject(AuthService);
  private readonly navPermissionsService = inject(NavPermissionsService);
  private readonly translocoService = inject(TranslocoService);

  /** Read inside navItems() purely so it re-evaluates on language change AND
   * once the initial translation payload finishes loading — langChanges$
   * alone doesn't fire on first load, so without translationLoaded the very
   * first render (before the async dictionary arrives) would freeze on raw
   * keys forever, since translate() calls inside a computed() don't
   * otherwise trigger a rerun when the data they depend on arrives later. */
  private readonly activeLang = toSignal(this.translocoService.langChanges$, {
    initialValue: this.translocoService.getActiveLang(),
  });
  private readonly translationLoaded = toSignal(
    this.translocoService.events$.pipe(filter((e) => e.type === 'translationLoadSuccess')),
    { initialValue: null },
  );

  public readonly navItems = computed<INavData[]>(() => {
    this.activeLang();
    this.translationLoaded();
    return this.translateNavItems(
      getNavItems(this.navPermissionsService.canAccessAdminMenus(), this.authService.currentUser()?.role === 'ADMIN'),
    );
  });
  public sidebarVisible = true;
  public readonly currentUser = this.authService.currentUser;

  private translateNavItems(items: INavData[]): INavData[] {
    return items.map((item) => ({
      ...item,
      name: item.name ? this.translocoService.translate(item.name) : item.name,
      children: item.children ? this.translateNavItems(item.children) : item.children,
    }));
  }

  userInitials(): string {
    const name = this.currentUser()?.fullName?.trim();
    if (!name) return '?';
    const parts = name.split(/\s+/);
    const first = parts[0]?.[0] ?? '';
    const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
    return (first + last).toUpperCase();
  }

  userRoleLabel(): string {
    const role = this.currentUser()?.role;
    if (!role) return '';
    return role
      .toLowerCase()
      .split(/[_\s]+/)
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }
}
