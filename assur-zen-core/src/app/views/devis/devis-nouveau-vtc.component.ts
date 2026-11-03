import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { QuoteSimulation, VtcEnergy, VtcQuoteForm, VtcVehicleRequest } from '../../core/quotes/models/quote.model';
import { QuoteVtcSettingsService } from '../../core/quotes/quote-vtc-settings.service';
import { QuoteService } from '../../core/quotes/quote.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

const FIELD_TO_CONTROL: Record<string, string> = {
  prospectName: 'prospectName',
  prospectPhone: 'prospectPhone',
  fiscalPower: 'fiscalPower',
  energy: 'energy',
  brand: 'brand',
  model: 'model',
  firstRegistrationYear: 'firstRegistrationYear',
  seats: 'seats',
  vtcDeclared: 'vtcDeclared',
  platform: 'platform',
  durationMonths: 'durationMonths',
  registrationNumber: 'registrationNumber',
  chassisNumber: 'registrationNumber',
  effectiveDate: 'effectiveDate',
  discountPercent: 'discountPercent',
};

const ENERGY_LABEL: Record<VtcEnergy, string> = {
  ESSENCE: 'Essence',
  DIESEL: 'Diesel',
  HYBRIDE: 'Hybride',
  ELECTRIQUE: 'Électrique',
};

const DEFAULT_DURATIONS = [1, 3, 6, 12];
const DEFAULT_MAX_DISCOUNT = 10;

