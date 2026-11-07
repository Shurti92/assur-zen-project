import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { catchError, forkJoin, map, of } from 'rxjs';
import { SpinnerComponent } from '@coreui/angular';

import { QuoteTariff, QuoteTariffRequestItem, VehicleCategory } from '../../core/quotes/models/quote-tariff.model';
import { QuoteGuaranteeService } from '../../core/quotes/quote-guarantee.service';
import { QuoteTariffService } from '../../core/quotes/quote-tariff.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { EntrepriseGuaranteesComponent, GuaranteeSource } from './entreprise-guarantees/entreprise-guarantees.component';

type AmountKey = 'netPremium' | 'taxes' | 'accessories' | 'fga' | 'cedeao' | 'attestation';
type CriteriaKey = 'minFiscalPower' | 'minSeats' | 'maxSeats';
type ProductTab = 'moto' | 'automobile-vtc' | 'entreprise';

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
  /** Produits Entreprise : nom et description de la formule (null = non modifiés). */
  formulaName: string | null;
  formulaDescription: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
}

interface FormulaGroup {
  formulaId: string;
  rows: TariffRow[];
}

const VTC_PRODUCT = 'automobile-vtc';
const ENTREPRISE_PRODUCT = 'entreprise';
const ENTREPRISE_DURATION = 12;
const FORMULA_SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const PRODUCT_TABS: { key: ProductTab; label: string; formulaId: string; category: string }[] = [
  { key: 'moto', label: 'Moto', formulaId: 'tiers-simple-moto', category: 'MOTO' },
  { key: 'automobile-vtc', label: 'Automobile VTC', formulaId: 'tiers-simple-vtc', category: 'VTC' },
  { key: 'entreprise', label: 'Entreprise', formulaId: '', category: 'OPTION_1' },
];

const FORMULA_LABEL: Record<string, string> = {
  'tiers-simple-moto': 'Tiers Simple Moto',
  'tiers-simple-vtc': 'Tiers Simple VTC',
};

const ERROR_MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: 'Une ligne est invalide : vérifiez les montants, la durée et les critères.',
  TARIFF_TOTAL_MISMATCH: 'Le total doit être égal à la somme des composantes.',
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
  imports: [AlertMessageComponent, EntrepriseGuaranteesComponent, ModalComponent, SpinnerComponent],
})
export class GrilleTarifaireComponent {
  private readonly service = inject(QuoteTariffService);
  private readonly guaranteeService = inject(QuoteGuaranteeService);
  private nextKey = 1;

  readonly tabs = PRODUCT_TABS;
  readonly product = signal<ProductTab>('moto');
  readonly isVtc = computed(() => this.product() === 'automobile-vtc');
  readonly isEntreprise = computed(() => this.product() === ENTREPRISE_PRODUCT);
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
    // Compteurs de garanties : chargés une fois à l'ouverture de l'onglet Entreprise.
    effect(() => {
      if (this.isEntreprise() && !this.loading() && this.rows().length > 0) {
        untracked(() => this.loadGuaranteeCounts());
      }
    });
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
    if (this.productOf(row) === ENTREPRISE_PRODUCT) {
      return (
        !!row.durationMonths &&
        FORMULA_SLUG.test(row.formulaId.trim()) &&
        /^OPTION_[0-9]{1,2}$/.test(row.vehicleCategory.trim()) &&
        row.netPremium !== null &&
        row.netPremium >= 1 &&
        (row.formulaName ?? '').length <= 100 &&
        (row.formulaDescription ?? '').length <= 500
      );
    }
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
    if (this.isEntreprise()) return;
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
        formulaName: null,
        formulaDescription: null,
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
      if (this.productOf(r) === ENTREPRISE_PRODUCT) {
        // Seul le montant TTC fait foi : prime nette = total, le reste à 0.
        const amount = r.netPremium as number;
        return {
          productKey: r.productKey,
          formulaId: r.formulaId.trim(),
          vehicleCategory: r.vehicleCategory.trim(),
          durationMonths: r.durationMonths as number,
          netPremium: amount,
          taxes: 0,
          accessories: 0,
          fga: 0,
          cedeao: 0,
          attestation: 0,
          total: amount,
          active: r.active,
          formulaName: r.formulaName,
          formulaDescription: r.formulaDescription,
        };
      }
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

