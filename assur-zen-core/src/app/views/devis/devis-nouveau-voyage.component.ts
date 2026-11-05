import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { SpinnerComponent } from '@coreui/angular';

import { CI_OPERATOR_LABEL, ciMobileOperator } from '../../core/quotes/ci-phone';
import { QUOTE_EMAIL_ENABLED } from '../../core/quotes/quote-email.config';
import { QUOTE_ADDRESS_ENABLED, QUOTE_SMS_ENABLED } from '../../core/quotes/quote-sms.config';
import { QuoteService } from '../../core/quotes/quote.service';
import { VoyageForm, VoyageService, VoyageSimulation } from '../../core/quotes/voyage.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

/** Champ de l'erreur 400 VALIDATION_FAILED (sans préfixe `voyage.`) → contrôle du formulaire. */
const FIELD_TO_CONTROL: Record<string, string> = {
  prospectName: 'prospectName',
  prospectPhone: 'prospectPhone',
  prospectEmail: 'prospectEmail',
  prospectAddress: 'prospectAddress',
  countryId: 'countryId',
  birthDate: 'birthDate',
  age: 'birthDate',
  departureDate: 'departureDate',
  returnDate: 'returnDate',
  durationDays: 'returnDate',
  passportNumber: 'passportNumber',
};

const PRICE_COLORS = ['#1b3b8f', '#ec7c1e', '#4a74e8'];
const DEFAULT_MAX_DAYS = 365;
const DEFAULT_MAX_AGE = 80;
const DAY_MS = 86_400_000;

function today(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Date `yyyy-MM-dd` → millisecondes UTC (null si invalide). */
function toUtc(value: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) return null;
  const t = Date.UTC(+m[1], +m[2] - 1, +m[3]);
  return Number.isNaN(t) ? null : t;
}

/** Âge en années entières à la date de départ. */
function ageAt(birth: string, departure: string): number | null {
  const b = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birth);
  const d = /^(\d{4})-(\d{2})-(\d{2})$/.exec(departure);
  if (!b || !d) return null;
  let age = +d[1] - +b[1];
  if (+d[2] < +b[2] || (+d[2] === +b[2] && +d[3] < +b[3])) age--;
  return age;
}

