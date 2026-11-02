import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { InsuredService } from '../../core/insured/insured.service';
import { InsuredFicheResponse, InsuredResponse, TypeClient } from '../../core/insured/models/insured.model';
import { ContractService } from '../../core/contracts/contract.service';
import { ContractResponse } from '../../core/contracts/models/contract.model';
import { VillePartenaireService } from '../../core/configuration/ville-partenaire.service';
import { VillePartenaireResponse } from '../../core/configuration/models/ville-partenaire.model';
import { ExerciceComptableService } from '../../core/configuration/exercice-comptable.service';
import { ExerciceComptableResponse } from '../../core/configuration/models/exercice-comptable.model';
import { SINISTRE_STATUS_LABEL, SINISTRE_STATUS_VARIANT } from '../../core/sinistres/sinistre-status';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { EntityDocumentsComponent } from '../../shared/entity-documents/entity-documents.component';

const TYPE_CLIENT_LABEL: Record<TypeClient, string> = {
  PARTICULIER: 'Particulier',
  ENTREPRISE: 'Entreprise',
};

interface FilterChip {
  key: 'ville' | 'typeClient';
  label: string;
}

@Component({
  selector: 'app-assures',
  templateUrl: './assures.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    FormSelectDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    AlertMessageComponent,
    ModalComponent,
    EntityDocumentsComponent,
  ],
})
export class AssuresComponent {
  private readonly insuredService = inject(InsuredService);
  private readonly contractService = inject(ContractService);
  private readonly villePartenaireService = inject(VillePartenaireService);
  private readonly exerciceService = inject(ExerciceComptableService);
  private readonly fb = inject(FormBuilder);

  readonly sinistreStatusLabel = SINISTRE_STATUS_LABEL;
  readonly sinistreStatusVariant = SINISTRE_STATUS_VARIANT;

  readonly pageSize = 20;

  readonly items = signal<InsuredResponse[]>([]);
  readonly pageIndex = signal(0);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');

  readonly villes = signal<VillePartenaireResponse[]>([]);
  readonly villeFilter = signal('');
  readonly typeClientFilter = signal<TypeClient | ''>('');
  readonly advancedFiltersOpen = signal(false);

  readonly activeFilterChips = computed<FilterChip[]>(() => {
    const chips: FilterChip[] = [];
    if (this.villeFilter()) chips.push({ key: 'ville', label: `Ville : ${this.villeFilter()}` });
    if (this.typeClientFilter()) {
      chips.push({ key: 'typeClient', label: `Type : ${this.typeClientLabel(this.typeClientFilter() as TypeClient)}` });
    }
    return chips;
  });

  readonly detailVisible = signal(false);
  readonly selectedInsured = signal<InsuredResponse | null>(null);
  readonly insuredContracts = signal<ContractResponse[]>([]);
  readonly contractsLoading = signal(true);
  readonly contractsError = signal<string | null>(null);

  readonly contractsPremiumTotal = computed(() => this.insuredContracts().reduce((sum, c) => sum + c.premium, 0));
  readonly contractsCommissionTotal = computed(() =>
    this.insuredContracts().reduce((sum, c) => sum + c.commissionAmount, 0),
  );

  // ================= Fiche de sinistralité =================

  readonly exercicesComptables = signal<ExerciceComptableResponse[]>([]);
  readonly ficheExerciceFilter = signal<number | ''>('');
  readonly fiche = signal<InsuredFicheResponse | null>(null);
  readonly ficheLoading = signal(false);
  readonly ficheError = signal<string | null>(null);

