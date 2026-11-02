import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  DropdownComponent,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { ExerciceComptableService } from '../../../core/configuration/exercice-comptable.service';
import {
  ExerciceComptableResponse,
  MoisComptableResponse,
} from '../../../core/configuration/models/exercice-comptable.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';
import { ThousandSeparatorDirective } from '../../../shared/directives/thousand-separator.directive';

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-exercice-comptable',
  templateUrl: './exercice-comptable.component.html',
  imports: [
    ButtonDirective,
    DropdownComponent,
    DropdownItemDirective,
    DropdownMenuDirective,
    DropdownToggleDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormCheckInputDirective,
    AlertMessageComponent,
    ModalComponent,
    ConfirmDialogComponent,
    ThousandSeparatorDirective,
  ],
})
export class ExerciceComptableComponent {
  private readonly exerciceService = inject(ExerciceComptableService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<ExerciceComptableResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly deleteTarget = signal<ExerciceComptableResponse | null>(null);
  readonly deleting = signal(false);

  readonly clotureTarget = signal<ExerciceComptableResponse | null>(null);
  readonly cloturing = signal(false);
  readonly reouvrant = signal<number | null>(null);

  readonly moisModalExerciceId = signal<number | null>(null);
  readonly moisActionPendingId = signal<number | null>(null);

  readonly moisModalExercice = computed(
    () => this.items().find((item) => item.id === this.moisModalExerciceId()) ?? null
  );

  readonly form = this.fb.nonNullable.group({
    libelle: [''],
    dateDebut: [''],
    dateFin: [''],
    active: [true],
    objectifGlobal: [0],
  });

  readonly filteredItems = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const items = this.items();
    if (!query) return items;
    return items.filter((item) => item.libelle.toLowerCase().includes(query));
  });

  /** New exercices can't start/end in the past — editing an existing one keeps
   * whatever historical dates it already has (e.g. closing out a prior period). */
  readonly minDate = computed(() => (this.editingId() ? null : today()));

  constructor() {
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.exerciceService.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les exercices comptables.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({ libelle: '', dateDebut: '', dateFin: '', active: true, objectifGlobal: 0 });
    this.formVisible.set(true);
  }

  openEdit(item: ExerciceComptableResponse): void {
    this.editingId.set(item.id);
    this.formError.set(null);
    this.form.reset({
      libelle: item.libelle,
      dateDebut: item.dateDebut,
      dateFin: item.dateFin,
      active: item.active,
      objectifGlobal: item.objectifGlobal,
    });
    this.formVisible.set(true);
  }

  save(): void {
    const raw = this.form.getRawValue();
    if (!raw.libelle.trim()) {
      this.formError.set('Le libellé est requis.');
      return;
    }
    if (!raw.dateDebut || !raw.dateFin) {
      this.formError.set('Les dates de début et de fin sont requises.');
      return;
    }
    if (raw.dateFin < raw.dateDebut) {
      this.formError.set('La date de fin doit être postérieure à la date de début.');
      return;
    }
    if (!this.editingId() && (raw.dateDebut < today() || raw.dateFin < today())) {
      this.formError.set("Les dates ne peuvent pas être antérieures à aujourd'hui.");
      return;
    }
    this.formSaving.set(true);
    this.formError.set(null);
    const payload = { ...raw, objectifGlobal: Number(raw.objectifGlobal) };
    const id = this.editingId();
    const request = id ? this.exerciceService.update(id, payload) : this.exerciceService.create(payload);
    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer l'exercice comptable. Réessayez.");
      },
    });
  }

  confirmDelete(item: ExerciceComptableResponse): void {
    this.deleteTarget.set(item);
  }

  performDelete(): void {
    const item = this.deleteTarget();
    if (!item) return;
    this.deleting.set(true);
    this.exerciceService.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.deleting.set(false);
        this.error.set("Impossible de supprimer cet exercice comptable.");
        this.deleteTarget.set(null);
      },
    });
  }

  confirmCloturer(item: ExerciceComptableResponse): void {
    this.clotureTarget.set(item);
  }

  performCloturer(): void {
    const item = this.clotureTarget();
    if (!item) return;
    this.cloturing.set(true);
    this.exerciceService.cloturer(item.id).subscribe({
      next: () => {
        this.cloturing.set(false);
        this.clotureTarget.set(null);
        this.fetch();
      },
      error: () => {
        this.cloturing.set(false);
        this.error.set('Impossible de clôturer cet exercice comptable.');
        this.clotureTarget.set(null);
      },
    });
  }

  reouvrir(item: ExerciceComptableResponse): void {
    this.reouvrant.set(item.id);
    this.exerciceService.reouvrir(item.id).subscribe({
      next: () => {
        this.reouvrant.set(null);
        this.fetch();
      },
      error: () => {
        this.reouvrant.set(null);
        this.error.set('Impossible de réouvrir cet exercice comptable.');
      },
    });
  }

  openMoisModal(item: ExerciceComptableResponse): void {
    this.moisModalExerciceId.set(item.id);
  }

  closeMoisModal(): void {
    this.moisModalExerciceId.set(null);
  }

  /** Clôture/réouverture d'un mois : purement structurel/reporting côté backend — ne bloque
   * pas la saisie d'écritures (seule la clôture de l'exercice annuel le fait). */
  toggleMoisCloture(exerciceId: number, mois: MoisComptableResponse): void {
    this.moisActionPendingId.set(mois.id);
    const request = mois.cloture
      ? this.exerciceService.reouvrirMois(exerciceId, mois.id)
      : this.exerciceService.cloturerMois(exerciceId, mois.id);
    request.subscribe({
      next: (updated) => {
        this.moisActionPendingId.set(null);
        this.items.update((list) =>
          list.map((item) =>
            item.id === exerciceId
              ? { ...item, mois: item.mois.map((m) => (m.id === updated.id ? updated : m)) }
              : item
          )
        );
      },
      error: () => {
        this.moisActionPendingId.set(null);
        this.error.set(`Impossible de ${mois.cloture ? 'réouvrir' : 'clôturer'} ce mois.`);
      },
    });
  }

  tauxVariant(taux: number): string {
    if (taux >= 100) return 'success';
    if (taux >= 50) return 'warning';
    return 'danger';
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('fr-FR');
  }
}
