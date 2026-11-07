import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import {
  LoyaltyDiscountSettings,
  LoyaltyDiscountTier,
  LoyaltyLevel,
  LoyaltyRule,
  LoyaltyService,
  LoyaltyStats,
  loyaltyErrorMessage,
} from '../../core/mobile-admin/loyalty.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../shared/modal/modal.component';

type Tab = 'stats' | 'rules' | 'levels' | 'discounts';

/** Brouillon d'une règle (édition en ligne). */
interface RuleDraft {
  code: string;
  label: string;
  points: number | null;
  limitLabel: string;
  monthlyCap: number | null;
  active: boolean;
  displayOrder: number | null;
}

const LEVEL_CODE = /^[A-Z0-9_]{1,20}$/;

/** Programme de fidélité Zen Points (ADMIN) : statistiques, règles de points, niveaux. */
@Component({
  selector: 'app-zen-points',
  templateUrl: './zen-points.component.html',
  styleUrl: './zen-points.component.scss',
  imports: [ButtonDirective, SpinnerComponent, FormsModule, RouterLink, AlertMessageComponent, ModalComponent, ConfirmDialogComponent],
})
export class ZenPointsComponent {
  private readonly service = inject(LoyaltyService);

  readonly tab = signal<Tab>('stats');
  readonly info = signal<string | null>(null);

  // ---- Statistiques ----
  readonly stats = signal<LoyaltyStats | null>(null);
  readonly statsLoading = signal(false);
  readonly statsError = signal<string | null>(null);
  readonly capPercent = computed(() => {
    const s = this.stats();
    if (!s || !s.globalMonthlyCap) return 0;
    return Math.min(100, Math.round((s.pointsDistributedThisMonth / s.globalMonthlyCap) * 100));
  });

  // ---- Règles ----
  readonly rules = signal<RuleDraft[]>([]);
  readonly rulesLoading = signal(false);
  readonly rulesError = signal<string | null>(null);
  readonly ruleBusy = signal<string | null>(null);
  readonly ruleErrors = signal<Record<string, string>>({});

  // ---- Niveaux ----
  readonly levels = signal<LoyaltyLevel[]>([]);
  readonly levelsLoading = signal(false);
  readonly levelsError = signal<string | null>(null);
  readonly levelVisible = signal(false);
  /** null = création. */
  readonly levelEditing = signal<LoyaltyLevel | null>(null);
  readonly levelCode = signal('');
  readonly levelLabel = signal('');
  readonly levelThreshold = signal<number | null>(0);
  readonly levelPerks = signal('');
  readonly levelBusy = signal(false);
  readonly levelError = signal<string | null>(null);

  constructor() {
    this.loadStats();
    this.loadRules();
    this.loadLevels();
    this.loadDiscounts();
  }

  setTab(tab: Tab): void {
    this.tab.set(tab);
    this.info.set(null);
  }

  // ------------------------------------------------------------------ statistiques

  loadStats(): void {
    this.statsLoading.set(true);
    this.statsError.set(null);
    this.service.stats().subscribe({
      next: (s) => {
        this.stats.set(s);
        this.statsLoading.set(false);
      },
      error: (err) => {
        this.statsError.set(loyaltyErrorMessage(err, 'Impossible de charger les statistiques.'));
        this.statsLoading.set(false);
      },
    });
  }

  fmt(n: number | null | undefined): string {
    return (n ?? 0).toLocaleString('fr-FR');
  }

  // ------------------------------------------------------------------ règles

  private toDraft(r: LoyaltyRule): RuleDraft {
    return {
      code: r.code,
      label: r.label,
      points: r.points,
      limitLabel: r.limitLabel ?? '',
      monthlyCap: r.monthlyCap && r.monthlyCap > 0 ? r.monthlyCap : null,
      active: r.active,
      displayOrder: r.displayOrder,
    };
  }

  loadRules(): void {
    this.rulesLoading.set(true);
    this.rulesError.set(null);
    this.service.rules().subscribe({
      next: (list) => {
        this.rules.set([...list].sort((a, b) => a.displayOrder - b.displayOrder).map((r) => this.toDraft(r)));
        this.rulesLoading.set(false);
      },
      error: (err) => {
        this.rulesError.set(loyaltyErrorMessage(err, 'Impossible de charger les règles.'));
        this.rulesLoading.set(false);
      },
    });
  }

  ruleValid(r: RuleDraft): boolean {
    return (
      r.label.trim().length > 0 &&
      r.label.length <= 200 &&
      Number.isInteger(r.points) &&
      (r.points as number) >= 0 &&
      (r.points as number) <= 100000 &&
      Number.isInteger(r.displayOrder) &&
      (r.monthlyCap === null || (Number.isInteger(r.monthlyCap) && r.monthlyCap >= 0))
    );
  }