  // ------------------------------------------------------------------ Entreprise (formules et options)

  /** Formules Entreprise avec leurs options, dans l'ordre de la grille. */
  readonly packs = computed<FormulaGroup[]>(() => this.groups());

  /** Formules ayant au moins une option active. */
  readonly activePacks = computed(() => this.packs().filter((g) => g.rows.some((r) => r.active)).length);

  /** Nombre de lignes de garanties par « formule|option » ; null = lecture impossible, absent = pas encore chargé. */
  readonly guaranteeCounts = signal<Record<string, number | null>>({});
  private countsRequested = false;

  readonly editorVisible = signal(false);
  readonly editorFormula = signal('');
  readonly editorOption = signal('');
  readonly editorTitle = signal('');

  /** Options (déjà enregistrées) dont on peut copier les garanties, avec au moins une ligne. */
  readonly guaranteeSources = computed<GuaranteeSource[]>(() => {
    const counts = this.guaranteeCounts();
    const list: GuaranteeSource[] = [];
    for (const g of this.packs()) {
      for (const r of g.rows) {
        const n = counts[this.guaranteeKey(r)];
        if (r.existing && n && n > 0) {
          list.push({
            formulaId: r.formulaId,
            option: r.vehicleCategory,
            label: `${this.packTitle(g)} · ${this.optionLabel(r.vehicleCategory)} (${n} ligne${n > 1 ? 's' : ''})`,
          });
        }
      }
    }
    return list;
  });

  readonly newVisible = signal(false);
  readonly newId = signal('');
  readonly newName = signal('');
  readonly newDesc = signal('');
  readonly newP1 = signal<number | null>(null);
  readonly newP2 = signal<number | null>(null);
  readonly newError = signal<string | null>(null);

  /** Nom affiché au client, sinon identifiant mis en forme (cabinet-rh → Cabinet Rh). */
  packTitle(g: FormulaGroup): string {
    const name = g.rows.find((r) => (r.formulaName ?? '').trim())?.formulaName?.trim();
    if (name) return name;
    return g.formulaId
      .split('-')
      .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
      .join(' ');
  }

  packName(g: FormulaGroup): string {
    return g.rows.find((r) => r.formulaName !== null)?.formulaName ?? '';
  }

  packDescription(g: FormulaGroup): string {
    return g.rows.find((r) => r.formulaDescription !== null)?.formulaDescription ?? '';
  }

  packActive(g: FormulaGroup): boolean {
    return g.rows.some((r) => r.active);
  }

  /** Active ou retire d'un geste les deux options d'une formule. */
  setPackActive(g: FormulaGroup, active: boolean): void {
    this.success.set(null);
    const keys = new Set(g.rows.map((r) => r.key));
    this.rows.update((rows) => rows.map((r) => (keys.has(r.key) ? { ...r, active } : r)));
  }

  /** Nom et description sont portés par chaque option d'une formule : on les met à jour ensemble. */
  setPackText(g: FormulaGroup, field: 'formulaName' | 'formulaDescription', value: string): void {
    this.success.set(null);
    const max = field === 'formulaName' ? 100 : 500;
    const text = value.slice(0, max);
    const keys = new Set(g.rows.map((r) => r.key));
    this.rows.update((rows) => rows.map((r) => (keys.has(r.key) ? { ...r, [field]: text } : r)));
  }

  guaranteeKey(row: { formulaId: string; vehicleCategory: string }): string {
    return `${row.formulaId}|${row.vehicleCategory}`;
  }

