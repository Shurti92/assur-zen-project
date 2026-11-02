import { Injectable, inject, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

import { LangueService } from '../langues/langue.service';

export interface AvailableLanguage {
  code: string;
  libelle: string;
}

const STORAGE_KEY = 'app_language';
const FALLBACK_LANG = 'fr';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translocoService = inject(TranslocoService);
  private readonly langueService = inject(LangueService);

  readonly availableLanguages = signal<AvailableLanguage[]>([]);
  readonly currentLanguage = signal<string>(this.readStoredLanguage());

  /** Syncs the active/available languages against the `/langues` referential.
   * Called once at app bootstrap — see provideAppInitializer in app.config.ts. */
  init(): void {
    this.langueService.list().subscribe({
      next: (langues) => {
        const active = langues.filter((l) => l.active);
        const options = active.map((l) => ({ code: l.code.toLowerCase(), libelle: l.libelle }));
        this.availableLanguages.set(options);
        this.translocoService.setAvailableLangs(options.map((o) => o.code));

        const stored = this.readStoredLanguage();
        const initial = options.some((o) => o.code === stored) ? stored : FALLBACK_LANG;
        this.setLanguage(initial);
      },
      error: () => {
        // Referential unreachable — keep the app usable with the persisted/default language only.
        this.setLanguage(this.currentLanguage());
      },
    });
  }

  setLanguage(code: string): void {
    this.translocoService.setActiveLang(code);
    this.currentLanguage.set(code);
    localStorage.setItem(STORAGE_KEY, code);
  }

  private readStoredLanguage(): string {
    return localStorage.getItem(STORAGE_KEY) ?? FALLBACK_LANG;
  }
}