  saveRule(r: RuleDraft): void {
    if (!this.ruleValid(r)) {
      this.setRuleError(r.code, 'Libellé, points (0 à 100 000) et ordre sont obligatoires ; le plafond doit être un entier positif.');
      return;
    }
    this.ruleBusy.set(r.code);
    this.setRuleError(r.code, null);
    this.info.set(null);
    this.service
      .updateRule(r.code, {
        label: r.label.trim(),
        points: r.points as number,
        limitLabel: r.limitLabel.trim() || null,
        monthlyCap: r.monthlyCap ? r.monthlyCap : null,
        active: r.active,
        displayOrder: r.displayOrder as number,
      })
      .subscribe({
        next: (saved) => {
          this.rules.update((list) => list.map((x) => (x.code === saved.code ? this.toDraft(saved) : x)));
          this.ruleBusy.set(null);
          this.info.set(`Règle « ${saved.label} » enregistrée. Les nouveaux points ne s'appliquent qu'aux gains futurs.`);
        },
        error: (err) => {
          this.ruleBusy.set(null);
          this.setRuleError(r.code, loyaltyErrorMessage(err, "Impossible d'enregistrer la règle."));
        },
      });
  }

  private setRuleError(code: string, message: string | null): void {
    this.ruleErrors.update((m) => {
      const next = { ...m };
      if (message) next[code] = message;
      else delete next[code];
      return next;
    });
  }

  // ------------------------------------------------------------------ niveaux

  loadLevels(): void {
    this.levelsLoading.set(true);
    this.levelsError.set(null);
    this.service.levels().subscribe({
      next: (list) => {
        this.levels.set([...list].sort((a, b) => a.threshold - b.threshold));
        this.levelsLoading.set(false);
      },
      error: (err) => {
        this.levelsError.set(loyaltyErrorMessage(err, 'Impossible de charger les niveaux.'));
        this.levelsLoading.set(false);
      },
    });
  }

  openLevel(level: LoyaltyLevel | null): void {
    this.levelEditing.set(level);
    this.levelCode.set(level?.code ?? '');
    this.levelLabel.set(level?.label ?? '');
    this.levelThreshold.set(level?.threshold ?? 0);
    this.levelPerks.set((level?.perks ?? []).join('\n'));
    this.levelError.set(null);
    this.levelVisible.set(true);
  }

  perksList(): string[] {
    return this.levelPerks()
      .split('\n')
      .map((p) => p.trim())
      .filter((p) => p.length > 0);
  }

  levelFormError(): string | null {
    if (!this.levelEditing() && !LEVEL_CODE.test(this.levelCode().trim())) {
      return 'Code : majuscules, chiffres et _ uniquement (20 caractères max).';
    }
    const label = this.levelLabel().trim();
    if (!label || label.length > 60) return 'Le nom est obligatoire (60 caractères max).';
    const t = this.levelThreshold();
    if (t === null || !Number.isInteger(t) || t < 0) return 'Le seuil doit être un entier positif ou nul.';
    if (this.perksList().some((p) => p.length > 200)) return 'Chaque avantage fait 200 caractères max.';
    return null;
  }

  saveLevel(): void {
    const problem = this.levelFormError();
    if (problem) {
      this.levelError.set(problem);
      return;
    }
    this.levelBusy.set(true);
    this.levelError.set(null);
    const base = { label: this.levelLabel().trim(), threshold: this.levelThreshold() as number, perks: this.perksList() };
    const editing = this.levelEditing();
    const call = editing
      ? this.service.updateLevel(editing.code, base)
      : this.service.createLevel({ code: this.levelCode().trim(), ...base });
    call.subscribe({
      next: (saved) => {
        this.levelBusy.set(false);
        this.levelVisible.set(false);
        this.info.set(`Niveau « ${saved.label} » ${editing ? 'enregistré' : 'créé'}.`);
        this.loadLevels();
      },
      error: (err) => {
        this.levelBusy.set(false);
        this.levelError.set(loyaltyErrorMessage(err, "Impossible d'enregistrer le niveau."));
      },
    });
  }

  // ------------------------------------------------------------------ remises automatiques (par produit)

  readonly discounts = signal<LoyaltyDiscountSettings | null>(null);
  readonly discountsLoading = signal(false);
  readonly discountsError = signal<string | null>(null);
  readonly tierVisible = signal(false);
  /** null = création. */
  readonly tierEditing = signal<LoyaltyDiscountTier | null>(null);
  readonly tierProduct = signal('');
  readonly tierLevel = signal('');
  readonly tierMin = signal<number | null>(1);
  readonly tierMaxMonths = signal<number | null>(1);
  readonly tierPercent = signal<number | null>(null);
  readonly tierActive = signal(true);
  readonly tierBusy = signal(false);
  readonly tierError = signal<string | null>(null);
  readonly tierDeleteTarget = signal<LoyaltyDiscountTier | null>(null);

  loadDiscounts(): void {
    this.discountsLoading.set(true);
    this.discountsError.set(null);
    this.service.discounts().subscribe({
      next: (d) => {
        this.discounts.set({
          ...d,
          tiers: [...d.tiers].sort((a, b) => this.levelRank(a.levelCode) - this.levelRank(b.levelCode) || a.minMonths - b.minMonths),
        });
        this.discountsLoading.set(false);
      },
      error: (err) => {
        this.discountsError.set(loyaltyErrorMessage(err, 'Impossible de charger les remises.'));
        this.discountsLoading.set(false);
      },
    });
  }