function today(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

@Component({
  selector: 'app-devis-nouveau-vtc',
  templateUrl: './devis-nouveau-vtc.component.html',
  imports: [AlertMessageComponent, ButtonDirective, ReactiveFormsModule, RouterLink, SpinnerComponent],
})
export class DevisNouveauVtcComponent {
  private readonly service = inject(QuoteService);
  private readonly settings = inject(QuoteVtcSettingsService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly options = signal<VtcQuoteForm | null>(null);
  readonly simulation = signal<QuoteSimulation | null>(null);
  /** Valeurs ayant servi à la simulation (hors remise, estimée à l'écran). */
  private readonly simulatedKey = signal<string | null>(null);
  readonly simulating = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly identifierMissing = signal(false);
  /** Plafond de remise (réglage ADMIN) ; 10 par défaut si non lisible, le serveur fait foi. */
  readonly maxDiscount = signal(DEFAULT_MAX_DISCOUNT);

  readonly form = this.fb.group({
    prospectName: ['', [Validators.required, Validators.maxLength(150)]],
    prospectPhone: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]],
    brand: ['', Validators.required],
    model: ['', Validators.required],
    fiscalPower: [null as number | null, [Validators.required, Validators.min(1)]],
    energy: ['ESSENCE' as VtcEnergy, Validators.required],
    firstRegistrationYear: [null as number | null, [Validators.required, Validators.min(1950)]],
    seats: [5 as number | null, Validators.required],
    platform: [''],
    registrationNumber: [''],
    chassisNumber: [''],
    effectiveDate: [today(), Validators.required],
    vtcDeclared: [null as boolean | null, Validators.required],
    durationMonths: [12, Validators.required],
    discountPercent: [0],
  });

  private readonly formKey = signal(this.keyOf());
  private readonly discount = signal(0);

  readonly minDate = computed(() => this.options()?.minEffectiveDate ?? today());
  readonly minFiscalPower = computed(() => this.options()?.minFiscalPower ?? 3);
  readonly minSeats = computed(() => this.options()?.minSeats ?? 4);
  readonly maxSeats = computed(() => this.options()?.maxSeats ?? 8);
  readonly brands = computed(() => this.options()?.brands ?? []);
  readonly platforms = computed(() => this.options()?.platforms ?? []);
  readonly energies = computed<VtcEnergy[]>(() => this.options()?.energies ?? ['ESSENCE', 'DIESEL', 'HYBRIDE', 'ELECTRIQUE']);
  readonly durations = computed<number[]>(() => {
    const list = this.options()?.durations ?? [];
    const months = list.map((d) => (typeof d === 'number' ? d : d.months)).filter((m) => m > 0);
    return months.length ? months : DEFAULT_DURATIONS;
  });
  readonly energyLabel = ENERGY_LABEL;

  readonly simulationUpToDate = computed(() => !!this.simulation() && this.simulatedKey() === this.formKey());

  /** Remise estimée à l'écran : arrondi(prime nette × % / 100) ; le serveur recalcule et fait foi. */
  readonly estimate = computed(() => {
    const s = this.simulation();
    if (!s) return null;
    const p = s.premium;
    const discount = Math.round((p.netPremium * this.discount()) / 100);
    const total =
      p.netPremium - discount + p.taxes + p.accessories + p.fga + (p.cedeao ?? 0) + (p.attestation ?? 0);
    return { discount, total };
  });

  constructor() {
    this.service.vtcForm().subscribe({
      next: (opts) => this.options.set(opts),
      error: () => this.options.set(null),
    });
    // Lisible seulement par un administrateur : à défaut, plafond par défaut (le serveur refuse au-delà).
    this.settings.maxDiscountPercent().subscribe({
      next: (v) => this.maxDiscount.set(v),
      error: () => this.maxDiscount.set(DEFAULT_MAX_DISCOUNT),
    });
    this.form.valueChanges.subscribe(() => {
      this.formKey.set(this.keyOf());
      this.discount.set(Number(this.form.controls.discountPercent.value) || 0);
      this.fieldErrors.set({});
    });
  }

  setDeclared(value: boolean): void {
    this.form.controls.vtcDeclared.setValue(value);
    this.form.controls.vtcDeclared.markAsTouched();
  }

  setDuration(months: number): void {
    this.form.controls.durationMonths.setValue(months);
  }

  hasError(control: string): boolean {
    const c = this.form.get(control);
    return !!(c && c.invalid && (c.touched || c.dirty)) || !!this.fieldErrors()[control];
  }

  fieldError(control: string): string | null {
    return this.fieldErrors()[control] ?? null;
  }

  simulate(): void {
    if (!this.validate() || this.simulating()) return;
    this.simulating.set(true);
    this.error.set(null);
    const key = this.formKey();
    this.service.simulateVtc(this.vehicleRequest(false)).subscribe({
      next: (s) => {
        this.simulation.set(s);
        this.simulatedKey.set(key);
        this.simulating.set(false);
      },
      error: (err) => {
        this.simulating.set(false);
        this.handleError(err, 'Impossible de calculer le prix.');
      },
    });
  }

  save(): void {
    if (!this.validate() || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.service
      .createVtc({ prospectName: v.prospectName.trim(), prospectPhone: v.prospectPhone, vehicle: this.vehicleRequest(true) })
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

  formatAmount(value: number): string {
    return `${value.toLocaleString('fr-FR')} F`;
  }

  /** Empreinte du formulaire sans la remise : changer la remise ne demande pas de recalcul. */
  private keyOf(): string {
    const { discountPercent: _discount, ...rest } = this.form.getRawValue();
    return JSON.stringify(rest);
  }

  private validate(): boolean {
    this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    const missing = !v.registrationNumber.trim() && !v.chassisNumber.trim();
    this.identifierMissing.set(missing);
    const seats = v.seats ?? 0;
    const errors: Record<string, string> = {};
    if (v.fiscalPower !== null && v.fiscalPower < this.minFiscalPower()) {
      errors['fiscalPower'] = `Au moins ${this.minFiscalPower()} CV.`;
    }
    if (seats < this.minSeats() || seats > this.maxSeats()) {
      errors['seats'] = `Entre ${this.minSeats()} et ${this.maxSeats()} places.`;
    }
    if (Number(v.discountPercent) > this.maxDiscount()) {
      errors['discountPercent'] = `La remise est plafonnée à ${this.maxDiscount()} %.`;
    }
    this.fieldErrors.set(errors);
    return this.form.valid && !missing && Object.keys(errors).length === 0;
  }

  private vehicleRequest(withDiscount: boolean): VtcVehicleRequest {
    const v = this.form.getRawValue();
    const discount = Number(v.discountPercent) || 0;
    return {
      fiscalPower: v.fiscalPower as number,
      energy: v.energy,
      brand: v.brand.trim(),
      model: v.model.trim(),
      firstRegistrationYear: v.firstRegistrationYear as number,
      seats: v.seats as number,
      vtcDeclared: v.vtcDeclared as boolean,
      platform: v.platform.trim() || undefined,
      durationMonths: Number(v.durationMonths),
      registrationNumber: v.registrationNumber.trim() || undefined,
      chassisNumber: v.chassisNumber.trim() || undefined,
      effectiveDate: v.effectiveDate,
      ...(withDiscount && discount > 0 ? { discountPercent: discount } : {}),
    };
  }

  private handleError(err: unknown, fallback: string): void {
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (err.status === 400 && body?.code === 'VALIDATION_FAILED') {
        const control = FIELD_TO_CONTROL[body.field ?? ''];
        if (control) {
          this.fieldErrors.set({ [control]: body.message ?? 'Valeur invalide.' });
          return;
        }
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
