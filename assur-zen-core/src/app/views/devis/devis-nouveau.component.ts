import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { SpinnerComponent } from '@coreui/angular';

import { QuoteForm, QuoteSimulation, QuoteVehicleRequest } from '../../core/quotes/models/quote.model';
import { QUOTE_EMAIL_ENABLED } from '../../core/quotes/quote-email.config';
import { QuoteService } from '../../core/quotes/quote.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

/** Champs du contrat (erreur 400 VALIDATION_FAILED, `field`) → contrôle du formulaire. */
const FIELD_TO_CONTROL: Record<string, string> = {
  prospectPhone: 'prospectPhone',
  prospectName: 'prospectName',
  prospectEmail: 'prospectEmail',
  registrationNumber: 'registrationNumber',
  chassisNumber: 'registrationNumber',
  effectiveDate: 'effectiveDate',
  durationMonths: 'durationMonths',
  vehicleType: 'vehicleType',
};

const PRICE_COLORS = ['#4f46e5', '#06b6d4', '#f59e0b', '#16a34a', '#8b5cf6', '#ec4899'];

function today(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

@Component({
  selector: 'app-devis-nouveau',
  templateUrl: './devis-nouveau.component.html',
  styleUrl: './devis-form.component.scss',
  imports: [AlertMessageComponent, ReactiveFormsModule, RouterLink, SpinnerComponent],
})
export class DevisNouveauComponent {
  private readonly service = inject(QuoteService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly options = signal<QuoteForm | null>(null);
  readonly simulation = signal<QuoteSimulation | null>(null);
  /** Valeurs ayant servi à la simulation : le prix n'est valable que si le formulaire n'a pas changé. */
  private readonly simulatedKey = signal<string | null>(null);
  readonly simulating = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});

  readonly form = this.fb.group({
    prospectName: ['', [Validators.required, Validators.maxLength(150)]],
    prospectPhone: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]],
    prospectEmail: ['', [Validators.email, Validators.maxLength(150)]],
    vehicleType: ['MOTO', Validators.required],
    cylinderCc: [null as number | null, [Validators.required, Validators.min(1)]],
    brand: ['', Validators.required],
    model: ['', Validators.required],
    durationMonths: [12, Validators.required],
    effectiveDate: [today(), Validators.required],
    registrationNumber: [''],
    chassisNumber: [''],
  });

  /** Valeur du formulaire en signal : les états calculés (étapes, progression, prêt) en dépendent. */
  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly formKey = computed(() => JSON.stringify(this.value()));

  readonly minDate = computed(() => this.options()?.minEffectiveDate ?? today());
  readonly brands = computed(() => this.options()?.brands ?? []);
  readonly vehicleTypes = computed(() => this.options()?.vehicleTypes ?? ['MOTO', 'TRICYCLE']);

  readonly nameOk = computed(() => (this.value(), this.form.controls.prospectName.valid && this.form.controls.prospectName.value.trim().length > 1));
  readonly phoneOk = computed(() => (this.value(), this.form.controls.prospectPhone.valid));
  readonly identifierOk = computed(() => {
    const v = this.value();
    return !!(v.registrationNumber?.trim() || v.chassisNumber?.trim());
  });
  readonly vehicleOk = computed(() => {
    this.value();
    const c = this.form.controls;
    return c.vehicleType.valid && c.cylinderCc.valid && c.brand.valid && c.model.valid;
  });
  readonly contractOk = computed(() => (this.value(), this.form.controls.durationMonths.valid && this.form.controls.effectiveDate.valid));

  /** Envoi par e-mail : inactif tant que la gateway n'a pas confirmé l'API (QUOTE_EMAIL_ENABLED). */
  readonly emailEnabled = QUOTE_EMAIL_ENABLED;
  readonly sendEmail = signal(false);
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

  /** Répartition du prix pour la barre colorée (composantes non nulles). */
  readonly rows = computed(() => {
    const s = this.simulation();
    if (!s) return [];
    const p = s.premium;
    return [
      { label: 'Prime nette', amount: p.netPremium },
      { label: 'Taxes', amount: p.taxes },
      { label: 'Accessoires', amount: p.accessories },
      { label: 'FGA', amount: p.fga },
    ];
  });
  readonly bars = computed(() => {
    const rows = this.rows().filter((r) => r.amount > 0);
    const sum = rows.reduce((a, r) => a + r.amount, 0) || 1;
    return rows.map((r, i) => ({ percent: (r.amount / sum) * 100, color: PRICE_COLORS[i % PRICE_COLORS.length] }));
  });

  constructor() {
    this.service.form().subscribe({
      next: (opts) => this.options.set(opts),
      error: () => this.options.set(null),
    });
    // Calcul automatique du prix (simulation sans écriture) dès que le formulaire est complet.
    this.form.valueChanges.pipe(debounceTime(400)).subscribe(() => {
      this.fieldErrors.set({});
      if (this.ready() && !this.simulationUpToDate()) this.simulate();
    });
  }

  durations(): number[] {
    const list = this.options()?.durations.map((d) => d.months);
    return list?.length ? list : [12];
  }

  setType(type: string): void {
    this.form.controls.vehicleType.setValue(type);
  }

  setDuration(months: number): void {
    this.form.controls.durationMonths.setValue(months);
  }

  typeIcon(type: string): string {
    return type === 'TRICYCLE' ? '🛺' : '🏍️';
  }

  typeLabel(type: string): string {
    return type === 'TRICYCLE' ? 'Tricycle' : 'Moto';
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
    this.service.simulate(this.vehicleRequest()).subscribe({
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
      .create({ prospectName: v.prospectName.trim(), prospectPhone: v.prospectPhone, ...this.emailPart(), vehicle: this.vehicleRequest() })
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

  /** E-mail du prospect (et envoi à l'enregistrement) : seulement si la fonction est active et l'adresse saisie. */
  private emailPart(): { prospectEmail?: string; sendEmail?: boolean } {
    const email = this.form.controls.prospectEmail.value.trim();
    if (!this.emailEnabled || !email) return {};
    return { prospectEmail: email, ...(this.sendEmail() ? { sendEmail: true } : {}) };
  }

  formatAmount(value: number): string {
    return `${value.toLocaleString('fr-FR')} F`;
  }

  private vehicleRequest(): QuoteVehicleRequest {
    const v = this.form.getRawValue();
    return {
      vehicleType: v.vehicleType,
      cylinderCc: v.cylinderCc as number,
      brand: v.brand.trim(),
      model: v.model.trim(),
      durationMonths: Number(v.durationMonths),
      registrationNumber: v.registrationNumber.trim() || undefined,
      chassisNumber: v.chassisNumber.trim() || undefined,
      effectiveDate: v.effectiveDate,
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
