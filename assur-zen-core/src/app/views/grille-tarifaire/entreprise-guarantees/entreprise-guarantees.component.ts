import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { SpinnerComponent } from '@coreui/angular';

import {
  EntrepriseGuarantee,
  EntrepriseGuaranteeInput,
  GuaranteeKind,
  QuoteGuaranteeService,
} from '../../../core/quotes/quote-guarantee.service';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

/** Option d'une autre formule dont on peut copier les garanties. */
export interface GuaranteeSource {
  formulaId: string;
  option: string;
  label: string;
}

interface EditRow {
  key: number;
  kind: GuaranteeKind;
  level: number;
  name: string;
  amount: number | null;
  label: string;
  deductible: string;
  basis: string;
  active: boolean;
}

const MAX_ROWS = 80;

const KIND_LABEL: Record<GuaranteeKind, string> = { GUARANTEE: 'Garantie', LIMIT: 'Plafond', NOTE: 'Note' };

/** Éditeur de garanties (tableau) d'une formule et d'une option Entreprise. */
@Component({
  selector: 'app-entreprise-guarantees',
  templateUrl: './entreprise-guarantees.component.html',
  styleUrl: './entreprise-guarantees.component.scss',
  imports: [AlertMessageComponent, ConfirmDialogComponent, ModalComponent, SpinnerComponent],
})
export class EntrepriseGuaranteesComponent {
  private readonly service = inject(QuoteGuaranteeService);
  private nextKey = 1;

  readonly visible = input(false);
  readonly formulaId = input('');
  readonly option = input('');
  readonly title = input('');
  readonly sources = input<GuaranteeSource[]>([]);

  readonly closed = output<void>();
  readonly saved = output<{ count: number }>();

  readonly rows = signal<EditRow[]>([]);
  private readonly snapshot = signal('[]');
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly confirmClose = signal(false);
  readonly copyVisible = signal(false);
  readonly copySource = signal('');
  readonly copyBusy = signal(false);
  readonly copyError = signal<string | null>(null);

  readonly dirty = computed(() => this.fingerprint(this.rows()) !== this.snapshot());
  readonly errors = computed(() => this.validate(this.rows()));
  readonly hasErrors = computed(() => this.errors().size > 0);
  readonly canSave = computed(() => this.dirty() && !this.hasErrors() && !this.loading() && !this.saving());
  readonly kindLabel = KIND_LABEL;

  constructor() {
    effect(() => {
      if (this.visible()) {
        untracked(() => this.load());
      }
    });
  }

