import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SpinnerComponent } from '@coreui/angular';

import { MobileFaqService } from '../../core/mobile-admin/mobile-faq.service';
import { MobileFeedback } from '../../core/mobile-admin/models/mobile-faq.model';

const STARS = [5, 4, 3, 2, 1];

@Component({
  selector: 'app-avis-mobile',
  templateUrl: './avis-mobile.component.html',
  styleUrl: './avis-mobile.component.scss',
  imports: [SpinnerComponent, RouterLink],
})
export class AvisMobileComponent {
  private readonly service = inject(MobileFaqService);

  readonly pageSize = 20;
  readonly stars = STARS;

  readonly feedback = signal<MobileFeedback[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly ratingFilter = signal<number | null>(null);
  readonly onlyWithComment = signal(false);
  readonly pageIndex = signal(0);

  readonly average = computed(() => {
    const list = this.feedback();
    return list.length ? list.reduce((sum, f) => sum + f.rating, 0) / list.length : 0;
  });

  /** Répartition des notes (5 → 1) avec le pourcentage de chaque note, pour les barres. */
  readonly distribution = computed(() => {
    const list = this.feedback();
    return STARS.map((star) => {
      const count = list.filter((f) => f.rating === star).length;
      return { star, count, percent: list.length ? Math.round((count / list.length) * 100) : 0 };
    });
  });

  readonly filtered = computed(() => {
    const rating = this.ratingFilter();
    const onlyComment = this.onlyWithComment();
    return this.feedback().filter((f) => (rating === null || f.rating === rating) && (!onlyComment || !!f.comment?.trim()));
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filtered().length / this.pageSize)));
  readonly pageItems = computed(() => {
    const start = this.pageIndex() * this.pageSize;
    return this.filtered().slice(start, start + this.pageSize);
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.listFeedback().subscribe({
      next: (list) => {
        // Les plus récents d'abord.
        this.feedback.set([...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
        this.pageIndex.set(0);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger les avis.'));
        this.loading.set(false);
      },
    });
  }

  onRatingChange(value: string): void {
    this.ratingFilter.set(value ? Number(value) : null);
    this.pageIndex.set(0);
  }

  onCommentOnlyChange(checked: boolean): void {
    this.onlyWithComment.set(checked);
    this.pageIndex.set(0);
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
  }

  /** Étoiles pleines puis vides, ex. 4 → ★★★★☆ */
  starsText(rating: number): string {
    const r = Math.max(0, Math.min(5, Math.round(rating)));
    return '★'.repeat(r) + '☆'.repeat(5 - r);
  }

  ratingVariant(rating: number): string {
    if (rating >= 4) return 'success';
    if (rating === 3) return 'warning';
    return 'danger';
  }

  formatDateTime(value?: string | null): string {
    if (!value) return '—';
    const date = new Date(value);
    return `${date.toLocaleDateString('fr-FR')} ${date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return fallback;
  }
}
