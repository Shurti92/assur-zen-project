import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SpinnerComponent } from '@coreui/angular';

import { MobileFaqService } from '../../core/mobile-admin/mobile-faq.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

interface FaqDraft {
  question: string;
  answer: string;
  visible: boolean;
  /** Carte dépliée (affichage seulement, jamais envoyé). */
  open: boolean;
}

// Limites du schéma FaqInput du gateway.
const MAX_QUESTION = 300;
const MAX_ANSWER = 2000;

@Component({
  selector: 'app-faq-mobile',
  templateUrl: './faq-mobile.component.html',
  styleUrl: './faq-mobile.component.scss',
  imports: [SpinnerComponent, FormsModule, CdkDropList, CdkDrag, CdkDragHandle, AlertMessageComponent],
})
export class FaqMobileComponent {
  private readonly service = inject(MobileFaqService);

  readonly maxQuestion = MAX_QUESTION;
  readonly maxAnswer = MAX_ANSWER;

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  readonly saved = signal(false);
  readonly dirty = signal(false);

  /** Brouillon édité par ngModel (objets simples) ; remplacé en bloc à l'enregistrement. */
  items: FaqDraft[] = [];

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.listFaq().subscribe({
      next: (items) => {
        // L'ordre d'affichage est celui de la liste renvoyée par le gateway (champ `order`).
        const sorted = [...items].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
        this.items = sorted.map((i) => ({ question: i.question, answer: i.answer, visible: i.visible ?? true, open: false }));
        this.dirty.set(false);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger la FAQ.'));
        this.loading.set(false);
      },
    });
  }

  markDirty(): void {
    this.dirty.set(true);
    this.saved.set(false);
  }

  add(): void {
    for (const i of this.items) i.open = false;
    this.items.push({ question: '', answer: '', visible: true, open: true });
    this.markDirty();
  }

  remove(index: number): void {
    this.items.splice(index, 1);
    this.markDirty();
  }

  move(index: number, delta: -1 | 1): void {
    const target = index + delta;
    if (target < 0 || target >= this.items.length) return;
    moveItemInArray(this.items, index, target);
    this.markDirty();
  }

  drop(event: CdkDragDrop<FaqDraft[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    moveItemInArray(this.items, event.previousIndex, event.currentIndex);
    this.markDirty();
  }

  /** Abandonne les modifications non enregistrées en rechargeant la FAQ du gateway. */
  discard(): void {
    this.saveError.set(null);
    this.load();
  }

  save(): void {
    if (this.items.some((i) => !i.question.trim() || !i.answer.trim())) {
      this.saveError.set('Chaque entrée doit avoir une question et une réponse (supprimez les entrées vides).');
      return;
    }
    this.saving.set(true);
    this.saveError.set(null);
    this.saved.set(false);
    // Le PUT remplace toute la FAQ : l'ordre du tableau fixe l'ordre d'affichage dans l'application.
    this.service
      .replaceFaq({ items: this.items.map((i) => ({ question: i.question.trim(), answer: i.answer.trim(), visible: i.visible })) })
      .subscribe({
        next: (items) => {
          const sorted = [...items].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
          this.items = sorted.map((i) => ({ question: i.question, answer: i.answer, visible: i.visible ?? true, open: false }));
          this.saving.set(false);
          this.dirty.set(false);
          this.saved.set(true);
        },
        error: (err) => {
          this.saving.set(false);
          this.saveError.set(this.messageOf(err, "Impossible d'enregistrer la FAQ."));
        },
      });
  }

  get visibleCount(): number {
    return this.items.filter((i) => i.visible).length;
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      const message = typeof err.error?.message === 'string' ? err.error.message : '';
      if (message) return err.error?.field ? `${message} (champ : ${err.error.field})` : message;
    }
    return fallback;
  }
}
