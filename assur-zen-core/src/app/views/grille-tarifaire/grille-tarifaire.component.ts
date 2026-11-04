import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { SpinnerComponent } from '@coreui/angular';

import { QuoteTariff, QuoteTariffRequestItem, VehicleCategory } from '../../core/quotes/models/quote-tariff.model';
import { QuoteTariffService } from '../../core/quotes/quote-tariff.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

type AmountKey = 'netPremium' | 'taxes' | 'accessories' | 'fga' | 'cedeao' | 'attestation';
type CriteriaKey = 'minFiscalPower' | 'minSeats' | 'maxSeats';
type ProductTab = 'moto' | 'automobile-vtc';

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
  cedeao: number | null;
  attestation: number | null;
  minFiscalPower: number | null;
  minSeats: number | null;
  maxSeats: number | null;
  /** Taux de taxe (% de la prime nette) : conservé et renvoyé tel quel, sinon la ligne le perdrait au PUT. */
  taxRate: number | null;
  active: boolean;
  updatedAt: string | null;
  updatedBy: string | null;
}

interface FormulaGroup {
  formulaId: string;
  rows: TariffRow[];
}

const VTC_PRODUCT = 'automobile-vtc';

const PRODUCT_TABS: { key: ProductTab; label: string; formulaId: string; category: string }[] = [
  { key: 'moto', label: 'Moto', formulaId: 'tiers-simple-moto', category: 'MOTO' },
  { key: 'automobile-vtc', label: 'Automobile VTC', formulaId: 'tiers-simple-vtc', category: 'VTC' },
];

const FORMULA_LABEL: Record<string, string> = {
  'tiers-simple-moto': 'Tiers Simple Moto',
  'tiers-simple-vtc': 'Tiers Simple VTC',
};

const ERROR_MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: 'Une ligne est invalide : vérifiez les montants, la durée et les critères.',
};

