import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SpinnerComponent } from '@coreui/angular';

import { MobileProductService } from '../../core/mobile-admin/mobile-product.service';
import { AssistancePlan } from '../../core/mobile-admin/models/mobile-product.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

// Limites du contrat (admin-mobile.md §11).
const MAX_PLANS = 10;
const MAX_SECTIONS = 8;
const MAX_ITEMS = 20;

interface ItemDraft {
  text: string;
  condition: string;
}

interface SectionDraft {
  title: string;
  items: ItemDraft[];
}

interface PlanDraft {
  /** Identifiant d'affichage local (jamais envoyé) : suit le niveau pendant le glisser-déposer. */
  uid: number;
  open: boolean;
  key: string;
  name: string;
  price: number | null;
  priceLabel: string;
  minVehicleAge: number | null;
  maxVehicleAge: number | null;
  active: boolean;
  benefit: boolean;
  recommended: boolean;
  sections: SectionDraft[];
}

const KEY_PATTERN = /^[a-z0-9-]{1,40}$/;

/** Onglet « Assistance » de la fiche Assurance Auto : niveaux Gratuit / Confort / Relax / Liberté. */
@Component({
  selector: 'app-assistance-plans',
  templateUrl: './assistance-plans.component.html',
  styleUrl: './assistance-plans.component.scss',
  imports: [FormsModule, CdkDropList, CdkDrag, CdkDragHandle, SpinnerComponent, AlertMessageComponent],
})
export class AssistancePlansComponent implements OnInit {
  private readonly service = inject(MobileProductService);

  readonly productId = input.required<number>();
  /** Nombre de niveaux (pour le compteur de l'onglet). */
  readonly countChange = output<number>();

  readonly limits = { plans: MAX_PLANS, sections: MAX_SECTIONS, items: MAX_ITEMS };

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly meta = signal<string | null>(null);

