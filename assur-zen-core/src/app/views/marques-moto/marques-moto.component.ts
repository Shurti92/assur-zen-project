import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { SpinnerComponent } from '@coreui/angular';

import { QuoteBrandService } from '../../core/quotes/quote-brand.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

interface BrandItem {
  id: number;
  name: string;
}

// Limites du contrat (devis-en-ligne.md, « Marques du devis moto »).
const MAX_BRANDS = 200;
const MAX_LENGTH = 60;
const PREVIEW_COUNT = 5;

@Component({
  selector: 'app-marques-moto',
  templateUrl: './marques-moto.component.html',
  styleUrl: './marques-moto.component.scss',
  imports: [AlertMessageComponent, CdkDrag, CdkDragHandle, CdkDropList, SpinnerComponent],
})
export class MarquesMotoComponent {
  private readonly service = inject(QuoteBrandService);
  private nextId = 1;

  readonly maxBrands = MAX_BRANDS;
  readonly maxLength = MAX_LENGTH;

  readonly items = signal<BrandItem[]>([]);
  private readonly saved = signal<string[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly newBrand = signal('');
  readonly newBrandError = signal<string | null>(null);
  /** Erreurs renvoyées par la gateway (`brands[i]`), par identifiant d'élément. */
  readonly serverErrors = signal<Record<number, string>>({});

  readonly names = computed(() => this.items().map((i) => i.name));
  readonly changed = computed(() => JSON.stringify(this.names()) !== JSON.stringify(this.saved()));

  /** Éléments en doublon d'une marque placée plus haut (sans tenir compte de la casse). */
  readonly duplicateIds = computed(() => {
    const seen = new Set<string>();
    const dup = new Set<number>();
    for (const item of this.items()) {
      const key = item.name.trim().toLowerCase();
      if (seen.has(key)) dup.add(item.id);
      seen.add(key);
    }
    return dup;
  });
  readonly hasInvalid = computed(() => this.items().some((i) => this.invalidReason(i.name) !== null));
  readonly canSave = computed(
    () => this.changed() && !this.saving() && !this.loading() && !this.hasInvalid() && this.duplicateIds().size === 0,
  );

  readonly preview = computed(() => this.names().slice(0, PREVIEW_COUNT));
  readonly previewMore = computed(() => Math.max(0, this.items().length - PREVIEW_COUNT));

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list().subscribe({
      next: (brands) => {
        this.setBrands(brands);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger les marques.'));
        this.loading.set(false);
      },
    });
  }

  /** Motif d'invalidité d'un nom de marque, ou null s'il est valide. */
  invalidReason(name: string): string | null {
    if (!name.trim()) return 'La marque ne peut pas être vide.';
    if (name.trim().length > MAX_LENGTH) return `${MAX_LENGTH} caractères maximum.`;
    if (/[,;\r\n]/.test(name)) return 'Ni virgule, ni point-virgule, ni retour à la ligne.';
    return null;
  }

  itemError(item: BrandItem): string | null {
    if (this.serverErrors()[item.id]) return this.serverErrors()[item.id];
    if (this.duplicateIds().has(item.id)) return 'Déjà dans la liste.';
    return this.invalidReason(item.name);
  }

  onNewBrandInput(value: string): void {
    this.newBrand.set(value);
    this.newBrandError.set(null);
  }

  add(): void {
    const name = this.newBrand().trim();
    const error =
      this.invalidReason(name) ??
      (this.names().some((n) => n.trim().toLowerCase() === name.toLowerCase())
        ? 'Cette marque est déjà dans la liste.'
        : this.items().length >= MAX_BRANDS
          ? `${MAX_BRANDS} marques maximum.`
          : null);
    this.newBrandError.set(error);
    if (error) return;
    this.success.set(null);
    this.items.update((items) => [...items, { id: this.nextId++, name }]);
    this.newBrand.set('');
  }

  rename(item: BrandItem, name: string): void {
    this.success.set(null);
    this.serverErrors.set({});
    this.items.update((items) => items.map((i) => (i.id === item.id ? { ...i, name } : i)));
  }

  remove(item: BrandItem): void {
    this.success.set(null);
    this.items.update((items) => items.filter((i) => i.id !== item.id));
  }

  move(index: number, delta: -1 | 1): void {
    const target = index + delta;
    if (target < 0 || target >= this.items().length) return;
    this.items.update((items) => {
      const copy = [...items];
      moveItemInArray(copy, index, target);
      return copy;
    });
  }

  drop(event: CdkDragDrop<BrandItem[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    this.success.set(null);
    this.items.update((items) => {
      const copy = [...items];
      moveItemInArray(copy, event.previousIndex, event.currentIndex);
      return copy;
    });
  }

  sortAlphabetically(): void {
    this.items.update((items) => [...items].sort((a, b) => a.name.localeCompare(b.name, 'fr', { sensitivity: 'base' })));
  }

  cancel(): void {
    this.success.set(null);
    this.error.set(null);
    this.serverErrors.set({});
    this.setBrands(this.saved());
  }

  save(): void {
    if (!this.canSave()) return;
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    this.serverErrors.set({});
    const items = this.items();
    this.service.save(items.map((i) => i.name.trim())).subscribe({
      next: (brands) => {
        this.setBrands(brands);
        this.saving.set(false);
        this.success.set(
          brands.length ? 'Marques enregistrées.' : 'Liste vidée : l\'application revient à la saisie libre.',
        );
      },
      error: (err) => {
        this.saving.set(false);
        this.handleSaveError(err, items);
      },
    });
  }

  private handleSaveError(err: unknown, items: BrandItem[]): void {
    if (err instanceof HttpErrorResponse && err.status === 400 && err.error?.code === 'VALIDATION_FAILED') {
      const match = /^brands\[(\d+)\]$/.exec(err.error?.field ?? '');
      const item = match ? items[Number(match[1])] : undefined;
      if (item) {
        this.serverErrors.set({ [item.id]: err.error.message ?? 'Valeur invalide.' });
        return;
      }
    }
    this.error.set(this.messageOf(err, "Impossible d'enregistrer les marques."));
  }

  private setBrands(brands: string[]): void {
    this.items.set(brands.map((name) => ({ id: this.nextId++, name })));
    this.saved.set([...brands]);
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return fallback;
  }
}
