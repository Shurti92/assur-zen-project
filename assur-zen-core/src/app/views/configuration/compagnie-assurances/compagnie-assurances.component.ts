import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { CompagnieService } from '../../../core/configuration/compagnie.service';
import { CompagnieResponse, CompagnieType } from '../../../core/configuration/models/compagnie.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type ActiveFilter = 'all' | 'active' | 'inactive';

@Component({
  selector: 'app-compagnie-assurances',
  templateUrl: './compagnie-assurances.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormCheckInputDirective,
    FormSelectDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class CompagnieAssurancesComponent {
  private readonly compagnieService = inject(CompagnieService);
  private readonly fb = inject(FormBuilder);

  readonly pageSize = 20;

  readonly items = signal<CompagnieResponse[]>([]);
  readonly pageIndex = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly activeFilter = signal<ActiveFilter>('all');

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly deleteTarget = signal<CompagnieResponse | null>(null);
  readonly deleting = signal(false);

  readonly form = this.fb.nonNullable.group({
    codeCompagnie: [''],
    nom: [''],
    active: [true],
    type: this.fb.nonNullable.control<CompagnieType | ''>(''),
  });

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.searchDebounce) clearTimeout(this.searchDebounce);
    });
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    const active = this.activeFilter() === 'all' ? undefined : this.activeFilter() === 'active';
    this.compagnieService.list(this.pageIndex(), this.pageSize, this.searchQuery() || undefined, active).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les compagnies.');
        this.loading.set(false);
      },
    });
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
    this.fetch();
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.pageIndex.set(0);
      this.fetch();
    }, 400);
  }

  setActiveFilter(filter: ActiveFilter): void {
    this.activeFilter.set(filter);
    this.pageIndex.set(0);
    this.fetch();
  }

  openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({ codeCompagnie: '', nom: '', active: true, type: '' });
    this.formVisible.set(true);
  }

  openEdit(item: CompagnieResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.form.reset({ codeCompagnie: item.codeCompagnie, nom: item.nom, active: item.active, type: item.type ?? '' });
    this.formVisible.set(true);
  }

  save(): void {
    const raw = this.form.getRawValue();
    if (!raw.nom.trim()) {
      this.formError.set('Le nom de la compagnie est requis.');
      return;
    }
    const payload = { ...raw, type: raw.type || undefined };
    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request = id ? this.compagnieService.update(id, payload) : this.compagnieService.create(payload);
    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer la compagnie. Réessayez.");
      },
    });
  }

  confirmDelete(item: CompagnieResponse): void {
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleting.set(true);
    this.compagnieService.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.error.set('Impossible de supprimer cette compagnie.');
        this.deleteTarget.set(null);
      },
    });
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }

  typeLabel(type: CompagnieType | null | undefined): string {
    if (type === 'VIE') return 'Vie';
    if (type === 'NON_VIE') return 'Non-vie';
    return '—';
  }
}
