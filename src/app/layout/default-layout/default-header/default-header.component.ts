import { NgClass, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';

import {
  AvatarComponent,
  BadgeComponent,
  BreadcrumbRouterComponent,
  ColorModeService,
  ContainerComponent,
  DropdownComponent,
  //DropdownDividerDirective,
  DropdownHeaderDirective,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  HeaderComponent,
  HeaderNavComponent,
  HeaderTogglerDirective,
  NavItemComponent,
  NavLinkDirective,
  SidebarToggleDirective
} from '@coreui/angular';

import { IconDirective } from '@coreui/icons-angular';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { environment } from '../../../../environments/environment';

type PendingHeaderAction = 'lock' | 'logout' | null;

@Component({
  selector: 'app-default-header',
  templateUrl: './default-header.component.html',
  imports: [
    NgClass,
    ContainerComponent,
    HeaderTogglerDirective,
    SidebarToggleDirective,
    IconDirective,
    HeaderNavComponent,
    // NavItemComponent,
    NavLinkDirective,
    RouterLink,
    // RouterLinkActive,
    NgTemplateOutlet,
    BreadcrumbRouterComponent,
    DropdownComponent,
    DropdownToggleDirective,
    AvatarComponent,
    DropdownMenuDirective,
    // DropdownHeaderDirective,
    DropdownItemDirective,
    // BadgeComponent,
    ConfirmDialogComponent,
    TranslocoPipe,
  ],
})
export class DefaultHeaderComponent extends HeaderComponent {
  readonly #colorModeService = inject(ColorModeService);
  readonly #authService = inject(AuthService);
  readonly #translocoService = inject(TranslocoService);
  readonly languageService = inject(LanguageService);
  readonly colorMode = this.#colorModeService.colorMode;
  readonly currentUser = this.#authService.currentUser;

  /** Only shown outside production so nothing appears for real end users. */
  readonly envBadge = (() => {
    switch (environment.envName) {
      case 'local':
        return { label: 'LOCAL', className: 'hdr-env-badge--local' };
      case 'recette':
        return { label: 'RECETTE', className: 'hdr-env-badge--recette' };
      default:
        return null;
    }
  })();

  /** Read inside the computed()s below purely so they re-evaluate on language change
   * AND once the initial translation payload finishes loading — computed() doesn't
   * otherwise know translate() depends on data that may still be in flight. */
  readonly #activeLang = toSignal(this.#translocoService.langChanges$, {
    initialValue: this.#translocoService.getActiveLang(),
  });
  readonly #translationLoaded = toSignal(
    this.#translocoService.events$.pipe(filter((e) => e.type === 'translationLoadSuccess')),
    { initialValue: null },
  );

  readonly colorModes = [
    { name: 'light', textKey: 'common.theme.light', icon: 'sun' },
    { name: 'dark', textKey: 'common.theme.dark', icon: 'moon' },
    { name: 'auto', textKey: 'common.theme.auto', icon: 'contrast' },
  ];

  readonly icons = computed(() => {
    const currentMode = this.colorMode();
    return this.colorModes.find((mode) => mode.name === currentMode)?.icon ?? 'sun';
  });

  constructor() {
    super();
  }

  sidebarId = input('sidebar1');

  readonly pendingAction = signal<PendingHeaderAction>(null);
  readonly confirmDialogVisible = computed(() => this.pendingAction() !== null);
  readonly confirmDialogTitle = computed(() => {
    this.#activeLang();
    this.#translationLoaded();
    return this.#translocoService.translate(
      this.pendingAction() === 'lock' ? 'common.header.lockDialog.title' : 'common.header.logoutDialog.title',
    );
  });
  readonly confirmDialogMessage = computed(() => {
    this.#activeLang();
    this.#translationLoaded();
    return this.#translocoService.translate(
      this.pendingAction() === 'lock' ? 'common.header.lockDialog.message' : 'common.header.logoutDialog.message',
    );
  });
  readonly confirmDialogConfirmLabel = computed(() => {
    this.#activeLang();
    this.#translationLoaded();
    return this.#translocoService.translate(
      this.pendingAction() === 'lock' ? 'common.header.lockDialog.confirm' : 'common.header.logoutDialog.confirm',
    );
  });

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

  lock(): void {
    this.pendingAction.set('lock');
  }

  logout(): void {
    this.pendingAction.set('logout');
  }

  onConfirmDialogConfirmed(): void {
    const action = this.pendingAction();
    this.pendingAction.set(null);
    if (action === 'lock') {
      this.#authService.lock();
    } else if (action === 'logout') {
      this.#authService.logout();
    }
  }

  onConfirmDialogCancelled(): void {
    this.pendingAction.set(null);
  }
}
