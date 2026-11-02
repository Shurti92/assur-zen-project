import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
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
import { TranslocoPipe } from '@jsverse/transloco';

import { SinistreService } from '../../../core/sinistres/sinistre.service';
import { SinistreResponse } from '../../../core/sinistres/models/sinistre.model';
import { ContractService } from '../../../core/contracts/contract.service';
import { ContractResponse } from '../../../core/contracts/models/contract.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ThousandSeparatorDirective } from '../../../shared/directives/thousand-separator.directive';

const DESCRIPTION_MAX_LENGTH = 4000;

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-declare-sinistre',
  templateUrl: './declare-sinistre.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    ReactiveFormsModule,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    AlertMessageComponent,
    ThousandSeparatorDirective,
    TranslocoPipe,
  ],
})
export class DeclareSinistreComponent {
  private readonly sinistreService = inject(SinistreService);
  private readonly contractService = inject(ContractService);
  private readonly fb = inject(FormBuilder);

  readonly contracts = signal<ContractResponse[]>([]);
  readonly contractsLoading = signal(true);

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<SinistreResponse | null>(null);

  readonly form = this.fb.nonNullable.group({
    contractId: [0],
    nature: [''],
    dateSurvenance: [today()],
    montantEstime: [0],
    description: [''],
    notes: [''],
  });

  readonly descriptionMaxLength = DESCRIPTION_MAX_LENGTH;

  private readonly contractIdValue = toSignal(this.form.controls.contractId.valueChanges, {
    initialValue: this.form.controls.contractId.value,
  });
  private readonly descriptionValue = toSignal(this.form.controls.description.valueChanges, {
    initialValue: this.form.controls.description.value,
  });

  readonly selectedContract = computed(() => {
    const id = Number(this.contractIdValue());
    return this.contracts().find((c) => c.id === id) ?? null;
  });
  readonly descriptionLength = computed(() => this.descriptionValue().length);

  constructor() {
    this.fetchContracts();
  }

  private fetchContracts(): void {
    this.contractsLoading.set(true);
    this.contractService.listAll('signed').subscribe({
      next: (page) => {
        this.contracts.set(page.content);
        this.contractsLoading.set(false);
      },
      error: () => {
        this.contractsLoading.set(false);
      },
    });
  }

  submit(): void {
    const raw = this.form.getRawValue();
    if (!raw.contractId) {
      this.error.set('sinistres.declare.erreurContratRequis');
      return;
    }
    if (!raw.nature.trim()) {
      this.error.set('sinistres.declare.erreurNatureRequise');
      return;
    }
    if (!raw.dateSurvenance) {
      this.error.set('sinistres.declare.erreurDateRequise');
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);

    this.sinistreService
      .declare({
        contractId: Number(raw.contractId),
        nature: raw.nature,
        dateSurvenance: raw.dateSurvenance,
        montantEstime: raw.montantEstime ? Number(raw.montantEstime) : undefined,
        description: raw.description.trim() || undefined,
        notes: raw.notes.trim() || undefined,
      })
      .subscribe({
        next: (sinistre) => {
          this.saving.set(false);
          this.success.set(sinistre);
          this.form.reset({ contractId: 0, nature: '', dateSurvenance: today(), montantEstime: 0, description: '', notes: '' });
        },
        error: () => {
          this.saving.set(false);
          this.error.set('sinistres.declare.erreurEnregistrement');
        },
      });
  }

  resetForm(): void {
    this.error.set(null);
    this.success.set(null);
    this.form.reset({ contractId: 0, nature: '', dateSurvenance: today(), montantEstime: 0, description: '', notes: '' });
  }

  contractLabel(contract: ContractResponse): string {
    return `${contract.policyNumber} — ${contract.insuredName}`;
  }

  formatAmount(value: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value)} FCFA`;
  }
}