  /** Lit le nombre de lignes de chaque option enregistrée (une lecture par option). */
  loadGuaranteeCounts(force = false): void {
    if (this.countsRequested && !force) return;
    this.countsRequested = true;
    const targets = this.rows().filter((r) => r.productKey === ENTREPRISE_PRODUCT && r.existing);
    if (!targets.length) return;
    forkJoin(
      targets.map((r) =>
        this.guaranteeService.get(r.formulaId, r.vehicleCategory).pipe(
          map((list): [string, number | null] => [this.guaranteeKey(r), list.items.length]),
          catchError((): import('rxjs').Observable<[string, number | null]> => of([this.guaranteeKey(r), null])),
        ),
      ),
    ).subscribe((entries) => this.guaranteeCounts.set(Object.fromEntries(entries)));
  }

  openGuarantees(g: FormulaGroup, row: TariffRow): void {
    this.editorFormula.set(row.formulaId);
    this.editorOption.set(row.vehicleCategory);
    this.editorTitle.set(`${this.packTitle(g)} · ${this.optionLabel(row.vehicleCategory)}`);
    this.editorVisible.set(true);
  }

  onGuaranteesSaved(count: number): void {
    this.editorVisible.set(false);
    this.guaranteeCounts.update((c) => ({ ...c, [`${this.editorFormula()}|${this.editorOption()}`]: count }));
    this.success.set(`Garanties enregistrées : ${this.editorTitle()} (${count} ligne${count > 1 ? 's' : ''}).`);
  }

  optionLabel(category: string): string {
    const m = /^OPTION_(\d+)$/.exec(category);
    return m ? `Option ${m[1]}` : category;
  }

  amountError(row: TariffRow): boolean {
    return this.productOf(row) === ENTREPRISE_PRODUCT && (row.netPremium === null || row.netPremium < 1);
  }

  openNew(): void {
    this.newId.set('');
    this.newName.set('');
    this.newDesc.set('');
    this.newP1.set(null);
    this.newP2.set(null);
    this.newError.set(null);
    this.newVisible.set(true);
  }

  newFormError(): string | null {
    const id = this.newId().trim();
    if (!FORMULA_SLUG.test(id) || id.length > 40) return 'Identifiant : minuscules, chiffres et tirets uniquement (40 caractères max).';
    if (this.rows().some((r) => r.productKey === ENTREPRISE_PRODUCT && r.formulaId === id)) return 'Cet identifiant existe déjà.';
    if (this.newName().length > 100) return 'Le nom fait 100 caractères au plus.';
    if (this.newDesc().length > 500) return 'La description fait 500 caractères au plus.';
    const p1 = this.newP1();
    const p2 = this.newP2();
    if (p1 === null || p2 === null || p1 < 1 || p2 < 1) return 'Les deux primes doivent être des montants supérieurs à 0.';
    return null;
  }

  /** Crée les deux options de la nouvelle formule ; l'enregistrement se fait avec la barre habituelle. */
  addFormula(): void {
    const problem = this.newFormError();
    if (problem) {
      this.newError.set(problem);
      return;
    }
    const id = this.newId().trim();
    const name = this.newName().trim();
    const desc = this.newDesc().trim();
    const mk = (category: string, amount: number): TariffRow => ({
      key: this.nextKey++,
      existing: false,
      productKey: ENTREPRISE_PRODUCT,
      formulaId: id,
      vehicleCategory: category,
      durationMonths: ENTREPRISE_DURATION,
      netPremium: amount,
      taxes: 0,
      accessories: 0,
      fga: 0,
      cedeao: 0,
      attestation: 0,
      minFiscalPower: null,
      minSeats: null,
      maxSeats: null,
      taxRate: null,
      active: true,
      formulaName: name || null,
      formulaDescription: desc || null,
      updatedAt: null,
      updatedBy: null,
    });
    this.success.set(null);
    this.rows.update((rows) => [...rows, mk('OPTION_1', this.newP1() as number), mk('OPTION_2', this.newP2() as number)]);
    this.newVisible.set(false);
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
    if (row.productKey === ENTREPRISE_PRODUCT) return 'entreprise';
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
        formulaName: t.formulaName ?? null,
        formulaDescription: t.formulaDescription ?? null,
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
      r.formulaName,
      r.formulaDescription,
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