/** Devis > Nouveau > Voyage : voyageur, pays et dates ; prix calculé par la simulation publique. Maquette validée le 2026-11-05. */
@Component({
  selector: 'app-devis-nouveau-voyage',
  templateUrl: './devis-nouveau-voyage.component.html',
  styleUrl: './devis-form.component.scss',
  imports: [AlertMessageComponent, ReactiveFormsModule, RouterLink, SpinnerComponent],
})
export class DevisNouveauVoyageComponent {
  private readonly service = inject(QuoteService);
  private readonly voyage = inject(VoyageService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly options = signal<VoyageForm | null>(null);
  readonly simulation = signal<VoyageSimulation | null>(null);
  private readonly simulatedKey = signal<string | null>(null);
  readonly simulating = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly simError = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});

  readonly form = this.fb.group({
    prospectName: ['', [Validators.required, Validators.maxLength(150)]],
    prospectPhone: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]],
    prospectEmail: ['', [Validators.email, Validators.maxLength(150)]],
    prospectAddress: ['', [Validators.maxLength(250)]],
    birthDate: ['', Validators.required],
    passportNumber: ['', Validators.maxLength(40)],
    countryId: [null as number | null, Validators.required],
    departureDate: ['', Validators.required],
    returnDate: ['', Validators.required],
  });

  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly formKey = computed(() => JSON.stringify(this.value()));

  readonly countries = computed(() => this.options()?.countries ?? []);
  readonly minDeparture = computed(() => this.options()?.minDepartureDate ?? today());
  readonly maxDays = computed(() => this.options()?.maxDurationDays ?? DEFAULT_MAX_DAYS);
  readonly maxAge = computed(() => this.options()?.maxAge ?? DEFAULT_MAX_AGE);

  readonly selectedCountry = computed(() => {
    const id = this.value().countryId;
    return this.countries().find((c) => c.id === Number(id)) ?? null;
  });

  /** Âge du voyageur à la date de départ (années entières). */
  readonly age = computed(() => {
    const v = this.value();
    const a = ageAt(v.birthDate ?? '', v.departureDate ?? '');
    return a !== null && a >= 0 ? a : null;
  });

  /** Durée du séjour : départ et retour inclus. */
  readonly durationDays = computed(() => {
    const v = this.value();
    const a = toUtc(v.departureDate ?? '');
    const b = toUtc(v.returnDate ?? '');
    if (a === null || b === null || b < a) return null;
    return Math.round((b - a) / DAY_MS) + 1;
  });

  /** Message sous le champ, calculé à l'écran (le serveur confirme à l'enregistrement). */
  readonly birthProblem = computed(() => {
    const v = this.value();
    if (!v.birthDate) return null;
    const b = toUtc(v.birthDate);
    if (b === null || b > Date.now()) return 'La date de naissance est invalide.';
    const a = this.age();
    if (a !== null && a > this.maxAge()) return `L'assurance voyage couvre les personnes de 0 à ${this.maxAge()} ans (âge au départ).`;
    return null;
  });
  readonly departureProblem = computed(() => {
    const d = this.value().departureDate;
    return d && d < this.minDeparture() ? 'Le départ ne peut pas être dans le passé.' : null;
  });
  readonly returnProblem = computed(() => {
    const v = this.value();
    if (!v.departureDate || !v.returnDate) return null;
    if (v.returnDate < v.departureDate) return 'La date de retour doit suivre la date de départ.';
    const d = this.durationDays();
    if (d !== null && d > this.maxDays()) return `Le séjour ne peut pas dépasser ${this.maxDays()} jours (1 an).`;
    return null;
  });

  readonly nameOk = computed(() => (this.value(), this.form.controls.prospectName.valid && this.form.controls.prospectName.value.trim().length > 1));
  readonly phoneOk = computed(() => (this.value(), this.form.controls.prospectPhone.valid));
  readonly emailOk = computed(() => (this.value(), this.form.controls.prospectEmail.valid));
  readonly emailFilled = computed(() => (this.value(), !!this.form.controls.prospectEmail.value.trim() && this.form.controls.prospectEmail.valid));
  readonly birthOk = computed(() => !!this.value().birthDate && !this.birthProblem());
  readonly step1Done = computed(() => this.nameOk() && this.phoneOk() && this.emailOk() && this.birthOk());
  readonly countryOk = computed(() => this.selectedCountry() !== null);
  readonly datesOk = computed(() => !!this.value().departureDate && !!this.value().returnDate && !this.departureProblem() && !this.returnProblem() && this.durationDays() !== null);
  readonly step2Done = computed(() => this.countryOk() && this.datesOk());
  readonly progress = computed(() => {
    const steps = [this.nameOk(), this.phoneOk(), this.birthOk(), this.countryOk(), this.datesOk()];
    return Math.round((steps.filter(Boolean).length / steps.length) * 100);
  });
  readonly ready = computed(() => this.step1Done() && this.step2Done() && this.age() !== null);

  readonly emailEnabled = QUOTE_EMAIL_ENABLED;
  readonly sendEmail = signal(false);
  readonly addressEnabled = QUOTE_ADDRESS_ENABLED;
  readonly smsEnabled = QUOTE_SMS_ENABLED;
  readonly smsToggle = signal(true);
  readonly phoneDigits = computed(() => (this.value(), this.form.controls.prospectPhone.value.replace(/\D/g, '').length));
  readonly operator = computed(() => (this.value(), ciMobileOperator(this.form.controls.prospectPhone.value)));
  readonly smsCapable = computed(() => this.operator() !== null);
  readonly smsOn = computed(() => this.smsCapable() && this.smsToggle());
  readonly operatorLabel = computed(() => (this.operator() ? CI_OPERATOR_LABEL[this.operator()!] : ''));

  readonly simulationUpToDate = computed(() => !!this.simulation() && this.simulatedKey() === this.priceKey());
  readonly shownSimulation = computed(() => (this.ready() ? this.simulation() : null));
  /** Le prix ne dépend que du pays, de l'âge et de la durée. */
  private readonly priceKey = computed(() => `${this.selectedCountry()?.id}|${this.age()}|${this.durationDays()}`);

  readonly rows = computed(() => {
    const s = this.simulation();
    if (!s) return [];
    return [
      { label: 'Prime nette HT', amount: s.netPremium },
      { label: 'Accessoires', amount: s.accessories },
      { label: 'Taxes', amount: s.taxes },
    ];
  });
  readonly bars = computed(() => {
    const rows = this.rows().filter((r) => r.amount > 0);
    const sum = rows.reduce((a, r) => a + r.amount, 0) || 1;
    return rows.map((r, i) => ({ percent: (r.amount / sum) * 100, color: PRICE_COLORS[i % PRICE_COLORS.length] }));
  });

  constructor() {
    this.voyage.form().subscribe({
      next: (opts) => this.options.set(opts),
      error: () => this.options.set(null),
    });
    // Calcul automatique du prix (simulation sans écriture) dès que le formulaire est complet.
    this.form.valueChanges.pipe(debounceTime(400)).subscribe(() => {
      this.fieldErrors.set({});
      if (this.ready() && !this.simulationUpToDate()) this.simulate();
    });
  }

  hasError(control: string): boolean {
    return !!this.fieldErrors()[control];
  }

  fieldError(control: string): string | null {
    return this.fieldErrors()[control] ?? null;
  }

  simulate(): void {
    const country = this.selectedCountry();
    const age = this.age();
    const days = this.durationDays();
    if (!this.ready() || !country || age === null || days === null || this.simulating()) return;
    this.simulating.set(true);
    this.simError.set(null);
    const key = this.priceKey();
    this.voyage.simulate({ countryId: country.id, age, durationDays: days }).subscribe({
      next: (s) => {
        this.simulation.set(s);
        this.simulatedKey.set(key);
        this.simulating.set(false);
        if (this.priceKey() !== key && this.ready()) this.simulate();
      },
      error: (err) => {
        this.simulating.set(false);
        this.simulation.set(null);
        this.simError.set(this.simMessage(err));
      },
    });
  }

  save(): void {
    const country = this.selectedCountry();
    if (!this.ready() || !country || !this.simulationUpToDate() || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const email = v.prospectEmail.trim();
    const address = v.prospectAddress.trim();
    this.service
      .createVoyage({
        prospectName: v.prospectName.trim(),
        prospectPhone: v.prospectPhone,
        ...(this.emailEnabled && email ? { prospectEmail: email, ...(this.sendEmail() ? { sendEmail: true } : {}) } : {}),
        ...(this.addressEnabled && address ? { prospectAddress: address } : {}),
        ...(this.smsEnabled && this.smsOn() ? { sendSms: true } : {}),
        voyage: {
          countryId: country.id,
          birthDate: v.birthDate,
          departureDate: v.departureDate,
          returnDate: v.returnDate,
          ...(v.passportNumber.trim() ? { passportNumber: v.passportNumber.trim() } : {}),
        },
      })
      .subscribe({
        next: (q) => {
          this.saving.set(false);
          void this.router.navigate(['/devis/liste', q.reference]);
        },
        error: (err) => {
          this.saving.set(false);
          this.handleError(err, "Impossible d'enregistrer le devis.");
        },
      });
  }

  toggleSms(): void {
    if (this.smsCapable()) this.smsToggle.set(!this.smsToggle());
  }

  formatAmount(value: number): string {
    return `${value.toLocaleString('fr-FR')} F`;
  }

  private simMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.error?.code === 'TARIFF_NOT_AVAILABLE') return "Aucun tarif ne correspond à cette combinaison : demandez à un administrateur de compléter la grille.";
      if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
    }
    return 'Impossible de calculer le prix.';
  }

  private handleError(err: unknown, fallback: string): void {
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (err.status === 400 && body?.code === 'VALIDATION_FAILED') {
        const field = String(body.field ?? '').split('.').pop() ?? '';
        const control = FIELD_TO_CONTROL[field];
        if (control) {
          this.fieldErrors.set({ [control]: body.message ?? 'Valeur invalide.' });
          return;
        }
      }
      if (err.status === 409 && body?.code === 'TARIFF_NOT_AVAILABLE') {
        this.error.set("Aucun tarif ne correspond à cette combinaison : demandez à un administrateur de compléter la grille.");
        return;
      }
      if (err.status === 403) {
        this.error.set('Accès non autorisé.');
        return;
      }
      if (typeof body?.message === 'string' && body.message) {
        this.error.set(body.message);
        return;
      }
    }
    this.error.set(fallback);
  }
}
