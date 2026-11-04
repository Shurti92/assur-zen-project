import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { HttpErrorResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SpinnerComponent } from '@coreui/angular';

import { MobileProductService } from '../../core/mobile-admin/mobile-product.service';
import {
  GuaranteeKind,
  MobileProduct,
  MobileProductContentRequest,
  ProductFamily,
} from '../../core/mobile-admin/models/mobile-product.model';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { AssistancePlansComponent } from './assistance-plans.component';

type EditTab = 'general' | 'guarantees' | 'formulas' | 'practical' | 'assistance';

interface GuaranteeDraft {
  /** Identifiant d'affichage local (jamais envoyé) : suit la carte pendant le glisser-déposer. */
  uid: number;
  open: boolean;
  id: string;
  name: string;
  kind: GuaranteeKind;
  description: string;
  limit: string;
  formulaIds: string[];
  visible: boolean;
}

interface FaqDraft {
  uid: number;
  open: boolean;
  question: string;
  answer: string;
}

interface FormulaDraft {
  id: string;
  name: string;
  description: string;
  visible: boolean;
}

interface Draft {
  visibleMobile: boolean;
  shortName: string;
  family: ProductFamily | '';
  icon: string;
  description: string;
  howItWorks: string;
  eligibility: string;
  benefits: string[];
  guarantees: GuaranteeDraft[];
  formulas: FormulaDraft[];
  exclusions: string[];
  requiredDocuments: string[];
  faq: FaqDraft[];
}

const FAMILY_LABEL: Record<ProductFamily, string> = {
  VEHICLE: 'Véhicule',
  PERSON: 'Personne',
  PROPERTY: 'Habitation / biens',
  PROFESSIONAL: 'Professionnel',
};

const KIND_LABEL: Record<GuaranteeKind, string> = {
  MANDATORY: 'Obligatoire',
  INCLUDED: 'Incluse',
  OPTIONAL: 'Optionnelle',
  TO_CONFIRM: 'À confirmer',
};

// Limites du contrat (admin-mobile.md §7 et §8).
const MAX_BENEFITS = 10;
const MAX_GUARANTEES = 20;
const MAX_FORMULAS = 10;
const MAX_EXCLUSIONS = 20;
const MAX_DOCUMENTS = 20;
const MAX_FAQ = 30;

/** Emoji du produit d'après son code d'icône (liste, en-tête de la fiche et aperçu). */
const ICON_GLYPH: Record<string, string> = {
  car: '🚗',
  moto: '🏍️',
  taxi: '🚕',
  health: '🩺',
  home: '🏠',
  plane: '✈️',
  truck: '🚚',
  fleet: '🚛',
  bike: '🚲',
  shield: '🛡️',
};

const ERROR_MESSAGES: Record<string, string> = {
  PRODUCT_NOT_FOUND: 'Produit introuvable.',
};

@Component({
  selector: 'app-catalogue-mobile',
  templateUrl: './catalogue-mobile.component.html',
  styleUrl: './catalogue-mobile.component.scss',
  imports: [
    SpinnerComponent,
    FormsModule,
    NgTemplateOutlet,
    CdkDropList,
    CdkDrag,
    CdkDragHandle,
    AlertMessageComponent,
    ModalComponent,
    AssistancePlansComponent,
  ],
})
export class CatalogueMobileComponent {
  private readonly service = inject(MobileProductService);

  readonly familyOptions = Object.entries(FAMILY_LABEL).map(([value, label]) => ({ value: value as ProductFamily, label }));
  readonly kindOptions = Object.entries(KIND_LABEL).map(([value, label]) => ({ value: value as GuaranteeKind, label }));
  readonly limits = {
    benefits: MAX_BENEFITS,
    guarantees: MAX_GUARANTEES,
    formulas: MAX_FORMULAS,
    exclusions: MAX_EXCLUSIONS,
    documents: MAX_DOCUMENTS,
    faq: MAX_FAQ,
  };