/** Composantes du prix, dans l'ordre d'affichage (couleur de la barre de répartition incluse). */
export const AMOUNT_FIELDS: { key: AmountKey; label: string; color: string; vtcOnly?: boolean }[] = [
  { key: 'netPremium', label: 'Prime nette', color: '#4f46e5' },
  { key: 'accessories', label: 'Accessoires', color: '#06b6d4' },
  { key: 'taxes', label: 'Taxes', color: '#f59e0b' },
  { key: 'cedeao', label: 'CEDEAO', color: '#16a34a', vtcOnly: true },
  { key: 'attestation', label: 'Attestation', color: '#8b5cf6', vtcOnly: true },
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

  readonly tabs = PRODUCT_TABS;
  readonly product = signal<ProductTab>('moto');
  readonly isVtc = computed(() => this.product() === 'automobile-vtc');
  /** Composantes affichées : CEDEAO et attestation seulement pour le VTC. */
  readonly fields = computed(() => AMOUNT_FIELDS.filter((f) => this.isVtc() || !f.vtcOnly));

  readonly rows = signal<TariffRow[]>([]);
  /** Photo de chaque ligne telle qu'enregistrée (clé de ligne → empreinte), pour repérer les changements. */
  private readonly savedByKey = signal<Map<number, string>>(new Map());
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  /** Lignes du produit sélectionné. */
  readonly visibleRows = computed(() => this.rows().filter((r) => this.productOf(r) === this.product()));

  readonly groups = computed<FormulaGroup[]>(() => {
    const map = new Map<string, TariffRow[]>();
    for (const r of this.visibleRows()) {
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
  /** Modifications sur tous les produits : l'enregistrement envoie toute la grille. */
  readonly changeCount = computed(() => this.changedKeys().size);

  /** Même formule + catégorie + durée que plus haut dans la liste : l'API écraserait l'autre ligne. */
  readonly duplicateKeys = computed(() => {
    const seen = new Set<string>();
    const dup = new Set<number>();
    for (const r of this.rows()) {
      const id = `${r.productKey}|${r.formulaId}|${r.vehicleCategory}|${r.durationMonths}`;
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

  readonly activeRows = computed(() => this.visibleRows().filter((r) => r.active));
  readonly activeDurations = computed(() =>
    [...new Set(this.activeRows().map((r) => r.durationMonths).filter((d): d is number => !!d))].sort((a, b) => a - b),
  );
  readonly minPrice = computed(() => {
    const totals = this.activeRows().map((r) => this.total(r));
    return totals.length ? Math.min(...totals) : null;
  });
  readonly lastUpdate = computed(() => {
    const done = this.visibleRows().filter((r) => r.updatedAt);
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

  selectProduct(tab: ProductTab): void {
    this.product.set(tab);
  }

  /** Nombre de modifications en attente sur un produit (pastille de l'onglet). */
  changesFor(tab: ProductTab): number {
    const changed = this.changedKeys();
    return this.rows().filter((r) => this.productOf(r) === tab && changed.has(r.key)).length;
  }

  /** Total = somme des composantes (la gateway refuse tout autre total). */
  total(row: TariffRow): number {
    return (
      (row.netPremium ?? 0) +
      (row.taxes ?? 0) +
      (row.accessories ?? 0) +
      (row.fga ?? 0) +
      (row.cedeao ?? 0) +
      (row.attestation ?? 0)
    );
  }

  /** Part de chaque composante dans le prix, en %, pour la barre de répartition. */
  share(row: TariffRow, key: AmountKey): number {
    const total = this.total(row);
    return total > 0 ? ((row[key] ?? 0) / total) * 100 : 0;
  }

  isComplete(row: TariffRow): boolean {
    const vtc = this.productOf(row) === 'automobile-vtc';
    const amounts: AmountKey[] = vtc
      ? ['netPremium', 'taxes', 'accessories', 'fga', 'cedeao', 'attestation']
      : ['netPremium', 'taxes', 'accessories', 'fga'];
    const criteriaOk =
      !vtc ||
      (!!row.minFiscalPower &&
        !!row.minSeats &&
        !!row.maxSeats &&
        row.maxSeats >= row.minSeats);
    return (
      !!row.durationMonths &&
      row.durationMonths > 0 &&
      !!row.formulaId.trim() &&
      !!row.vehicleCategory.trim() &&
      criteriaOk &&
      amounts.every((k) => row[k] !== null && (row[k] as number) >= 0)
    );
  }

  /** Message sous la carte quand les critères VTC sont incohérents. */
  criteriaInvalid(row: TariffRow): boolean {
    return (
      this.productOf(row) === 'automobile-vtc' &&
      row.minSeats !== null &&
      row.maxSeats !== null &&
      row.maxSeats < row.minSeats
    );
  }

  formulaLabel(formulaId: string): string {
    return FORMULA_LABEL[formulaId] ?? formulaId;
  }

  categoryLabel(category: string): string {
    if (category === 'MOTO') return 'Moto';
    if (category === 'TRICYCLE') return 'Tricycle';
    return category;
  }

  icon(row: TariffRow): string {
    if (this.productOf(row) === 'automobile-vtc') return '🚕';
    return row.vehicleCategory === 'TRICYCLE' ? '🛺' : '🏍️';
  }

  update(row: TariffRow, patch: Partial<TariffRow>): void {
    this.success.set(null);
    this.rows.update((rows) => rows.map((r) => (r.key === row.key ? { ...r, ...patch } : r)));
  }

  setAmount(row: TariffRow, key: AmountKey | CriteriaKey, value: string): void {
    const amount = this.numberOf(value);
    // Ligne à taux de taxe (VTC) : les taxes suivent la prime nette, comme le calcule la gateway.
    if (key === 'netPremium' && row.taxRate != null && amount !== null) {
      this.update(row, { netPremium: amount, taxes: Math.round((amount * row.taxRate) / 100) });
      return;
    }
    this.update(row, { [key]: amount });
  }

  /** Saisie numérique : champ vide → null, sinon entier positif. */
  numberOf(value: string): number | null {
    const digits = value.replace(/\D/g, '');
    return digits === '' ? null : Number(digits);
  }

  addRow(): void {
    const tab = PRODUCT_TABS.find((t) => t.key === this.product()) ?? PRODUCT_TABS[0];
    const last = this.visibleRows().at(-1);
    const vtc = tab.key === VTC_PRODUCT;
    this.success.set(null);
    this.rows.update((rows) => [
      ...rows,
      {
        key: this.nextKey++,
        existing: false,
        productKey: tab.key,
        formulaId: last?.formulaId ?? tab.formulaId,
        vehicleCategory: last?.vehicleCategory ?? tab.category,
        durationMonths: null,
        netPremium: null,
        taxes: null,
        accessories: null,
        fga: null,
        cedeao: vtc ? null : 0,
        attestation: vtc ? null : 0,
        minFiscalPower: vtc ? (last?.minFiscalPower ?? 3) : null,
        minSeats: vtc ? (last?.minSeats ?? 4) : null,
        maxSeats: vtc ? (last?.maxSeats ?? 8) : null,
        taxRate: last?.taxRate ?? null,
        active: true,
        updatedAt: null,
        updatedBy: null,
      },
    ]);
  }

  /** Copie les montants d'un tarif vers un nouveau (autre catégorie Moto/Tricycle, ou autre tranche VTC). */
  duplicate(row: TariffRow): void {
    this.success.set(null);
    const vtc = this.productOf(row) === 'automobile-vtc';
    this.rows.update((rows) => [
      ...rows,
      {
        ...row,
        key: this.nextKey++,
        existing: false,
        vehicleCategory: vtc ? this.nextTranche(row.vehicleCategory) : row.vehicleCategory === 'MOTO' ? 'TRICYCLE' : 'MOTO',
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
    const body: QuoteTariffRequestItem[] = this.rows().map((r) => {
      const vtc = this.productOf(r) === 'automobile-vtc';
      return {
        productKey: r.productKey,
        formulaId: r.formulaId.trim(),
        vehicleCategory: r.vehicleCategory.trim(),
        durationMonths: r.durationMonths as number,
        netPremium: r.netPremium as number,
        taxes: r.taxes as number,
        accessories: r.accessories as number,
        fga: r.fga as number,
        cedeao: r.cedeao ?? 0,
        attestation: r.attestation ?? 0,
        ...(vtc ? { minFiscalPower: r.minFiscalPower, minSeats: r.minSeats, maxSeats: r.maxSeats } : {}),
        ...(r.taxRate != null ? { taxRate: r.taxRate } : {}),
        total: this.total(r),
        active: r.active,
      };
    });
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

  private productOf(row: { productKey: string }): ProductTab {
    return row.productKey === VTC_PRODUCT ? 'automobile-vtc' : 'moto';
  }

  /** VTC → VTC-2 → VTC-3 … en évitant une catégorie déjà utilisée. */
  private nextTranche(category: string): string {
    const base = category.replace(/-\d+$/, '');
    const used = new Set(this.rows().map((r) => r.vehicleCategory));
    let n = 2;
    while (used.has(`${base}-${n}`)) n++;
    return `${base}-${n}`;
  }

  private setRows(items: QuoteTariff[]): void {
    const rows: TariffRow[] = [...items]
      .sort(
        (a, b) =>
          a.productKey.localeCompare(b.productKey) ||
          a.formulaId.localeCompare(b.formulaId) ||
          a.vehicleCategory.localeCompare(b.vehicleCategory) ||
          a.durationMonths - b.durationMonths,
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
        cedeao: t.cedeao ?? 0,
        attestation: t.attestation ?? 0,
        minFiscalPower: t.minFiscalPower ?? null,
        minSeats: t.minSeats ?? null,
        maxSeats: t.maxSeats ?? null,
        taxRate: t.taxRate ?? null,
        active: t.active,
        updatedAt: t.updatedAt ?? null,
        updatedBy: t.updatedBy ?? null,
      }));
    this.rows.set(rows);
    this.savedByKey.set(new Map(rows.map((r) => [r.key, this.snapshot(r)])));
  }

  private snapshot(r: TariffRow): string {
    return JSON.stringify([
      r.productKey,
      r.formulaId,
      r.vehicleCategory,
      r.durationMonths,
      r.netPremium,
      r.taxes,
      r.accessories,
      r.fga,
      r.cedeao,
      r.attestation,
      r.minFiscalPower,
      r.minSeats,
      r.maxSeats,
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
