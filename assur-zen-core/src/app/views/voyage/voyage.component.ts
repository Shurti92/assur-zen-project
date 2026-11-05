import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SpinnerComponent } from '@coreui/angular';

import {
  VOYAGE_SPACES,
  VoyageCountry,
  VoyageForm,
  VoyageService,
  VoyageSimulation,
  VoyageSpace,
  VoyageTariff,
} from '../../core/quotes/voyage.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

type Tab = 'countries' | 'tariffs' | 'simulator';
type StatusFilter = 'all' | 'active' | 'inactive';

/** Durées maximales de la grille de départ (jours) ; null = « 1 an » (au-delà de 180 jours). */
const DEFAULT_DURATIONS: (number | null)[] = [7, 10, 15, 21, 30, 45, 60, 91, 180, null];

interface CountryDraft {
  id: number | null;
  name: string;
  space: VoyageSpace;
  active: boolean;
}

interface Cell {
  ageMin: number;
  ageMax: number;
  maxDays: number | null;
  tariff: VoyageTariff | null;
}

interface TariffDraft {
  ageMin: number | null;
  ageMax: number | null;
  maxDays: number | null;
  net: number;
  accessories: number;
  taxes: number;
}

/** Devis > Voyage : pays de destination, grille tarifaire par espace, simulateur de contrôle. Maquette validée le 2026-11-05. */
@Component({
  selector: 'app-voyage',
  templateUrl: './voyage.component.html',
  styleUrl: './voyage.component.scss',
  imports: [AlertMessageComponent, FormsModule, SpinnerComponent],
})
export class VoyageComponent {
  private readonly service = inject(VoyageService);

  readonly spaces = VOYAGE_SPACES;
  readonly tab = signal<Tab>('countries');
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  readonly modalError = signal<string | null>(null);
  readonly saving = signal(false);

  // ---------- Pays ----------
  readonly countries = signal<VoyageCountry[]>([]);
  readonly countriesLoading = signal(true);
  readonly spaceFilter = signal<VoyageSpace | null>(null);
  readonly statusFilter = signal<StatusFilter>('all');
  readonly search = signal('');
  readonly countryEdit = signal<CountryDraft | null>(null);
  readonly countryDelete = signal<VoyageCountry | null>(null);

  readonly spaceCounts = computed(() => {
    const list = this.countries();
    return {
      all: list.length,
      AFRIQUE_OUEST: list.filter((c) => c.space === 'AFRIQUE_OUEST').length,
      ESPACE_SCHENGEN: list.filter((c) => c.space === 'ESPACE_SCHENGEN').length,
      MONDE_ENTIER: list.filter((c) => c.space === 'MONDE_ENTIER').length,
    };
  });
  readonly statusCounts = computed(() => ({
    active: this.countries().filter((c) => c.active).length,
    inactive: this.countries().filter((c) => !c.active).length,
  }));
  readonly shownCountries = computed(() => {
    const s = this.spaceFilter();
    const st = this.statusFilter();
    const q = this.normalize(this.search());
    return this.countries().filter(
      (c) => (!s || c.space === s) && (st === 'all' || (st === 'active') === c.active) && (!q || this.normalize(c.name).includes(q)),
    );
  });

  // ---------- Grille ----------
  readonly tariffSpace = signal<VoyageSpace>('AFRIQUE_OUEST');
  readonly tariffs = signal<VoyageTariff[]>([]);
  readonly tariffsLoading = signal(true);
  readonly selected = signal<Cell | null>(null);
  readonly detail = signal<{ net: number; accessories: number; taxes: number } | null>(null);
  readonly deleteAsk = signal(false);
  readonly tariffNew = signal<TariffDraft | null>(null);

