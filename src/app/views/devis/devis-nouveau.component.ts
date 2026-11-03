import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonDirective, SpinnerComponent } from '@coreui/angular';

import { QuoteForm, QuoteSimulation, QuoteVehicleRequest } from '../../core/quotes/models/quote.model';
import { QuoteService } from '../../core/quotes/quote.service';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';

/** Champs du contrat (erreur 400 VALIDATION_FAILED, `field`) → contrôle du formulaire. */
const FIELD_TO_CONTROL: Record<string, string> = {
  prospectPhone: 'prospectPhone',
  prospectName: 'prospectName',
  registrationNumber: 'registrationNumber',
  chassisNumber: 'registrationNumber',
  effectiveDate: 'effectiveDate',
  durationMonths: 'durationMonths',
  vehicleType: 'vehicleType',
};

function today(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

@Component({
  selector: 'app-devis-nouveau',
  templateUrl: './devis-nouveau.component.html',
  imports: [AlertMessageComponent, ButtonDirective, ReactiveFormsModule, RouterLink, SpinnerComponent],
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
    vehicleType: ['MOTO' as 'MOTO' | 'TRICYCLE', Validators.required],
    cylinderCc: [null as number | null, [Validators.required, Validators.min(1)]],
    brand: ['', Validators.required],
    model: ['', Validators.required],
    durationMonths: [12, Validators.required],
    effectiveDate: [today(), Validators.required],
    registrationNumber: [''],
    chassisNumber: [''],
  });

  private readonly formKey = signal(JSON.stringify(this.form.getRawValue()));

  readonly minDate = computed(() => this.options()?.minEffectiveDate ?? today());
  readonly identifierMissing = signal(false);
  /** Le prix affiché correspond bien aux valeurs actuelles du formulaire. */
  readonly simulationUpToDate = computed(() => !!this.simulation() && this.simulatedKey() === this.formKey());

  constructor() {
    this.service.form().subscribe({
      next: (opts) => this.options.set(opts),
      error: () => this.options.set(null),
    });
    this.form.valueChanges.subscribe(() => {
      this.formKey.set(JSON.stringify(this.form.getRawValue()));
      this.fieldErrors.set({});
    });
  }

  durations(): number[] {
    const list = this.options()?.durations.map((d) => d.months);
    return list?.length ? list : [12];
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
    this.service.simulate(this.vehicleRequest()).subscribe({
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
      .create({ prospectName: v.prospectName.trim(), prospectPhone: v.prospectPhone, vehicle: this.vehicleRequest() })
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

  private validate(): boolean {
    this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    const missing = !v.registrationNumber.trim() && !v.chassisNumber.trim();
    this.identifierMissing.set(missing);
    return this.form.valid && !missing;
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