  // ---- Liste ----
  readonly products = signal<MobileProduct[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly listMessage = signal<string | null>(null);
  readonly busyProductId = signal<number | null>(null);
  readonly savingOrder = signal(false);

  // ---- Fiche d'édition ----
  readonly editVisible = signal(false);
  readonly editTab = signal<EditTab>('general');
  readonly editProduct = signal<MobileProduct | null>(null);
  /** Nombre de niveaux d'assistance (onglet réservé à l'Assurance Auto, productKey « auto »). */
  readonly assistanceCount = signal<number | null>(null);
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);
  /** Brouillon édité par ngModel : objet simple (pas un signal) pour permettre le binding bidirectionnel. */
  draft: Draft | null = null;

  constructor() {
    this.load();
  }

  // ================= Liste =================

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list().subscribe({
      next: (items) => {
        this.products.set(items);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger le catalogue.'));
        this.loading.set(false);
      },
    });
  }

  /** Compteurs de l'en-tête : visibles sur l'application, masqués (contenu enregistré), sans contenu. */
  readonly counts = computed(() => {
    const list = this.products();
    const visible = list.filter((p) => p.productActive && p.configured && p.visibleMobile).length;
    const noContent = list.filter((p) => !p.configured).length;
    return { total: list.length, visible, hidden: list.length - visible - noContent, noContent };
  });

  /** Nouvel avantage en cours de saisie (puces de l'onglet Général). */
  newBenefit = '';
  /** Nouvelle pièce à fournir en cours de saisie (puces de l'onglet Infos pratiques). */
  newDocument = '';
  private uidSeq = 0;

  get isAuto(): boolean {
    return this.editProduct()?.productKey === 'auto';
  }

  get visibleCount(): number {
    return this.products().filter((p) => p.productActive && p.configured && p.visibleMobile).length;
  }

  drop(event: CdkDragDrop<MobileProduct[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    const before = this.products();
    const reordered = [...before];
    moveItemInArray(reordered, event.previousIndex, event.currentIndex);
    this.products.set(reordered);
    this.savingOrder.set(true);
    this.listMessage.set(null);
    this.service.saveOrder(reordered.map((p) => p.productId)).subscribe({
      next: (items) => {
        this.products.set(items);
        this.savingOrder.set(false);
      },
      error: (err) => {
        // On revient à l'ordre précédent : l'écran ne doit pas montrer un ordre non enregistré.
        this.products.set(before);
        this.savingOrder.set(false);
        this.listMessage.set(this.messageOf(err, "Impossible d'enregistrer l'ordre."));
      },
    });
  }

  /** Bascule la visibilité : le PUT remplace tout le contenu, on renvoie donc le contenu existant. */
  toggleVisible(product: MobileProduct): void {
    if (!product.configured) return;
    this.busyProductId.set(product.productId);
    this.listMessage.set(null);
    const content = this.requestFromProduct(product);
    content.visibleMobile = !product.visibleMobile;
    this.service.save(product.productId, content).subscribe({
      next: (saved) => {
        this.replace(saved);
        this.busyProductId.set(null);
      },
      error: (err) => {
        this.busyProductId.set(null);
        this.listMessage.set(this.messageOf(err, 'Impossible de modifier la visibilité.'));
      },
    });
  }

  private replace(saved: MobileProduct): void {
    this.products.update((list) => list.map((p) => (p.productId === saved.productId ? saved : p)));
  }

  /** Reconstruit un corps de PUT identique au contenu enregistré (pour les modifications partielles). */
  private requestFromProduct(p: MobileProduct): MobileProductContentRequest {
    return {
      visibleMobile: p.visibleMobile,
      shortName: p.shortName || undefined,
      family: p.family || undefined,
      description: p.description || undefined,
      benefits: p.benefits ?? [],
      howItWorks: p.howItWorks || undefined,
      eligibility: p.eligibility || undefined,
      guarantees: (p.guarantees ?? []).map((g) => ({
        id: g.id ?? undefined,
        name: g.name,
        kind: g.kind,
        description: g.description || undefined,
        limit: g.limit || undefined,
        formulaIds: g.formulaIds ?? [],
        displayOrder: g.displayOrder ?? undefined,
        visible: g.visible ?? true,
      })),
      formulas: (p.formulas ?? []).map((f) => ({
        id: f.id,
        name: f.name,
        description: f.description || undefined,
        displayOrder: f.displayOrder ?? undefined,
        visible: f.visible ?? true,
      })),
      exclusions: p.exclusions ?? [],
      requiredDocuments: p.requiredDocuments ?? [],
      faq: p.faq ?? [],
      icon: p.icon || undefined,
      displayOrder: p.displayOrder ?? undefined,
      onlineSubscription: false,
    };
  }

  // ================= Fiche d'édition =================

  openEdit(product: MobileProduct): void {
    this.editProduct.set(product);
    this.assistanceCount.set(null);
    this.newBenefit = '';
    this.newDocument = '';
    this.formError.set(null);
    this.editTab.set('general');
    this.draft = {
      visibleMobile: product.configured ? product.visibleMobile : false,
      shortName: product.shortName ?? '',
      family: product.family ?? '',
      icon: product.icon ?? '',
      description: product.description ?? '',
      howItWorks: product.howItWorks ?? '',
      eligibility: product.eligibility ?? '',
      benefits: [...(product.benefits ?? [])],
      guarantees: (product.guarantees ?? []).map((g) => ({
        uid: ++this.uidSeq,
        open: false,
        id: g.id ?? '',
        name: g.name,
        kind: g.kind,
        description: g.description ?? '',
        limit: g.limit ?? '',
        formulaIds: [...(g.formulaIds ?? [])],
        visible: g.visible ?? true,
      })),
      formulas: (product.formulas ?? []).map((f) => ({
        id: f.id,
        name: f.name,
        description: f.description ?? '',
        visible: f.visible ?? true,
      })),
      exclusions: [...(product.exclusions ?? [])],
      requiredDocuments: [...(product.requiredDocuments ?? [])],
      faq: (product.faq ?? []).map((q) => ({ uid: ++this.uidSeq, open: false, question: q.question, answer: q.answer })),
    };
    this.editVisible.set(true);
  }

  closeEdit(): void {
    this.editVisible.set(false);
    this.draft = null;
  }

  // ---- Listes de textes (avantages, exclusions, pièces) ----

  addText(list: string[], max: number): void {
    if (list.length < max) list.push('');
  }

  removeAt<T>(list: T[], index: number): void {
    list.splice(index, 1);
  }

  move<T>(list: T[], index: number, delta: -1 | 1): void {
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    moveItemInArray(list, index, target);
  }

  // ---- Garanties ----

  addGuarantee(): void {
    if (!this.draft || this.draft.guarantees.length >= MAX_GUARANTEES) return;
    for (const g of this.draft.guarantees) g.open = false;
    this.draft.guarantees.push({ uid: ++this.uidSeq, open: true, id: '', name: '', kind: 'INCLUDED', description: '', limit: '', formulaIds: [], visible: true });
  }

  toggleGuaranteeFormula(guarantee: GuaranteeDraft, formulaId: string): void {
    const i = guarantee.formulaIds.indexOf(formulaId);
    if (i >= 0) guarantee.formulaIds.splice(i, 1);
    else guarantee.formulaIds.push(formulaId);
  }

  // ---- Formules ----

  addFormula(): void {
    if (!this.draft || this.draft.formulas.length >= MAX_FORMULAS) return;
    this.draft.formulas.push({ id: this.newFormulaId(), name: '', description: '', visible: true });
  }

  removeFormula(index: number): void {
    if (!this.draft) return;
    const [removed] = this.draft.formulas.splice(index, 1);
    // Une garantie ne peut pas référencer une formule supprimée (400 VALIDATION_FAILED côté gateway).
    for (const g of this.draft.guarantees) g.formulaIds = g.formulaIds.filter((id) => id !== removed.id);
  }

  private newFormulaId(): string {
    const used = new Set(this.draft?.formulas.map((f) => f.id));
    let n = (this.draft?.formulas.length ?? 0) + 1;
    while (used.has(`formule-${n}`)) n++;
    return `formule-${n}`;
  }

  // ---- FAQ ----

  addFaq(): void {
    if (!this.draft || this.draft.faq.length >= MAX_FAQ) return;
    for (const q of this.draft.faq) q.open = false;
    this.draft.faq.push({ uid: ++this.uidSeq, open: true, question: '', answer: '' });
  }

  // ---- Icône : minuscules, chiffres, - ou _, 40 caractères max ----

  onIconInput(value: string): void {
    if (!this.draft) return;
    this.draft.icon = value.toLowerCase().replace(/[^a-z0-9_-]/g, '').slice(0, 40);
  }

  // ---- Enregistrement ----

  save(): void {
    const product = this.editProduct();
    const d = this.draft;
    if (!product || !d) return;

    const problem = this.validate(d);
    if (problem) {
      this.formError.set(problem);
      return;
    }

    const clean = (list: string[]) => list.map((s) => s.trim()).filter(Boolean);
    const content: MobileProductContentRequest = {
      visibleMobile: d.visibleMobile,
      shortName: d.shortName.trim() || undefined,
      family: d.family || undefined,
      description: d.description.trim() || undefined,
      benefits: clean(d.benefits),
      howItWorks: d.howItWorks.trim() || undefined,
      eligibility: d.eligibility.trim() || undefined,
      guarantees: d.guarantees.map((g, i) => ({
        id: g.id || undefined,
        name: g.name.trim(),
        kind: g.kind,
        description: g.description.trim() || undefined,
        limit: g.limit.trim() || undefined,
        formulaIds: g.formulaIds,
        displayOrder: (i + 1) * 10,
        visible: g.visible,
      })),
      formulas: d.formulas.map((f, i) => ({
        id: f.id,
        name: f.name.trim(),
        description: f.description.trim() || undefined,
        displayOrder: (i + 1) * 10,
        visible: f.visible,
      })),
      exclusions: clean(d.exclusions),
      requiredDocuments: clean(d.requiredDocuments),
      faq: d.faq.map((q) => ({ question: q.question.trim(), answer: q.answer.trim() })),
      icon: d.icon || undefined,
      displayOrder: product.displayOrder ?? undefined,
      // La souscription en ligne n'existe pas encore : on ne l'active jamais depuis le portail.
      onlineSubscription: false,
    };

    this.saving.set(true);
    this.formError.set(null);
    this.service.save(product.productId, content).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.replace(saved);
        this.closeEdit();
      },
      error: (err) => {
        this.saving.set(false);
        this.formError.set(this.messageOf(err, "Impossible d'enregistrer le contenu."));
      },
    });
  }

  private validate(d: Draft): string | null {
    if (d.guarantees.some((g) => !g.name.trim())) return 'Chaque garantie doit avoir un nom.';
    if (d.formulas.some((f) => !f.name.trim())) return 'Chaque formule doit avoir un nom.';
    if (d.faq.some((q) => !q.question.trim() || !q.answer.trim())) return 'Chaque question de la FAQ doit avoir une réponse.';
    if (d.visibleMobile && !d.description.trim() && !d.shortName.trim()) {
      return 'Renseignez au moins un nom court ou une description avant de rendre le produit visible.';
    }
    return null;
  }

  // ================= Affichage =================

  familyLabel(family?: ProductFamily | null): string {
    return family ? FAMILY_LABEL[family] : '—';
  }

  /** Emoji du produit (code d'icône inconnu ou absent : bouclier). */
  glyph(icon?: string | null): string {
    return (icon && ICON_GLYPH[icon]) || '🛡️';
  }

  /** Complétude du contenu mobile en % : 10 rubriques, chacune pèse 10 points (0 si aucun contenu enregistré). */
  completeness(p: MobileProduct): number {
    if (!p.configured) return 0;
    const filled = [
      p.shortName,
      p.family,
      p.icon,
      p.description,
      p.howItWorks,
      p.eligibility,
      (p.benefits ?? []).length > 0,
      (p.guarantees ?? []).length > 0,
      (p.formulas ?? []).length > 0,
      (p.requiredDocuments ?? []).length > 0,
    ].filter(Boolean).length;
    return filled * 10;
  }

  completenessLabel(p: MobileProduct): string {
    if (!p.configured) return 'À renseigner';
    return this.completeness(p) === 100 ? 'Contenu complet' : 'Contenu à compléter';
  }

  /** Classe de la pastille de statut (maquette : ok / mut / warn). */
  pillClass(p: MobileProduct): string {
    const v = this.statusChip(p).variant;
    return v === 'success' ? 'cm-pill--ok' : v === 'warning' ? 'cm-pill--warn' : 'cm-pill--mut';
  }

  // ---- Avantages en puces + aperçu téléphone ----

  addBenefit(): void {
    const d = this.draft;
    const text = this.newBenefit.trim();
    if (!d || !text || d.benefits.length >= MAX_BENEFITS) return;
    d.benefits.push(text);
    this.newBenefit = '';
  }

  addDocument(): void {
    const d = this.draft;
    const text = this.newDocument.trim();
    if (!d || !text || d.requiredDocuments.length >= MAX_DOCUMENTS) return;
    d.requiredDocuments.push(text);
    this.newDocument = '';
  }

  // ---- Garanties : glisser-déposer, libellés, formules incluses ----

  dropGuarantee(event: CdkDragDrop<GuaranteeDraft[]>): void {
    if (this.draft) moveItemInArray(this.draft.guarantees, event.previousIndex, event.currentIndex);
  }

  dropFaq(event: CdkDragDrop<FaqDraft[]>): void {
    if (this.draft) moveItemInArray(this.draft.faq, event.previousIndex, event.currentIndex);
  }

  kindLabel(kind: GuaranteeKind): string {
    return KIND_LABEL[kind];
  }

  /** Classe de la pastille du type de garantie (maquette : req / inc / opt / tbc). */
  kindClass(kind: GuaranteeKind): string {
    const map: Record<GuaranteeKind, string> = {
      MANDATORY: 'cm-pill--req',
      INCLUDED: 'cm-pill--ok',
      OPTIONAL: 'cm-pill--warn',
      TO_CONFIRM: 'cm-pill--mut',
    };
    return map[kind];
  }

  /** Noms des formules qui incluent la garantie (puces de l'en-tête de carte). */
  guaranteeFormulaNames(g: GuaranteeDraft): string[] {
    return (this.draft?.formulas ?? []).filter((f) => g.formulaIds.includes(f.id)).map((f) => f.name || f.id);
  }

  /** Nombre de garanties incluses dans une formule. */
  formulaCount(f: FormulaDraft): number {
    return (this.draft?.guarantees ?? []).filter((g) => g.formulaIds.includes(f.id)).length;
  }

  /** Description de l'aperçu : début du texte saisi. */
  previewDescription(): string {
    const text = (this.draft?.description ?? '').trim();
    return text.length > 110 ? `${text.slice(0, 110).trimEnd()}…` : text;
  }

  previewBenefits(): string[] {
    return (this.draft?.benefits ?? []).map((b) => b.trim()).filter(Boolean).slice(0, 5);
  }

  statusChip(p: MobileProduct): { label: string; variant: string } {
    if (!p.productActive) return { label: 'Produit inactif', variant: 'neutral' };
    if (!p.configured) return { label: 'Aucun contenu', variant: 'warning' };
    return p.visibleMobile ? { label: 'Visible', variant: 'success' } : { label: 'Masqué', variant: 'neutral' };
  }

  formulaName(id: string): string {
    return this.draft?.formulas.find((f) => f.id === id)?.name || id;
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      const code = err.error?.code as string | undefined;
      if (code && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code];
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      const message = typeof err.error?.message === 'string' ? err.error.message : '';
      if (message) return err.error?.field ? `${message} (champ : ${err.error.field})` : message;
    }
    return fallback;
  }
}