  readonly durations = computed<(number | null)[]>(() => {
    const set = new Set<number | null>(DEFAULT_DURATIONS);
    this.tariffs().forEach((t) => set.add(t.maxDays));
    return [...set].sort((a, b) => (a === null ? 1 : b === null ? -1 : a - b));
  });
  readonly ageBands = computed(() => {
    const seen = new Map<string, { min: number; max: number }>();
    this.tariffs().forEach((t) => seen.set(`${t.ageMin}-${t.ageMax}`, { min: t.ageMin, max: t.ageMax }));
    return [...seen.values()].sort((a, b) => a.min - b.min);
  });
  readonly rows = computed(() =>
    this.ageBands().map((band) => ({
      band,
      cells: this.durations().map<Cell>((d) => ({
        ageMin: band.min,
        ageMax: band.max,
        maxDays: d,
        tariff: this.tariffs().find((t) => t.ageMin === band.min && t.ageMax === band.max && t.maxDays === d) ?? null,
      })),
    })),
  );
  readonly missingCells = computed(() => this.rows().flatMap((r) => r.cells).filter((c) => !c.tariff));

  // ---------- Simulateur ----------
  readonly form = signal<VoyageForm | null>(null);
  readonly simCountryId = signal<number | null>(null);
  readonly simAge = signal<number | null>(34);
  readonly simDays = signal<number | null>(12);
  readonly simulating = signal(false);
  readonly simResult = signal<VoyageSimulation | null>(null);
  readonly simError = signal<string | null>(null);

  constructor() {
    this.loadCountries();
    this.loadTariffs();
  }

  setTab(tab: Tab): void {
    this.tab.set(tab);
    this.error.set(null);
    this.info.set(null);
    if (tab === 'simulator' && !this.form()) this.loadForm();
  }

  // ====================== Pays ======================
  loadCountries(): void {
    this.countriesLoading.set(true);
    this.service.countries().subscribe({
      next: (items) => {
        this.countries.set(items);
        this.countriesLoading.set(false);
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger les pays.'));
        this.countriesLoading.set(false);
      },
    });
  }

  pickSpace(space: VoyageSpace | null): void {
    this.spaceFilter.set(space && space === this.spaceFilter() ? null : space);
  }

  spaceLabel(code: string): string {
    return this.spaces.find((s) => s.code === code)?.label ?? code;
  }

  newCountry(): void {
    this.modalError.set(null);
    this.countryEdit.set({ id: null, name: '', space: this.spaceFilter() ?? 'AFRIQUE_OUEST', active: true });
  }

  editCountry(c: VoyageCountry): void {
    this.modalError.set(null);
    this.countryEdit.set({ id: c.id, name: c.name, space: c.space, active: c.active });
  }

