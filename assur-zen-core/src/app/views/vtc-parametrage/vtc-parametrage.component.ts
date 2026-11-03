import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { SpinnerComponent } from '@coreui/angular';

import { QuoteVtcSettingsService } from '../../core/quotes/quote-vtc-settings.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { EditableListComponent, isListValid } from './editable-list.component';

// Limites du contrat : 50 plateformes max (marques : 200, comme la moto), 60 caractères.
const MAX_BRANDS = 200;
const MAX_PLATFORMS = 50;

@Component({
  selector: 'app-vtc-parametrage',
  templateUrl: './vtc-parametrage.component.html',
  styleUrl: './vtc-parametrage.component.scss',
  imports: [AlertMessageComponent, EditableListComponent, SpinnerComponent],
})
export class VtcParametrageComponent {
  private readonly service = inject(QuoteVtcSettingsService);

  readonly maxBrands = MAX_BRANDS;
  readonly maxPlatforms = MAX_PLATFORMS;

  readonly brands = signal<string[]>([]);
  readonly platforms = signal<string[]>([]);
  /** Saisie du plafond : texte pour accepter un champ momentanément vide. */
  readonly discount = signal<string>('');

  private readonly savedBrands = signal<string[]>([]);
  private readonly savedPlatforms = signal<string[]>([]);
  private readonly savedDiscount = signal<number>(0);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  readonly discountValue = computed(() => {
    const raw = this.discount().replace(',', '.').trim();
    return raw === '' ? null : Number(raw);
  });
  readonly discountValid = computed(() => {
    const v = this.discountValue();
    return v !== null && Number.isFinite(v) && v >= 0 && v <= 100;
  });

  readonly brandsChanged = computed(() => JSON.stringify(this.brands()) !== JSON.stringify(this.savedBrands()));
  readonly platformsChanged = computed(() => JSON.stringify(this.platforms()) !== JSON.stringify(this.savedPlatforms()));
  readonly discountChanged = computed(() => this.discountValid() && this.discountValue() !== this.savedDiscount());
  readonly changeCount = computed(
    () => Number(this.brandsChanged()) + Number(this.platformsChanged()) + Number(this.discountChanged()),
  );

  readonly canSave = computed(
    () =>
      this.changeCount() > 0 &&
      !this.saving() &&
      !this.loading() &&
      this.discountValid() &&
      isListValid(this.brands(), MAX_BRANDS) &&
      isListValid(this.platforms(), MAX_PLATFORMS),
  );

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      brands: this.service.brands(),
      platforms: this.service.platforms(),
      discount: this.service.maxDiscountPercent(),
    }).subscribe({
      next: ({ brands, platforms, discount }) => {
        this.apply(brands, platforms, discount);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger le paramétrage VTC.'));
        this.loading.set(false);
      },
    });
  }

  setDiscount(value: string): void {
    this.success.set(null);
    this.discount.set(value);
  }

  cancel(): void {
    this.success.set(null);
    this.error.set(null);
    this.brands.set([...this.savedBrands()]);
    this.platforms.set([...this.savedPlatforms()]);
    this.discount.set(String(this.savedDiscount()));
  }

  /** N'envoie que ce qui a changé (chaque PUT remplace la valeur entière). */
  save(): void {
    if (!this.canSave()) return;
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    forkJoin({
      brands: this.brandsChanged() ? this.service.saveBrands(this.brands().map((b) => b.trim())) : of(null),
      platforms: this.platformsChanged() ? this.service.savePlatforms(this.platforms().map((p) => p.trim())) : of(null),
      discount: this.discountChanged() ? this.service.saveMaxDiscountPercent(this.discountValue() as number) : of(null),
    }).subscribe({
      next: (r) => {
        this.apply(
          r.brands ?? this.savedBrands(),
          r.platforms ?? this.savedPlatforms(),
          r.discount ?? this.savedDiscount(),
        );
        this.saving.set(false);
        this.success.set('Paramétrage VTC enregistré.');
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(this.messageOf(err, "Impossible d'enregistrer le paramétrage VTC."));
      },
    });
  }

  private apply(brands: string[], platforms: string[], discount: number): void {
    this.brands.set([...brands]);
    this.savedBrands.set([...brands]);
    this.platforms.set([...platforms]);
    this.savedPlatforms.set([...platforms]);
    this.discount.set(String(discount));
    this.savedDiscount.set(discount);
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return fallback;
  }
}
