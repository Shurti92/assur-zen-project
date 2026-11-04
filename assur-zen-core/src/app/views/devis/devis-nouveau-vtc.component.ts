import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { SpinnerComponent } from '@coreui/angular';

import { QuoteSimulation, VtcEnergy, VtcQuoteForm, VtcVehicleRequest } from '../../core/quotes/models/quote.model';
import { CI_OPERATOR_LABEL, ciMobileOperator } from '../../core/quotes/ci-phone';
import { QUOTE_EMAIL_ENABLED } from '../../core/quotes/quote-email.config';
import { QUOTE_ADDRESS_ENABLED, QUOTE_SMS_ENABLED } from '../../core/quotes/quote-sms.config';
import { QuoteService } from '../../core/quotes/quote.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

const FIELD_TO_CONTROL: Record<string, string> = {
  prospectName: 'prospectName',
  prospectPhone: 'prospectPhone',
  prospectEmail: 'prospectEmail',
  prospectAddress: 'prospectAddress',
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

const PRICE_COLORS = ['#4f46e5', '#06b6d4', '#f59e0b', '#16a34a', '#8b5cf6', '#ec4899'];
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
  styleUrl: './devis-form.component.scss',
  imports: [AlertMessageComponent, ReactiveFormsModule, RouterLink, SpinnerComponent],
})
export class DevisNouveauVtcComponent {
  private readonly service = inject(QuoteService);
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
  /** Plafond de remise (réglage ADMIN) ; 10 par défaut si non lisible, le serveur fait foi. */
  readonly maxDiscount = signal(DEFAULT_MAX_DISCOUNT);