  submitCountry(): void {
    const d = this.countryEdit();
    if (!d || this.saving()) return;
    if (!d.name.trim()) {
      this.modalError.set('Le nom du pays est obligatoire.');
      return;
    }
    this.saving.set(true);
    this.modalError.set(null);
    const body = { name: d.name.trim(), space: d.space, active: d.active };
    const call = d.id === null ? this.service.createCountry(body) : this.service.updateCountry(d.id, body);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.countryEdit.set(null);
        this.info.set(d.id === null ? 'Pays ajouté.' : 'Pays enregistré.');
        this.loadCountries();
      },
      error: (err) => {
        this.saving.set(false);
        this.modalError.set(this.messageOf(err, "Impossible d'enregistrer le pays."));
      },
    });
  }

  toggleCountry(c: VoyageCountry): void {
    this.error.set(null);
    this.service.updateCountry(c.id, { name: c.name, space: c.space, active: !c.active }).subscribe({
      next: () => this.loadCountries(),
      error: (err) => this.error.set(this.messageOf(err, 'Impossible de changer le statut du pays.')),
    });
  }

  confirmDeleteCountry(): void {
    const c = this.countryDelete();
    if (!c || this.saving()) return;
    this.saving.set(true);
    this.service.deleteCountry(c.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.countryDelete.set(null);
        this.info.set('Pays supprimé.');
        this.loadCountries();
      },
      error: (err) => {
        this.saving.set(false);
        this.countryDelete.set(null);
        this.error.set(this.messageOf(err, 'Impossible de supprimer le pays.'));
      },
    });
  }

  // ====================== Grille ======================
  loadTariffs(): void {
    this.tariffsLoading.set(true);
    this.service.tariffs(this.tariffSpace()).subscribe({
      next: (items) => {
        this.tariffs.set(items);
        this.tariffsLoading.set(false);
        this.refreshSelection();
      },
      error: (err) => {
        this.error.set(this.messageOf(err, 'Impossible de charger la grille.'));
        this.tariffsLoading.set(false);
      },
    });
  }

  pickTariffSpace(space: VoyageSpace): void {
    if (space === this.tariffSpace()) return;
    this.tariffSpace.set(space);
    this.selected.set(null);
    this.detail.set(null);
    this.loadTariffs();
  }

  durationLabel(d: number | null): string {
    return d === null ? '1 an' : `≤ ${d} j`;
  }

  durationLong(d: number | null): string {
    return d === null ? 'plus de 180 jours (1 an)' : `jusqu'à ${d} jours`;
  }

  bandLabel(b: { min: number; max: number }): string {
    return `${b.min} – ${b.max} ${b.max <= 1 ? 'an' : 'ans'}`;
  }

  isSelected(c: Cell): boolean {
    const s = this.selected();
    return !!s && s.ageMin === c.ageMin && s.ageMax === c.ageMax && s.maxDays === c.maxDays;
  }

  select(c: Cell): void {
    this.selected.set(c);
    this.deleteAsk.set(false);
    this.modalError.set(null);
    this.detail.set(c.tariff ? { net: c.tariff.netPremium, accessories: c.tariff.accessories, taxes: c.tariff.taxes } : null);
  }

  /** Après un rechargement : garde la cellule sélectionnée à jour. */
  private refreshSelection(): void {
    const s = this.selected();
    if (!s) return;
    const t = this.tariffs().find((x) => x.ageMin === s.ageMin && x.ageMax === s.ageMax && x.maxDays === s.maxDays) ?? null;
    this.select({ ...s, tariff: t });
  }

  total(a: { net: number; accessories: number; taxes: number } | null): number {
    return a ? (Number(a.net) || 0) + (Number(a.accessories) || 0) + (Number(a.taxes) || 0) : 0;
  }

  saveTariff(): void {
    const s = this.selected();
    const d = this.detail();
    if (!s?.tariff || !d || this.saving()) return;
    if ([d.net, d.accessories, d.taxes].some((v) => !(Number(v) >= 0))) {
      this.error.set('Les montants doivent être positifs ou nuls.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.service
      .updateTariff(s.tariff.id, { netPremium: Number(d.net), accessories: Number(d.accessories), taxes: Number(d.taxes), totalPremium: this.total(d) })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.info.set('Tarif enregistré.');
          this.loadTariffs();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(this.messageOf(err, "Impossible d'enregistrer le tarif."));
        },
      });
  }

  deleteTariff(): void {
    const s = this.selected();
    if (!s?.tariff) return;
    if (!this.deleteAsk()) {
      this.deleteAsk.set(true);
      return;
    }
    this.deleteAsk.set(false);
    this.service.deleteTariff(s.tariff.id).subscribe({
      next: () => {
        this.info.set('Tranche supprimée : la combinaison n’est plus couverte.');
        this.loadTariffs();
      },
      error: (err) => this.error.set(this.messageOf(err, 'Impossible de supprimer la tranche.')),
    });
  }

  newTariff(cell?: Cell): void {
    this.modalError.set(null);
    this.tariffNew.set({ ageMin: cell?.ageMin ?? null, ageMax: cell?.ageMax ?? null, maxDays: cell ? cell.maxDays : null, net: 0, accessories: 500, taxes: 0 });
  }

  submitTariff(): void {
    const d = this.tariffNew();
    if (!d || this.saving()) return;
    if (d.ageMin === null || d.ageMax === null || d.ageMin < 0 || d.ageMax < d.ageMin) {
      this.modalError.set("Renseignez une tranche d'âge valide (minimum ≤ maximum).");
      return;
    }
    if ([d.net, d.accessories, d.taxes].some((v) => !(Number(v) >= 0))) {
      this.modalError.set('Les montants doivent être positifs ou nuls.');
      return;
    }
    this.saving.set(true);
    this.modalError.set(null);
    this.service
      .createTariff({
        space: this.tariffSpace(),
        ageMin: Number(d.ageMin),
        ageMax: Number(d.ageMax),
        maxDays: d.maxDays ? Number(d.maxDays) : undefined,
        netPremium: Number(d.net),
        accessories: Number(d.accessories),
        taxes: Number(d.taxes),
        totalPremium: this.total({ net: d.net, accessories: d.accessories, taxes: d.taxes }),
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.tariffNew.set(null);
          this.info.set('Tranche ajoutée.');
          this.loadTariffs();
        },
        error: (err) => {
          this.saving.set(false);
          this.modalError.set(this.messageOf(err, "Impossible d'ajouter la tranche."));
        },
      });
  }

  // ====================== Simulateur ======================
  loadForm(): void {
    this.service.form().subscribe({
      next: (f) => {
        this.form.set(f);
        if (this.simCountryId() === null && f.countries.length) this.simCountryId.set(f.countries[0].id);
      },
      error: (err) => this.error.set(this.messageOf(err, 'Impossible de charger la liste des pays.')),
    });
  }

  simulate(): void {
    const id = this.simCountryId();
    if (id === null || this.simAge() === null || this.simDays() === null || this.simulating()) return;
    const max = this.form()?.maxDurationDays ?? 365;
    if (Number(this.simDays()) > max) {
      this.simResult.set(null);
      this.simError.set(`La durée ne peut pas dépasser ${max} jours (1 an).`);
      return;
    }
    this.simulating.set(true);
    this.simError.set(null);
    this.service.simulate({ countryId: id, age: Number(this.simAge()), durationDays: Number(this.simDays()) }).subscribe({
      next: (r) => {
        this.simResult.set(r);
        this.simulating.set(false);
      },
      error: (err) => {
        this.simResult.set(null);
        this.simulating.set(false);
        this.simError.set(this.simMessage(err));
      },
    });
  }

  share(part: number, total: number): number {
    return total > 0 ? Math.max(0, (part / total) * 100) : 0;
  }

  // ====================== Utilitaires ======================
  private normalize(value: string): string {
    return value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
  }

  private simMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.error?.code === 'TARIFF_NOT_AVAILABLE') return 'Aucun tarif ne correspond à cette combinaison : complétez la grille.';
      if (err.error?.code === 'VALIDATION_FAILED') {
        const field = err.error?.field ? ` (${err.error.field})` : '';
        return `${err.error?.message || 'Valeurs invalides.'}${field}`;
      }
    }
    return 'Impossible de calculer la prime.';
  }

  private messageOf(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      const code = err.error?.code as string | undefined;
      if (code === 'COUNTRY_ALREADY_EXISTS') return 'Ce pays existe déjà.';
      if (code === 'TARIFF_ALREADY_EXISTS') return 'Cette tranche existe déjà pour cet espace.';
      if (err.status === 404) return 'Élément introuvable : il a peut-être été supprimé.';
      if (err.status === 403) return 'Accès non autorisé.';
      if (code === 'VALIDATION_FAILED' || err.status === 400) {
        const field = err.error?.field ? ` (${err.error.field})` : '';
        return `${typeof err.error?.message === 'string' && err.error.message ? err.error.message : 'Valeurs invalides.'}${field}`;
      }
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return fallback;
  }
}
