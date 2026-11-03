import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { SpinnerComponent } from '@coreui/angular';

import { QuoteTariff, QuoteTariffRequestItem, VehicleCategory } from '../../core/quotes/models/quote-tariff.model';
import { QuoteTariffService } from '../../core/quotes/quote-tariff.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

type AmountKey = 'netPremium' | 'taxes' | 'accessories' | 'fga';

interface TariffRow {
  key: number;
  /** Ligne déjà enregistrée : formule, catégorie et durée ne sont plus modifiables. */
  existing: boolean;
  productKey: string;
  formulaId: string;
  vehicleCategory: VehicleCategory;
  durationMonths: number | null;
  netPremium: number | null;
  taxes: number | null;
  accessories: number | null;
  fga: number | null;
  active: boolean;
  updatedAt: string | null;
  updatedBy: string | null;
}

interface FormulaGroup {
  formulaId: string;
  rows: TariffRow[];
}

const DEFAULT_PRODUCT_KEY = 'moto';
const DEFAULT_FORMULA_ID = 'tiers-simple-moto';

const FORMULA_LABEL: Record<string, string> = {
  'tiers-simple-moto': 'Tiers Simple Moto',
};

const ERROR_MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: 'Une ligne est invalide : vérifiez les montants et la durée.',
};

/** Composantes du prix, dans l'ordre d'affichage (couleur de la barre de répartition incluse). */
export const AMOUNT_FIELDS: { key: AmountKey; label: string; color: string }[] = [
  { key: 'netPremium', label: 'Prime nette', color: '#4f46e5' },
  { key: 'taxes', label: 'Taxes', color: '#f59e0b' },
  { key: 'accessories', label: 'Accessoires', color: '#06b6d4' },
  { key: 'fga', label: 'FGA', color: '#ec4899' },
];

@Component({
  selector: 'app-grille-tarifaire',
  templateUrl: './grille-tarifaire.component.html',
  styleUrl: './grille-tarifaire.component.scss',
  imports: [AlertMessageComponent, SpinnerComponent],
})
export class GrilleTarifaireComponent {
  private readonly service = inject(QuoteTariffService);
  private nextKey = 1;

  readonly fields = AMOUNT_FIELDS;

  readonly rows = signal<TariffRow[]>([]);
  /** Photo de chaque ligne telle qu'enregistrée (clé de ligne → empreinte), pour repérer les changements. */
  private readonly savedByKey = signal<Map<number, string>>(new Map());
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  readonly groups = computed<FormulaGroup[]>(() => {
    const map = new Map<string, TariffRow[]>();
    for (const r of this.rows()) {
      const list = map.get(r.formulaId) ?? [];
      list.push(r);
      map.set(r.formulaId, list);
    }
    return [...map.entries()].map(([formulaId, rows]) => ({ formulaId, rows }));
  });

  readonly changedKeys = computed(() => {
    const saved = this.savedByKey();
    return new Set(this.rows().filter((r) => saved.get(r.key) !== this.snapshot(r)).map((r) => r.key));
  });
  /** Une ligne supprimée (nouvelle ligne retirée) ne compte pas : seules les modifications visibles comptent. */
  readonly changeCount = computed(() => this.changedKeys().size);

  /** Même formule + catégorie + durée que plus haut dans la liste : l'API écraserait l'autre ligne. */
  readonly duplicateKeys = computed(() => {
    const seen = new Set<string>();
    const dup = new Set<number>();
    for (const r of this.rows()) {
      const id = `${r.formulaId}|${r.vehicleCategory}|${r.durationMonths}`;
      if (r.durationMonths && seen.has(id)) dup.add(r.key);
      seen.add(id);
    }
    return dup;
  });
  readonly incomplete = computed(() => this.rows().some((r) => !this.isComplete(r)));
  readonly canSave = computed(
    () =>
      this.changeCount() > 0 &&
      !this.saving() &&
      !this.loading() &&
      !this.incomplete() &&
      this.duplicateKeys().size === 0,
  );