  /** Brouillon édité par ngModel : tableau simple pour le binding bidirectionnel. */
  plans: PlanDraft[] = [];
  private uid = 0;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.assistance(this.productId()).subscribe({
      next: (r) => {
        this.apply(r.plans, r.updatedAt, r.updatedBy);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, "Impossible de charger les niveaux d'assistance."));
        this.loading.set(false);
      },
    });
  }

  private apply(plans: AssistancePlan[], updatedAt?: string | null, updatedBy?: string | null): void {
    this.plans = [...plans]
      .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0))
      .map((p) => ({
        uid: ++this.uid,
        open: false,
        key: p.key,
        name: p.name,
        price: p.price,
        priceLabel: p.priceLabel ?? '',
        minVehicleAge: p.minVehicleAge ?? null,
        maxVehicleAge: p.maxVehicleAge ?? null,
        active: p.active ?? true,
        benefit: p.benefit ?? false,
        recommended: p.recommended ?? false,
        sections: (p.sections ?? []).map((s) => ({
          title: s.title,
          items: (s.items ?? []).map((i) => ({ text: i.text, condition: i.condition ?? '' })),
        })),
      }));
    this.countChange.emit(this.plans.length);
    this.meta.set(updatedAt ? `Dernière modification le ${new Date(updatedAt).toLocaleDateString('fr-FR')}${updatedBy ? ' par ' + updatedBy : ''}.` : null);
  }

  // ---- Niveaux ----

  toggle(plan: PlanDraft): void {
    plan.open = !plan.open;
  }

  drop(event: CdkDragDrop<PlanDraft[]>): void {
    moveItemInArray(this.plans, event.previousIndex, event.currentIndex);
  }

  addPlan(): void {
    if (this.plans.length >= MAX_PLANS) return;
    for (const p of this.plans) p.open = false;
    this.plans.push({
      uid: ++this.uid,
      open: true,
      key: '',
      name: '',
      price: 0,
      priceLabel: '',
      minVehicleAge: null,
      maxVehicleAge: null,
      active: true,
      benefit: false,
      recommended: false,
      sections: [],
    });
    this.countChange.emit(this.plans.length);
  }

  removePlan(index: number): void {
    this.plans.splice(index, 1);
    this.countChange.emit(this.plans.length);
  }

  onKeyInput(plan: PlanDraft, value: string): void {
    plan.key = value.toLowerCase().replace(/[^a-z0-9-]/g, '').slice(0, 40);
  }

  // ---- Sections et prestations ----

  addSection(plan: PlanDraft): void {
    if (plan.sections.length < MAX_SECTIONS) plan.sections.push({ title: '', items: [] });
  }

  addItem(section: SectionDraft): void {
    if (section.items.length < MAX_ITEMS) section.items.push({ text: '', condition: '' });
  }

  removeAt<T>(list: T[], index: number): void {
    list.splice(index, 1);
  }

  move<T>(list: T[], index: number, delta: -1 | 1): void {
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    moveItemInArray(list, index, target);
  }

  // ---- Affichage ----

  formatPrice(plan: PlanDraft): string {
    return `${(plan.price ?? 0).toLocaleString('fr-FR')} F`;
  }

  ageLabel(plan: PlanDraft): string {
    const { minVehicleAge: min, maxVehicleAge: max } = plan;
    if (min == null && max == null) return '';
    if (min != null && max != null) return `Véhicule ${min} – ${max} ans`;
    return min != null ? `Véhicule ≥ ${min} ans` : `Véhicule ≤ ${max} ans`;
  }

  // ---- Enregistrement ----

  save(): void {
    this.success.set(null);
    const payload = this.build();
    if (typeof payload === 'string') {
      this.error.set(payload);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.service.saveAssistance(this.productId(), payload).subscribe({
      next: (r) => {
        this.apply(r.plans, r.updatedAt, r.updatedBy);
        this.saving.set(false);
        this.success.set("Niveaux d'assistance enregistrés.");
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(this.messageOf(err, "Impossible d'enregistrer les niveaux d'assistance."));
      },
    });
  }

  /** Corps du PUT, ou le message du premier problème détecté. */
  private build(): AssistancePlan[] | string {
    const used = new Set<string>();
    const out: AssistancePlan[] = [];
    for (const [i, p] of this.plans.entries()) {
      const label = p.name.trim() || `Niveau ${i + 1}`;
      if (!p.name.trim()) return `${label} : le nom est obligatoire.`;
      const key = p.key.trim() || this.slug(p.name);
      if (!KEY_PATTERN.test(key)) return `${label} : identifiant invalide (minuscules, chiffres et tirets, 40 caractères max).`;
      if (used.has(key)) return `${label} : l'identifiant « ${key} » est déjà utilisé.`;
      used.add(key);
      const price = p.price ?? 0;
      if (!Number.isInteger(price) || price < 0) return `${label} : le prix doit être un entier supérieur ou égal à 0.`;
      for (const age of [p.minVehicleAge, p.maxVehicleAge]) {
        if (age != null && (!Number.isInteger(age) || age < 0 || age > 100)) return `${label} : l'âge du véhicule doit être compris entre 0 et 100 ans.`;
      }
      if (p.minVehicleAge != null && p.maxVehicleAge != null && p.minVehicleAge > p.maxVehicleAge) {
        return `${label} : l'âge minimum dépasse l'âge maximum.`;
      }
      const sections = [];
      for (const s of p.sections) {
        const items = s.items.map((it) => ({ text: it.text.trim(), condition: it.condition.trim() })).filter((it) => it.text);
        if (!s.title.trim()) return `${label} : chaque section doit avoir un titre.`;
        sections.push({ title: s.title.trim(), items: items.map((it) => ({ text: it.text, ...(it.condition ? { condition: it.condition } : {}) })) });
      }
      out.push({
        key,
        name: p.name.trim(),
        price,
        priceLabel: p.priceLabel.trim() || null,
        minVehicleAge: p.minVehicleAge,
        maxVehicleAge: p.maxVehicleAge,
        displayOrder: (i + 1) * 10,
        active: p.active,
        benefit: p.benefit,
        recommended: p.recommended,
        sections,
      });
    }
    return out;
  }

  private slug(name: string): string {
    return name
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 40);
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      if (err.error?.code === 'ASSISTANCE_NOT_APPLICABLE') return "L'assistance n'existe que pour l'Assurance Auto.";
      const message = typeof err.error?.message === 'string' ? err.error.message : '';
      if (message) return err.error?.field ? `${message} (champ : ${err.error.field})` : message;
    }
    return fallback;
  }
}
