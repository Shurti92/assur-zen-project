import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
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
import { AuthService } from '../../../core/auth/auth.service';

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
    DecimalPipe,
  ],
  styleUrl: './exercice-comptable.component.scss',
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

  // ---- Objectifs de production mensuels (saisie réservée à l'ADMIN) ----
  private readonly auth = inject(AuthService);
  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  /** Montants saisis, par numéro de mois (null = pas d'objectif). */
  readonly objectifsDraft = signal<Record<number, number | null>>({});
  readonly objectifsSaving = signal(false);
  readonly objectifsError = signal<string | null>(null);
  readonly objectifsInfo = signal<string | null>(null);
  /** Aperçu de la répartition en parts égales (null = fenêtre fermée). */
  readonly repartirPreview = signal<{ total: number; base: number; dernier: number } | null>(null);
  /** Confirmation demandée pour la modification de mois clos. */
  readonly clotureConfirm = signal(false);
  readonly copyBusy = signal(false);

  /** Sauvegarde de l'état serveur pour détecter les modifications. */
  private objectifsSaved(): Record<number, number | null> {
    const ex = this.moisModalExercice();
    return Object.fromEntries((ex?.mois ?? []).map((m) => [m.numero, m.objectifMensuel ?? null]));
  }

  readonly objectifsDirtyNumeros = computed(() => {
    const saved = this.objectifsSaved();
    const draft = this.objectifsDraft();
    return (this.moisModalExercice()?.mois ?? [])
      .map((m) => m.numero)
      .filter((n) => (draft[n] ?? null) !== (saved[n] ?? null));
  });

  /** Les Gateways antérieures ne renvoient pas objectifMensuel : on masque alors la grille. */
  readonly objectifsSupported = computed(() => (this.moisModalExercice()?.mois ?? []).some((m) => 'objectifMensuel' in m));

  readonly objectifsTotal = computed(() => {
    const draft = this.objectifsDraft();
    return (this.moisModalExercice()?.mois ?? []).reduce((sum, m) => sum + (draft[m.numero] ?? 0), 0);
  });
  readonly objectifsEcart = computed(() => this.objectifsTotal() - (this.moisModalExercice()?.objectifGlobal ?? 0));
  readonly objectifsEcartPct = computed(() => {
    const annuel = this.moisModalExercice()?.objectifGlobal ?? 0;
    return annuel > 0 ? (this.objectifsEcart() / annuel) * 100 : null;
  });
  /** Pas d'enregistrement tant qu'un montant est invalide ou que rien n'a changé. */
  readonly objectifsCanSave = computed(
    () => this.isAdmin() && !this.moisModalExercice()?.cloture && this.objectifsDirtyNumeros().length > 0 && !this.objectifsSaving(),
  );
  readonly hasClosedChanged = computed(() => {
    const dirty = new Set(this.objectifsDirtyNumeros());
    return (this.moisModalExercice()?.mois ?? []).some((m) => m.cloture && dirty.has(m.numero));
  });

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
    effect(() => {
      const ex = this.moisModalExercice();
      untracked(() => {
        this.objectifsDraft.set(Object.fromEntries((ex?.mois ?? []).map((m) => [m.numero, m.objectifMensuel ?? null])));
      });
    });
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
    this.objectifsError.set(null);
    this.objectifsInfo.set(null);
    this.repartirPreview.set(null);
    this.clotureConfirm.set(false);
  }

  // ---------------- Objectifs mensuels ----------------

  /** Saisie d'un montant : vide = pas d'objectif, sinon entier positif. */
  onObjectifInput(numero: number, value: string): void {
    this.objectifsInfo.set(null);
    const digits = value.replace(/\D/g, '');
    this.objectifsDraft.update((d) => ({ ...d, [numero]: digits === '' ? null : Number(digits) }));
  }

  formatInput(value: number | null | undefined): string {
    return value === null || value === undefined ? '' : value.toLocaleString('fr-FR');
  }

  /** Part de l'objectif annuel pour un mois, en % (null si pas d'objectif annuel). */
  partAnnee(numero: number): number | null {
    const annuel = this.moisModalExercice()?.objectifGlobal ?? 0;
    const v = this.objectifsDraft()[numero];
    return annuel > 0 && v ? (v / annuel) * 100 : null;
  }

  /** Statut d'un mois par rapport à aujourd'hui : clos, en cours, à venir. */
  moisEtat(mois: MoisComptableResponse): 'clos' | 'cours' | 'avenir' {
    if (mois.cloture) return 'clos';
    const now = new Date().toISOString().slice(0, 10);
    if (mois.dateDebut <= now && now <= mois.dateFin) return 'cours';
    return mois.dateFin < now ? 'clos' : 'avenir';
  }

  openRepartir(): void {
    const ex = this.moisModalExercice();
    if (!ex || ex.objectifGlobal <= 0 || ex.mois.length === 0) return;
    const n = ex.mois.length;
    const base = Math.floor(ex.objectifGlobal / n);
    const dernier = ex.objectifGlobal - base * (n - 1);
    this.repartirPreview.set({ total: ex.objectifGlobal, base, dernier });
  }

  applyRepartir(): void {
    const ex = this.moisModalExercice();
    const p = this.repartirPreview();
    if (!ex || !p) return;
    const sorted = [...ex.mois].sort((a, b) => a.numero - b.numero);
    this.objectifsDraft.set(Object.fromEntries(sorted.map((m, i) => [m.numero, i === sorted.length - 1 ? p.dernier : p.base])));
    this.repartirPreview.set(null);
    this.objectifsInfo.set(null);
  }

  /** Copie les montants de l'exercice précédent (par numéro de mois), sans enregistrer. */
  canCopyPrevious(): boolean {
    return this.previousExercice()?.mois.some((m) => (m.objectifMensuel ?? 0) > 0) ?? false;
  }

  previousExercice(): ExerciceComptableResponse | null {
    const ex = this.moisModalExercice();
    if (!ex) return null;
    const before = this.items()
      .filter((i) => i.id !== ex.id && i.dateFin < ex.dateDebut)
      .sort((a, b) => b.dateFin.localeCompare(a.dateFin));
    return before[0] ?? null;
  }

  copyPrevious(): void {
    const prev = this.previousExercice();
    if (!prev) return;
    const byNum = Object.fromEntries(prev.mois.map((m) => [m.numero, m.objectifMensuel ?? null]));
    this.objectifsDraft.update((d) => {
      const next = { ...d };
      for (const m of this.moisModalExercice()?.mois ?? []) if (byNum[m.numero] != null) next[m.numero] = byNum[m.numero];
      return next;
    });
    this.objectifsInfo.set(null);
  }

  resetObjectifs(): void {
    const ex = this.moisModalExercice();
    this.objectifsDraft.set(Object.fromEntries((ex?.mois ?? []).map((m) => [m.numero, m.objectifMensuel ?? null])));
    this.objectifsError.set(null);
  }

  requestSaveObjectifs(): void {
    if (!this.objectifsCanSave()) return;
    // Modifier un mois clos change le taux d'atteinte historique : confirmation demandée.
    if (this.hasClosedChanged()) {
      this.clotureConfirm.set(true);
      return;
    }
    this.saveObjectifs();
  }

  saveObjectifs(): void {
    const ex = this.moisModalExercice();
    if (!ex || !this.objectifsCanSave()) return;
    this.clotureConfirm.set(false);
    this.objectifsSaving.set(true);
    this.objectifsError.set(null);
    const draft = this.objectifsDraft();
    const items = this.objectifsDirtyNumeros().map((numero) => ({ numero, montant: draft[numero] ?? null }));
    this.exerciceService.setObjectifsMensuels(ex.id, items).subscribe({
      next: (updated) => {
        this.objectifsSaving.set(false);
        this.items.update((list) => list.map((i) => (i.id === updated.id ? updated : i)));
        this.objectifsInfo.set('Objectifs mensuels enregistrés.');
      },
      error: (err) => {
        this.objectifsSaving.set(false);
        this.objectifsError.set(this.objectifsErrorMessage(err));
      },
    });
  }

  private objectifsErrorMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const code = err.error?.code as string | undefined;
      if (code === 'EXERCICE_CLOTURE') return 'Cet exercice est clôturé : les objectifs ne sont plus modifiables.';
      if (code === 'MOIS_NOT_FOUND') return 'Un mois est introuvable pour cet exercice.';
      if (err.status === 403) return 'Accès réservé aux administrateurs.';
      if (err.status === 404) return 'Exercice introuvable.';
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return "Impossible d'enregistrer les objectifs mensuels.";
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