  // ------------------------------------------------------------------ chargement

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rows.set([]);
    this.snapshot.set('[]');
    this.service.get(this.formulaId(), this.option()).subscribe({
      next: (list) => {
        const rows = this.toRows(list.items);
        this.rows.set(rows);
        this.snapshot.set(this.fingerprint(rows));
        this.loading.set(false);
      },
      error: (err) => {
        // 404 : option sans garantie enregistrée → tableau vide.
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.loading.set(false);
          return;
        }
        this.error.set(this.messageOf(err, 'Impossible de charger les garanties.'));
        this.loading.set(false);
      },
    });
  }

  private toRows(items: EntrepriseGuarantee[]): EditRow[] {
    return [...items]
      .sort((a, b) => a.order - b.order)
      .map((g) => ({
        key: this.nextKey++,
        kind: g.kind,
        level: g.level,
        name: g.name ?? '',
        amount: g.indemnityAmount ?? null,
        label: g.indemnityLabel ?? '',
        deductible: g.deductible ?? '',
        basis: g.basis ?? '',
        active: g.active,
      }));
  }

  // ------------------------------------------------------------------ édition

  private blank(kind: GuaranteeKind, level = 0): EditRow {
    return { key: this.nextKey++, kind, level, name: '', amount: null, label: '', deductible: '', basis: '', active: true };
  }

  add(kind: GuaranteeKind): void {
    if (this.rows().length >= MAX_ROWS) return;
    this.rows.update((rows) => [...rows, this.blank(kind)]);
  }

  /** Ajoute une ligne « dont » juste après la ligne et ses éventuelles lignes « dont ». */
  addDont(index: number): void {
    if (this.rows().length >= MAX_ROWS) return;
    this.rows.update((rows) => {
      let at = index + 1;
      while (at < rows.length && rows[at].level === 1) at++;
      const next = [...rows];
      next.splice(at, 0, this.blank(rows[index].kind === 'LIMIT' ? 'LIMIT' : 'GUARANTEE', 1));
      return next;
    });
  }

  remove(index: number): void {
    this.rows.update((rows) => {
      const parent = rows[index];
      let end = index + 1;
      // Retirer une ligne de niveau 0 emporte ses lignes « dont » : elles n'auraient plus de parent.
      if (parent.level === 0) while (end < rows.length && rows[end].level === 1) end++;
      return [...rows.slice(0, index), ...rows.slice(end)];
    });
  }

  patch(index: number, change: Partial<EditRow>): void {
    this.rows.update((rows) => rows.map((r, i) => (i === index ? { ...r, ...change } : r)));
  }

  setAmount(index: number, value: string): void {
    const digits = value.replace(/\D/g, '');
    this.patch(index, { amount: digits === '' ? null : Number(digits) });
  }

  setKind(index: number, kind: GuaranteeKind): void {
    // Changer de type vide les champs qui n'ont plus de sens.
    if (kind === 'NOTE') this.patch(index, { kind, name: '', amount: null, deductible: '', basis: '', level: 0 });
    else if (kind === 'LIMIT') this.patch(index, { kind, label: '' });
    else this.patch(index, { kind });
  }

  /** Une ligne de niveau 0 se déplace avec ses lignes « dont » ; une ligne « dont » reste dans son groupe. */
  move(index: number, direction: -1 | 1): void {
    this.rows.update((rows) => {
      const row = rows[index];
      if (row.level === 1) {
        const target = index + direction;
        if (target < 0 || target >= rows.length || rows[target].level !== 1) return rows;
        const next = [...rows];
        [next[index], next[target]] = [next[target], next[index]];
        return next;
      }
      const groups: EditRow[][] = [];
      for (const r of rows) {
        if (r.level === 0 || groups.length === 0) groups.push([r]);
        else groups[groups.length - 1].push(r);
      }
      const gi = groups.findIndex((g) => g[0].key === row.key);
      const target = gi + direction;
      if (gi < 0 || target < 0 || target >= groups.length) return rows;
      [groups[gi], groups[target]] = [groups[target], groups[gi]];
      return groups.flat();
    });
  }

  canMoveUp(index: number): boolean {
    const rows = this.rows();
    if (rows[index].level === 1) return index > 0 && rows[index - 1].level === 1;
    return rows.findIndex((r) => r.level === 0) < index;
  }

  canMoveDown(index: number): boolean {
    const rows = this.rows();
    if (rows[index].level === 1) return index + 1 < rows.length && rows[index + 1].level === 1;
    let end = index + 1;
    while (end < rows.length && rows[end].level === 1) end++;
    return end < rows.length;
  }

  // ------------------------------------------------------------------ contrôles

  /**
   * Une garantie sans montant ni texte est un intitulé de groupe : acceptée seulement si la ligne suivante est une
   * garantie de niveau 1 (« dont »), comme le contrôle la Gateway.
   */
  private isHeadingAt(rows: EditRow[], index: number): boolean {
    const r = rows[index];
    const next = rows[index + 1];
    return (
      r.kind === 'GUARANTEE' &&
      r.level === 0 &&
      !!r.name.trim() &&
      (r.amount === null || r.amount < 1) &&
      !r.label.trim() &&
      !!next &&
      next.kind === 'GUARANTEE' &&
      next.level === 1
    );
  }

  /** Pour le gabarit : la ligne est-elle un intitulé de groupe ? */
  isHeading(index: number): boolean {
    return this.isHeadingAt(this.rows(), index);
  }

  private validate(rows: EditRow[]): Map<number, string> {
    const errors = new Map<number, string>();
    let hasParent = false;
    rows.forEach((r, index) => {
      const name = r.name.trim();
      if (r.level === 0) hasParent = true;
      if (r.level === 1 && !hasParent) {
        errors.set(r.key, 'Une ligne « dont » doit suivre une ligne de niveau 0.');
        return;
      }
      if (r.kind === 'GUARANTEE') {
        if (!name) {
          errors.set(r.key, 'Une garantie doit avoir un nom.');
          return;
        }
        const noIndemnity = (r.amount === null || r.amount < 1) && !r.label.trim();
        if (noIndemnity && !this.isHeadingAt(rows, index)) {
          errors.set(
            r.key,
            'Renseignez un montant ou un texte (ex. Non garanti), ou faites de cette ligne un intitulé de groupe suivi d’au moins une ligne « dont ».',
          );
          return;
        }
      } else if (r.kind === 'LIMIT') {
        if (!name || r.amount === null || r.amount < 1) {
          errors.set(r.key, 'Un plafond doit avoir un nom et un montant.');
          return;
        }
      } else if (!r.label.trim()) {
        errors.set(r.key, 'Une note doit contenir un texte.');
        return;
      }
      if (name.length > 250 || r.label.length > 250) {
        errors.set(r.key, 'Le nom et le texte font 250 caractères au plus.');
      } else if (r.deductible.length > 100 || r.basis.length > 100) {
        errors.set(r.key, 'La franchise et la base font 100 caractères au plus.');
      }
    });
    if (rows.length > MAX_ROWS) {
      errors.set(rows[MAX_ROWS].key, `${MAX_ROWS} lignes au maximum.`);
    }
    return errors;
  }

  private fingerprint(rows: EditRow[]): string {
    return JSON.stringify(rows.map((r) => [r.kind, r.level, r.name.trim(), r.amount, r.label.trim(), r.deductible.trim(), r.basis.trim(), r.active]));
  }

  // ------------------------------------------------------------------ enregistrement

  save(): void {
    if (!this.canSave()) return;
    this.saving.set(true);
    this.error.set(null);
    const items: EntrepriseGuaranteeInput[] = this.rows().map((r) => ({
      kind: r.kind,
      level: r.level,
      name: r.kind === 'NOTE' ? null : r.name.trim(),
      indemnityAmount: r.kind === 'NOTE' ? null : r.amount,
      indemnityLabel: r.kind === 'LIMIT' ? null : r.label.trim() || null,
      deductible: r.kind === 'NOTE' ? null : r.deductible.trim() || null,
      basis: r.kind === 'NOTE' ? null : r.basis.trim() || null,
      active: r.active,
    }));
    this.service.save(this.formulaId(), this.option(), items).subscribe({
      next: (list) => {
        this.saving.set(false);
        const rows = this.toRows(list.items);
        this.rows.set(rows);
        this.snapshot.set(this.fingerprint(rows));
        this.saved.emit({ count: list.items.length });
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(this.messageOf(err, "Impossible d'enregistrer les garanties."));
      },
    });
  }

  // ------------------------------------------------------------------ fermeture et copie

  requestClose(): void {
    if (this.dirty() && !this.saving()) {
      this.confirmClose.set(true);
      return;
    }
    this.closed.emit();
  }

  discardAndClose(): void {
    this.confirmClose.set(false);
    this.closed.emit();
  }

  openCopy(): void {
    this.copyError.set(null);
    this.copySource.set(this.sources()[0] ? this.sourceKey(this.sources()[0]) : '');
    this.copyVisible.set(true);
  }

  sourceKey(s: GuaranteeSource): string {
    return `${s.formulaId}|${s.option}`;
  }

  /** Remplace les lignes de l'éditeur par celles de la source (rien n'est enregistré). */
  copyFrom(): void {
    const key = this.copySource();
    const source = this.sources().find((s) => this.sourceKey(s) === key);
    if (!source) {
      this.copyError.set('Choisissez une source.');
      return;
    }
    this.copyBusy.set(true);
    this.copyError.set(null);
    this.service.get(source.formulaId, source.option).subscribe({
      next: (list) => {
        this.rows.set(this.toRows(list.items));
        this.copyBusy.set(false);
        this.copyVisible.set(false);
      },
      error: (err) => {
        this.copyBusy.set(false);
        this.copyError.set(this.messageOf(err, 'Impossible de lire les garanties de la source.'));
      },
    });
  }

  formatAmount(value: number | null): string {
    return value === null ? '' : value.toLocaleString('fr-FR');
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      if (err.status === 404) return 'Cette formule ou cette option n’existe pas dans la grille.';
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return fallback;
  }
}