  readonly activeRows = computed(() => this.rows().filter((r) => r.active));
  readonly activeDurations = computed(() =>
    [...new Set(this.activeRows().map((r) => r.durationMonths).filter((d): d is number => !!d))].sort((a, b) => a - b),
  );
  readonly minPrice = computed(() => {
    const totals = this.activeRows().map((r) => this.total(r));
    return totals.length ? Math.min(...totals) : null;
  });
  readonly lastUpdate = computed(() => {
    const done = this.rows().filter((r) => r.updatedAt);
    if (!done.length) return null;
    return done.reduce((a, b) => ((a.updatedAt as string) >= (b.updatedAt as string) ? a : b));
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list().subscribe({
      next: (items) => {
        this.setRows(items);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger la grille tarifaire.'));
        this.loading.set(false);
      },
    });
  }

  /** Total = somme des composantes (la gateway refuse tout autre total). */
  total(row: TariffRow): number {
    return (row.netPremium ?? 0) + (row.taxes ?? 0) + (row.accessories ?? 0) + (row.fga ?? 0);
  }

  /** Part de chaque composante dans le prix, en %, pour la barre de répartition. */
  share(row: TariffRow, key: AmountKey): number {
    const total = this.total(row);
    return total > 0 ? ((row[key] ?? 0) / total) * 100 : 0;
  }

  isComplete(row: TariffRow): boolean {
    return (
      !!row.durationMonths &&
      row.durationMonths > 0 &&
      !!row.formulaId.trim() &&
      this.fields.every((f) => row[f.key] !== null && (row[f.key] as number) >= 0)
    );
  }

  formulaLabel(formulaId: string): string {
    return FORMULA_LABEL[formulaId] ?? formulaId;
  }

  update(row: TariffRow, patch: Partial<TariffRow>): void {
    this.success.set(null);
    this.rows.update((rows) => rows.map((r) => (r.key === row.key ? { ...r, ...patch } : r)));
  }

  setAmount(row: TariffRow, key: AmountKey, value: string): void {
    this.update(row, { [key]: this.numberOf(value) });
  }

  /** Saisie numérique : champ vide → null, sinon entier positif. */
  numberOf(value: string): number | null {
    const digits = value.replace(/\D/g, '');
    return digits === '' ? null : Number(digits);
  }

  addRow(): void {
    const last = this.rows().at(-1);
    this.success.set(null);
    this.rows.update((rows) => [
      ...rows,
      {
        key: this.nextKey++,
        existing: false,
        productKey: last?.productKey ?? DEFAULT_PRODUCT_KEY,
        formulaId: last?.formulaId ?? DEFAULT_FORMULA_ID,
        vehicleCategory: last?.vehicleCategory ?? 'MOTO',
        durationMonths: null,
        netPremium: null,
        taxes: null,
        accessories: null,
        fga: null,
        active: true,
        updatedAt: null,
        updatedBy: null,
      },
    ]);
  }

  /** Copie les montants d'un tarif vers un nouveau (autre durée ou autre catégorie). */
  duplicate(row: TariffRow): void {
    this.success.set(null);
    this.rows.update((rows) => [
      ...rows,
      {
        ...row,
        key: this.nextKey++,
        existing: false,
        vehicleCategory: row.vehicleCategory === 'MOTO' ? 'TRICYCLE' : 'MOTO',
        updatedAt: null,
        updatedBy: null,
      },
    ]);
  }

  /** Seules les lignes pas encore enregistrées peuvent être retirées (on désactive les autres). */
  removeRow(row: TariffRow): void {
    this.rows.update((rows) => rows.filter((r) => r.key !== row.key));
  }

  cancel(): void {
    this.success.set(null);
    this.error.set(null);
    this.load();
  }

  save(): void {
    if (!this.canSave()) return;
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    const body: QuoteTariffRequestItem[] = this.rows().map((r) => ({
      productKey: r.productKey,
      formulaId: r.formulaId.trim(),
      vehicleCategory: r.vehicleCategory,
      durationMonths: r.durationMonths as number,
      netPremium: r.netPremium as number,
      taxes: r.taxes as number,
      accessories: r.accessories as number,
      fga: r.fga as number,
      total: this.total(r),
      active: r.active,
    }));
    this.service.save(body).subscribe({
      next: (items) => {
        this.setRows(items);
        this.saving.set(false);
        this.success.set('Grille tarifaire enregistrée.');
      },
      error: (err) => {
        this.error.set(this.messageOf(err, "Impossible d'enregistrer la grille tarifaire."));
        this.saving.set(false);
      },
    });
  }

  formatDate(value?: string | null): string {
    if (!value) return '—';
    const d = new Date(value);
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  formatAmount(value: number): string {
    return `${value.toLocaleString('fr-FR')} F`;
  }

  private setRows(items: QuoteTariff[]): void {
    const rows: TariffRow[] = [...items]
      .sort(
        (a, b) =>
          a.productKey.localeCompare(b.productKey) ||
          a.formulaId.localeCompare(b.formulaId) ||
          a.durationMonths - b.durationMonths ||
          a.vehicleCategory.localeCompare(b.vehicleCategory),
      )
      .map((t) => ({
        key: this.nextKey++,
        existing: true,
        productKey: t.productKey,
        formulaId: t.formulaId,
        vehicleCategory: t.vehicleCategory,
        durationMonths: t.durationMonths,
        netPremium: t.netPremium,
        taxes: t.taxes,
        accessories: t.accessories,
        fga: t.fga,
        active: t.active,
        updatedAt: t.updatedAt ?? null,
        updatedBy: t.updatedBy ?? null,
      }));
    this.rows.set(rows);
    this.savedByKey.set(new Map(rows.map((r) => [r.key, this.snapshot(r)])));
  }

  private snapshot(r: TariffRow): string {
    return JSON.stringify([
      r.formulaId,
      r.vehicleCategory,
      r.durationMonths,
      r.netPremium,
      r.taxes,
      r.accessories,
      r.fga,
      r.active,
    ]);
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      const code = err.error?.code;
      if (typeof code === 'string' && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code];
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return fallback;
  }
}