  readonly exporting = signal(false);
  readonly exportError = signal<string | null>(null);

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({
    nom: [''],
    prenoms: [''],
    clientPhone: [''],
    clientPhone2: [''],
    clientCity: [''],
    clientEmail: [''],
    typeClient: this.fb.nonNullable.control<TypeClient>('PARTICULIER'),
  });

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.searchDebounce) clearTimeout(this.searchDebounce);
    });
    this.villePartenaireService.list().subscribe({ next: (list) => this.villes.set(list) });
    this.exerciceService.list().subscribe({
      next: (list) => this.exercicesComptables.set(list),
      error: () => {
        // Silent: le sélecteur d'exercice comptable reste vide si ça échoue.
      },
    });
    this.fetch();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    const q = this.searchQuery() || undefined;
    const typeClient = this.typeClientFilter() || undefined;
    const ville = this.villeFilter();

    if (ville) {
      // Le backend ne filtre pas par ville — on charge un lot large et on
      // pagine côté client sur le sous-ensemble filtré.
      this.insuredService.list(0, 1000, q, typeClient).subscribe({
        next: (result) => {
          const filtered = result.content.filter(
            (i) => (i.clientCity ?? '').trim().toLowerCase() === ville.trim().toLowerCase(),
          );
          this.totalElements.set(filtered.length);
          this.totalPages.set(Math.max(1, Math.ceil(filtered.length / this.pageSize)));
          const start = this.pageIndex() * this.pageSize;
          this.items.set(filtered.slice(start, start + this.pageSize));
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Impossible de charger les assurés.');
          this.loading.set(false);
        },
      });
      return;
    }

    this.insuredService.list(this.pageIndex(), this.pageSize, q, typeClient).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les assurés.');
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

  onVilleFilterChange(value: string): void {
    this.villeFilter.set(value);
    this.pageIndex.set(0);
    this.fetch();
  }

  onTypeClientFilterChange(value: string): void {
    this.typeClientFilter.set((value as TypeClient) || '');
    this.pageIndex.set(0);
    this.fetch();
  }

  removeFilterChip(key: FilterChip['key']): void {
    if (key === 'ville') this.villeFilter.set('');
    if (key === 'typeClient') this.typeClientFilter.set('');
    this.pageIndex.set(0);
    this.fetch();
  }

  resetFilters(): void {
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchQuery.set('');
    this.villeFilter.set('');
    this.typeClientFilter.set('');
    this.pageIndex.set(0);
    this.fetch();
  }

  toggleAdvancedFilters(): void {
    this.advancedFiltersOpen.update((open) => !open);
  }

  openDetail(insured: InsuredResponse): void {
    this.selectedInsured.set(insured);
    this.detailVisible.set(true);
    this.ficheExerciceFilter.set('');
    this.fiche.set(null);
    this.fetchContracts(insured.id);
    this.fetchFiche(insured.id);
  }

  openCreate(): void {
    this.editingId.set(null);
    this.formError.set(null);
    this.form.reset({
      nom: '',
      prenoms: '',
      clientPhone: '',
      clientPhone2: '',
      clientCity: '',
      clientEmail: '',
      typeClient: 'PARTICULIER',
    });
    this.formVisible.set(true);
  }

  openEdit(insured: InsuredResponse): void {
    this.editingId.set(insured.id);
    this.formError.set(null);
    this.form.reset({
      nom: insured.nom,
      prenoms: insured.prenoms,
      clientPhone: insured.clientPhone ?? '',
      clientPhone2: insured.clientPhone2 ?? '',
      clientCity: insured.clientCity ?? '',
      clientEmail: insured.clientEmail ?? '',
      typeClient: insured.typeClient ?? 'PARTICULIER',
    });
    this.formVisible.set(true);
  }

  save(): void {
    const raw = this.form.getRawValue();
    const id = this.editingId();
    if (!raw.nom.trim() || !raw.prenoms.trim() || !raw.clientPhone.trim() || !raw.clientCity.trim()) {
      this.formError.set('Nom, prénoms, téléphone et ville sont obligatoires.');
      return;
    }
    if (raw.clientEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(raw.clientEmail.trim())) {
      this.formError.set("L'adresse email n'est pas valide.");
      return;
    }

    this.formSaving.set(true);
    this.formError.set(null);
    const payload = {
      nom: raw.nom.trim(),
      prenoms: raw.prenoms.trim(),
      clientPhone: raw.clientPhone.trim(),
      clientPhone2: raw.clientPhone2.trim() || undefined,
      clientCity: raw.clientCity.trim(),
      clientEmail: raw.clientEmail.trim() || undefined,
      typeClient: raw.typeClient,
    };
    const request = id ? this.insuredService.update(id, payload) : this.insuredService.create(payload);
    request.subscribe({
      next: (saved) => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        // Le détail peut être ouvert sur cette même fiche — le refléter sans
        // attendre un nouveau fetch().
        if (id && this.selectedInsured()?.id === id) this.selectedInsured.set(saved);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set(id ? "Impossible d'enregistrer les modifications. Réessayez." : "Impossible de créer cet assuré. Réessayez.");
      },
    });
  }

  private fetchContracts(insuredId: number): void {
    this.contractsLoading.set(true);
    this.contractsError.set(null);
    this.contractService.list(0, 100, { insuredId }).subscribe({
      next: (page) => {
        this.insuredContracts.set(page.content);
        this.contractsLoading.set(false);
      },
      error: () => {
        this.contractsError.set('Impossible de charger les contrats de cet assuré.');
        this.contractsLoading.set(false);
      },
    });
  }

  fetchFiche(insuredId: number): void {
    this.ficheLoading.set(true);
    this.ficheError.set(null);
    const exerciceId = this.ficheExerciceFilter() || undefined;
    this.insuredService.getFicheSinistralite(insuredId, exerciceId).subscribe({
      next: (fiche) => {
        this.fiche.set(fiche);
        this.ficheLoading.set(false);
      },
      error: () => {
        this.ficheError.set('Impossible de charger la fiche de sinistralité.');
        this.ficheLoading.set(false);
      },
    });
  }

  onFicheExerciceChange(value: string): void {
    this.ficheExerciceFilter.set(value ? Number(value) : '');
    const insured = this.selectedInsured();
    if (insured) this.fetchFiche(insured.id);
  }

  // ================= Export CSV de la liste =================

  exportList(): void {
    this.exporting.set(true);
    this.exportError.set(null);
    this.insuredService.export().subscribe({
      next: (blob) => {
        this.downloadBlob(blob, `assurzen-assures-${new Date().toISOString().replace(/[:.]/g, '-')}.csv`);
        this.exporting.set(false);
      },
      error: () => {
        this.exportError.set("Impossible d'exporter les assurés.");
        this.exporting.set(false);
      },
    });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  sinistreMontant(value: number | null): string {
    return value === null ? '—' : this.formatAmount(value);
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
  }

  statusVariant(status: string): string {
    if (status === 'signed') return 'success';
    if (status === 'pending') return 'warning';
    if (status === 'cancelled') return 'danger';
    return 'neutral';
  }

  statusLabel(status: string): string {
    return { signed: 'Signé', pending: 'En attente', draft: 'Brouillon', cancelled: 'Annulé' }[status] ?? status;
  }

  typeClientLabel(value: TypeClient | string | undefined): string {
    if (!value) return 'Particulier';
    return TYPE_CLIENT_LABEL[value as TypeClient] ?? value;
  }

  initials(nom: string, prenoms: string): string {
    return ((nom?.[0] ?? '') + (prenoms?.[0] ?? '')).toUpperCase();
  }
}