  private levelRank(code: string): number {
    const i = this.levels().findIndex((l) => l.code === code);
    return i < 0 ? Number.MAX_SAFE_INTEGER : i;
  }

  /** Libellé lisible d'une clé produit (moto, automobile-vtc…). */
  productLabel(key: string): string {
    if (key === 'moto') return 'Moto';
    if (key === 'automobile-vtc') return 'Automobile / VTC';
    return key;
  }

  tiersOf(product: string): LoyaltyDiscountTier[] {
    return (this.discounts()?.tiers ?? []).filter((t) => t.productKey === product);
  }

  /**
   * Niveaux sans tranche propre pour ce produit. L'héritage ne va que vers le haut : un niveau utilise les tranches
   * du niveau inférieur le plus proche qui en possède ; le premier niveau n'hérite de rien (aucune remise).
   */
  inheritanceRows(product: string): { level: LoyaltyLevel; from: string | null }[] {
    const used = new Set(this.tiersOf(product).map((t) => t.levelCode));
    const levels = this.levels();
    const rows: { level: LoyaltyLevel; from: string | null }[] = [];
    levels.forEach((level, i) => {
      if (used.has(level.code)) return;
      let from: string | null = null;
      for (let j = i - 1; j >= 0; j--) {
        if (used.has(levels[j].code)) {
          from = levels[j].label;
          break;
        }
      }
      rows.push({ level, from });
    });
    return rows;
  }

  levelLabelOf(code: string): string {
    return this.levels().find((l) => l.code === code)?.label ?? code;
  }

  openTier(tier: LoyaltyDiscountTier | null, productKey?: string, levelCode?: string): void {
    this.tierEditing.set(tier);
    this.tierProduct.set(tier?.productKey ?? productKey ?? this.discounts()?.products[0] ?? '');
    this.tierLevel.set(tier?.levelCode ?? levelCode ?? this.levels()[0]?.code ?? '');
    this.tierMin.set(tier?.minMonths ?? 1);
    this.tierMaxMonths.set(tier?.maxMonths ?? 1);
    this.tierPercent.set(tier?.percent ?? null);
    this.tierActive.set(tier?.active ?? true);
    this.tierError.set(null);
    this.tierVisible.set(true);
  }

  tierFormError(): string | null {
    if (!this.tierProduct()) return 'Choisissez un produit.';
    if (!this.tierLevel()) return 'Choisissez un niveau.';
    const min = this.tierMin();
    const max = this.tierMaxMonths();
    if (min === null || max === null || !Number.isInteger(min) || !Number.isInteger(max) || min < 1 || max > 60) {
      return 'Les durées sont des nombres entiers de mois, de 1 à 60.';
    }
    if (max < min) return 'La durée maximale doit être supérieure ou égale à la durée minimale.';
    const p = this.tierPercent();
    if (p === null || !(p > 0)) return 'La remise doit être supérieure à 0 %.';
    const cap = this.discounts()?.maxPercent ?? 100;
    if (p > cap) return `La remise ne peut pas dépasser ${cap} %.`;
    return null;
  }

  saveTier(): void {
    const problem = this.tierFormError();
    if (problem) {
      this.tierError.set(problem);
      return;
    }
    this.tierBusy.set(true);
    this.tierError.set(null);
    const body = {
      levelCode: this.tierLevel(),
      productKey: this.tierProduct(),
      minMonths: this.tierMin() as number,
      maxMonths: this.tierMaxMonths() as number,
      percent: this.tierPercent() as number,
      active: this.tierActive(),
    };
    const editing = this.tierEditing();
    const call = editing ? this.service.updateDiscount(editing.id, body) : this.service.createDiscount(body);
    call.subscribe({
      next: () => {
        this.tierBusy.set(false);
        this.tierVisible.set(false);
        this.info.set(editing ? 'Tranche enregistrée.' : 'Tranche créée.');
        this.loadDiscounts();
      },
      error: (err) => {
        this.tierBusy.set(false);
        this.tierError.set(loyaltyErrorMessage(err, "Impossible d'enregistrer la tranche."));
      },
    });
  }

  confirmDeleteTier(): void {
    const target = this.tierDeleteTarget();
    if (!target) return;
    this.tierDeleteTarget.set(null);
    this.discountsError.set(null);
    this.service.deleteDiscount(target.id).subscribe({
      next: () => {
        this.info.set('Tranche supprimée.');
        this.loadDiscounts();
      },
      error: (err) => this.discountsError.set(loyaltyErrorMessage(err, 'Impossible de supprimer la tranche.')),
    });
  }

  levelClass(code: string): string {
    return code === 'BRONZE' ? 'zp-lvl zp-lvl--bronze' : code === 'SILVER' ? 'zp-lvl zp-lvl--silver' : code === 'GOLD' ? 'zp-lvl zp-lvl--gold' : 'zp-lvl';
  }
}