  readonly form = this.fb.group({
    prospectName: ['', [Validators.required, Validators.maxLength(150)]],
    prospectPhone: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]],
    prospectEmail: ['', [Validators.email, Validators.maxLength(150)]],
    prospectAddress: ['', [Validators.maxLength(250)]],
    brand: ['', Validators.required],
    model: ['', Validators.required],
    fiscalPower: [null as number | null, [Validators.required, Validators.min(1)]],
    energy: ['ESSENCE' as VtcEnergy, Validators.required],
    firstRegistrationYear: [null as number | null, [Validators.required, Validators.min(1950)]],
    seats: [5 as number, Validators.required],
    platform: [''],
    registrationNumber: [''],
    chassisNumber: [''],
    effectiveDate: [today(), Validators.required],
    vtcDeclared: [true as boolean, Validators.required],
    durationMonths: [12, Validators.required],
    discountPercent: [0],
  });

  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  /** Empreinte du formulaire sans la remise : changer la remise ne demande pas de recalcul. */
  readonly formKey = computed(() => {
    const { discountPercent: _discount, ...rest } = this.value();
    return JSON.stringify(rest);
  });
  readonly discount = computed(() => Number(this.value().discountPercent) || 0);

  readonly minDate = computed(() => this.options()?.minEffectiveDate ?? today());
  readonly minFiscalPower = computed(() => this.options()?.minFiscalPower ?? 3);
  readonly minSeats = computed(() => this.options()?.minSeats ?? 4);
  readonly maxSeats = computed(() => this.options()?.maxSeats ?? 8);
  readonly brands = computed(() => this.options()?.brands ?? []);
  readonly platforms = computed(() => this.options()?.platforms ?? []);
  readonly energies = computed<VtcEnergy[]>(
    () => this.options()?.energies ?? ['ESSENCE', 'DIESEL', 'HYBRIDE', 'ELECTRIQUE'],
  );
  readonly durations = computed<number[]>(() => {
    const list = this.options()?.durations ?? [];
    const months = list.map((d) => (typeof d === 'number' ? d : d.months)).filter((m) => m > 0);
    return months.length ? months : DEFAULT_DURATIONS;
  });
  readonly energyLabel = ENERGY_LABEL;

  readonly nameOk = computed(
    () => (this.value(), this.form.controls.prospectName.valid && this.form.controls.prospectName.value.trim().length > 1),
  );
  readonly phoneOk = computed(() => (this.value(), this.form.controls.prospectPhone.valid));
  readonly identifierOk = computed(() => {
    const v = this.value();
    return !!(v.registrationNumber?.trim() || v.chassisNumber?.trim());
  });
  readonly vehicleOk = computed(() => {
    const v = this.value();
    const c = this.form.controls;
    return (
      c.brand.valid &&
      c.model.valid &&
      c.fiscalPower.valid &&
      (v.fiscalPower ?? 0) >= this.minFiscalPower() &&
      c.firstRegistrationYear.valid &&
      (v.seats ?? 0) >= this.minSeats() &&
      (v.seats ?? 0) <= this.maxSeats() &&
      c.energy.valid
    );
  });
  readonly contractOk = computed(
    () => (this.value(), this.form.controls.durationMonths.valid && this.form.controls.effectiveDate.valid) && this.discount() <= this.maxDiscount(),
  );

  /** Envoi par e-mail : inactif tant que la gateway n'a pas confirmé l'API (QUOTE_EMAIL_ENABLED). */
  readonly emailEnabled = QUOTE_EMAIL_ENABLED;
  readonly sendEmail = signal(false);
  /** Adresse et SMS : inactifs tant que la gateway n'a pas confirmé l'API (quote-sms.config.ts). */
  readonly addressEnabled = QUOTE_ADDRESS_ENABLED;
  readonly smsEnabled = QUOTE_SMS_ENABLED;
  /** Interrupteur SMS (activé par défaut) : il ne compte que si le numéro est un mobile ivoirien valide. */
  readonly smsToggle = signal(true);
  readonly phoneDigits = computed(() => (this.value(), this.form.controls.prospectPhone.value.replace(/D/g, '').length));
  readonly operator = computed(() => (this.value(), ciMobileOperator(this.form.controls.prospectPhone.value)));
  readonly smsCapable = computed(() => this.operator() !== null);
  readonly smsOn = computed(() => this.smsCapable() && this.smsToggle());
  readonly operatorLabel = computed(() => (this.operator() ? CI_OPERATOR_LABEL[this.operator()!] : ''));
  readonly emailFilled = computed(
    () => (this.value(), !!this.form.controls.prospectEmail.value.trim() && this.form.controls.prospectEmail.valid),
  );
  readonly emailOk = computed(() => (this.value(), this.form.controls.prospectEmail.valid));
  readonly step1Done = computed(() => this.nameOk() && this.phoneOk() && this.emailOk());
  readonly step2Done = computed(() => this.vehicleOk() && this.identifierOk());
  readonly progress = computed(() => {
    const steps = [this.nameOk(), this.phoneOk(), this.vehicleOk(), this.identifierOk(), this.contractOk()];
    return Math.round((steps.filter(Boolean).length / steps.length) * 100);
  });
  /** Formulaire complet : le prix se calcule tout seul. */
  readonly ready = computed(() => this.step1Done() && this.step2Done() && this.contractOk());

  readonly simulationUpToDate = computed(() => !!this.simulation() && this.simulatedKey() === this.formKey());
  readonly shownSimulation = computed(() => (this.ready() ? this.simulation() : null));

  /** Remise estimée à l'écran : arrondi(prime nette × % / 100) ; le serveur recalcule et fait foi. */
  readonly rows = computed(() => {
    const s = this.simulation();
    if (!s) return [];
    const p = s.premium;
    const discount = Math.round((p.netPremium * this.discount()) / 100);
    // Taxes VTC = taux % de la prime nette : avec une remise, elles sont recalculées sur la prime nette remisée.
    // Le taux se déduit de la simulation sans remise (14,5 % pour le VTC, 0 si taxes nulles) ; le serveur fait foi.
    const taxRate = p.netPremium > 0 ? Math.round((p.taxes * 1000) / p.netPremium) / 10 : 0;
    const taxes = discount > 0 && taxRate > 0 ? Math.round(((p.netPremium - discount) * taxRate) / 100) : p.taxes;
    const rows: { label: string; amount: number; discount?: boolean }[] = [{ label: 'Prime nette', amount: p.netPremium }];
    if (discount > 0) rows.push({ label: `Remise ${this.discount()} %`, amount: -discount, discount: true });
    rows.push(
      { label: 'Taxes', amount: taxes },
      { label: 'Accessoires', amount: p.accessories },
      { label: 'CEDEAO', amount: p.cedeao ?? 0 },
      { label: 'Attestation', amount: p.attestation ?? 0 },
      { label: 'FGA', amount: p.fga },
    );
    return rows;
  });
  readonly total = computed(() => this.rows().reduce((a, r) => a + r.amount, 0));
  readonly bars = computed(() => {
    const rows = this.rows().filter((r) => r.amount > 0);
    const sum = rows.reduce((a, r) => a + r.amount, 0) || 1;
    return rows.map((r, i) => ({ percent: (r.amount / sum) * 100, color: PRICE_COLORS[i % PRICE_COLORS.length] }));
  });

  constructor() {
    this.service.vtcForm().subscribe({
      next: (opts) => this.options.set(opts),
      error: () => this.options.set(null),
    });
    // Plafond lisible par ADMIN et COMMERCIAL ; à défaut, 10 % (le serveur refuse au-delà du vrai plafond).
    this.service.vtcDiscountLimit().subscribe({
      next: (v) => this.maxDiscount.set(v),
      error: () => this.maxDiscount.set(DEFAULT_MAX_DISCOUNT),
    });
    // Calcul automatique du prix (simulation sans écriture, sans remise) dès que le formulaire est complet.
    this.form.valueChanges.pipe(debounceTime(400)).subscribe(() => {
      this.fieldErrors.set({});
      if (this.ready() && !this.simulationUpToDate()) this.simulate();
    });
  }

  setEnergy(energy: VtcEnergy): void {
    this.form.controls.energy.setValue(energy);
  }

  stepSeats(delta: -1 | 1): void {
    const next = Math.min(this.maxSeats(), Math.max(this.minSeats(), (this.form.controls.seats.value ?? 5) + delta));
    this.form.controls.seats.setValue(next);
  }

  toggleDeclared(): void {
    this.form.controls.vtcDeclared.setValue(!this.form.controls.vtcDeclared.value);
  }

  setDuration(months: number): void {
    this.form.controls.durationMonths.setValue(months);
  }

  hasError(control: string): boolean {
    return !!this.fieldErrors()[control];
  }

  fieldError(control: string): string | null {
    return this.fieldErrors()[control] ?? null;
  }

  simulate(): void {
    if (!this.ready() || this.simulating()) return;
    this.simulating.set(true);
    this.error.set(null);
    const key = this.formKey();
    this.service.simulateVtc(this.vehicleRequest(false)).subscribe({
      next: (s) => {
        this.simulation.set(s);
        this.simulatedKey.set(key);
        this.simulating.set(false);
        // Le formulaire a pu changer pendant le calcul : on recalcule.
        if (this.formKey() !== key && this.ready()) this.simulate();
      },
      error: (err) => {
        this.simulating.set(false);
        this.handleError(err, 'Impossible de calculer le prix.');
      },
    });
  }

  save(): void {
    if (!this.ready() || !this.simulationUpToDate() || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.service
      .createVtc({ prospectName: v.prospectName.trim(), prospectPhone: v.prospectPhone, ...this.emailPart(), vehicle: this.vehicleRequest(true) })
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

  /** E-mail du prospect (et envoi à l'enregistrement) : seulement si la fonction est active et l'adresse saisie. */
  private emailPart(): { prospectEmail?: string; sendEmail?: boolean; prospectAddress?: string; sendSms?: boolean } {
    const email = this.form.controls.prospectEmail.value.trim();
    const address = this.form.controls.prospectAddress.value.trim();
    return {
      ...(this.emailEnabled && email ? { prospectEmail: email, ...(this.sendEmail() ? { sendEmail: true } : {}) } : {}),
      ...(this.addressEnabled && address ? { prospectAddress: address } : {}),
      ...(this.smsEnabled && this.smsOn() ? { sendSms: true } : {}),
    };
  }

  formatAmount(value: number): string {
    return `${value.toLocaleString('fr-FR')} F`;
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
      seats: v.seats,
      vtcDeclared: v.vtcDeclared,
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
