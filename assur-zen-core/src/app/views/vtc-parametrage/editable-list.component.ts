import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, computed, input, model, signal } from '@angular/core';

export const LIST_ITEM_MAX_LENGTH = 60;

/** Motif d'invalidité d'un nom (1 à 60 caractères, ni virgule, ni point-virgule, ni retour à la ligne). */
export function listItemProblem(name: string): string | null {
  if (!name.trim()) return 'Ne peut pas être vide.';
  if (name.trim().length > LIST_ITEM_MAX_LENGTH) return `${LIST_ITEM_MAX_LENGTH} caractères maximum.`;
  if (/[,;\r\n]/.test(name)) return 'Ni virgule, ni point-virgule, ni retour à la ligne.';
  return null;
}

/** Vrai si la liste est enregistrable : noms valides, sans doublon (casse ignorée), dans la limite. */
export function isListValid(items: string[], max: number): boolean {
  const seen = new Set<string>();
  for (const i of items) {
    const key = i.trim().toLowerCase();
    if (listItemProblem(i) || seen.has(key)) return false;
    seen.add(key);
  }
  return items.length <= max;
}

/** Liste de textes réordonnable (glisser-déposer), avec ajout, renommage et suppression. */
@Component({
  selector: 'app-editable-list',
  templateUrl: './editable-list.component.html',
  styleUrl: './editable-list.component.scss',
  imports: [CdkDrag, CdkDragHandle, CdkDropList],
})
export class EditableListComponent {
  readonly items = model<string[]>([]);
  readonly title = input.required<string>();
  readonly placeholder = input('Ajouter');
  readonly max = input(200);
  readonly emptyHint = input('Liste vide = saisie libre.');

  readonly maxLength = LIST_ITEM_MAX_LENGTH;
  readonly draft = signal('');
  readonly draftError = signal<string | null>(null);

  /** Indices en doublon d'un nom placé plus haut. */
  readonly duplicates = computed(() => {
    const seen = new Set<string>();
    const dup = new Set<number>();
    this.items().forEach((n, i) => {
      const key = n.trim().toLowerCase();
      if (seen.has(key)) dup.add(i);
      seen.add(key);
    });
    return dup;
  });

  problem(index: number): string | null {
    if (this.duplicates().has(index)) return 'Déjà dans la liste.';
    return listItemProblem(this.items()[index]);
  }

  onDraft(value: string): void {
    this.draft.set(value);
    this.draftError.set(null);
  }

  add(): void {
    const name = this.draft().trim();
    const error =
      listItemProblem(name) ??
      (this.items().some((n) => n.trim().toLowerCase() === name.toLowerCase())
        ? 'Déjà dans la liste.'
        : this.items().length >= this.max()
          ? `${this.max()} éléments maximum.`
          : null);
    this.draftError.set(error);
    if (error) return;
    this.items.update((l) => [...l, name]);
    this.draft.set('');
  }

  rename(index: number, value: string): void {
    this.items.update((l) => l.map((n, i) => (i === index ? value : n)));
  }

  remove(index: number): void {
    this.items.update((l) => l.filter((_, i) => i !== index));
  }

  move(index: number, delta: -1 | 1): void {
    const target = index + delta;
    if (target < 0 || target >= this.items().length) return;
    this.items.update((l) => {
      const copy = [...l];
      moveItemInArray(copy, index, target);
      return copy;
    });
  }

  drop(event: CdkDragDrop<string[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    this.items.update((l) => {
      const copy = [...l];
      moveItemInArray(copy, event.previousIndex, event.currentIndex);
      return copy;
    });
  }
}
