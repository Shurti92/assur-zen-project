import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { forkJoin, Observable, of } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, FormControlDirective, FormDirective, FormLabelDirective, FormSelectDirective, SpinnerComponent } from '@coreui/angular';

import { SatisfactionImportService } from '../../../../core/satisfaction-import/satisfaction-import.service';
import {
  SatisfactionImportDuplicateStrategy,
  SatisfactionImportErrorResponse,
  SatisfactionImportErrorType,
  SatisfactionImportMappingProfileResponse,
  SatisfactionImportMappingRequest,
  SatisfactionImportMappingResponse,
  SatisfactionImportReportResponse,
  SatisfactionImportResponse,
  SatisfactionImportStatut,
  SatisfactionImportTargetField,
} from '../../../../core/satisfaction-import/models/satisfaction-import.model';
import { AlertMessageComponent } from '../../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../../shared/modal/modal.component';

interface MappingValueDraft {
  valeurSource: string;
  valeurCible: string;
  existing: boolean;
}

interface MappingRow {
  csvColumnName: string;
  targetField: SatisfactionImportTargetField | '';
  obligatoire: boolean;
  existingMappingId: number | null;
  valueMappings: MappingValueDraft[];
}

const SOURCE_PRESETS = ['Google Forms', 'CSV', 'Excel', 'API'] as const;

const TARGET_FIELD_ORDER: SatisfactionImportTargetField[] = [
  'RESPONSE_DATE',
  'CUSTOMER_NAME',
  'PHONE',
  'EMAIL',
  'POLICY_NUMBER',
  'SCORE',
  'COMMENT',
];

const TARGET_FIELD_LABEL: Record<SatisfactionImportTargetField, string> = {
  RESPONSE_DATE: 'Date de réponse',
  CUSTOMER_NAME: 'Nom du client',
  PHONE: 'Téléphone',
  EMAIL: 'Email',
  POLICY_NUMBER: 'Contrat',
  SCORE: 'Score',
  COMMENT: 'Commentaire',
};

const STATUT_LABEL: Record<SatisfactionImportStatut, string> = {
  PENDING: 'En attente',
  RUNNING: 'En cours',
  SUCCESS: 'Réussi',
  PARTIAL_SUCCESS: 'Partiellement réussi',
  FAILED: 'Échoué',
  CANCELLED: 'Annulé',
};

const STATUT_VARIANT: Record<SatisfactionImportStatut, string> = {
  PENDING: 'neutral',
  RUNNING: 'info',
  SUCCESS: 'success',
  PARTIAL_SUCCESS: 'warning',
  FAILED: 'danger',
  CANCELLED: 'neutral',
};

const ERROR_TYPE_LABEL: Record<SatisfactionImportErrorType, string> = {
  CONTRAT_INTROUVABLE: 'Contrat introuvable',
  CLIENT_INTROUVABLE: 'Client introuvable',
  TELEPHONE_INVALIDE: 'Téléphone invalide',
  EMAIL_INVALIDE: 'Email invalide',
  NOTE_INVALIDE: 'Note invalide',
  COLONNE_OBLIGATOIRE_ABSENTE: 'Colonne obligatoire absente',
  ENCODAGE_INVALIDE: 'Encodage invalide',
  FORMAT_DATE_INVALIDE: 'Format de date invalide',
  DOUBLON: 'Doublon',
  ERREUR_INCONNUE: 'Erreur inconnue',
};

@Component({
  selector: 'app-import-wizard',
  templateUrl: './import-wizard.component.html',
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
    ModalComponent,
    ConfirmDialogComponent,
  ],
})
export class ImportWizardComponent {
  private readonly satisfactionImportService = inject(SatisfactionImportService);
  private readonly fb = inject(FormBuilder);

  readonly steps = ['SOURCE', 'FICHIER', 'CORRESPONDANCE', 'VALIDATION'] as const;
  readonly currentStep = signal(0);
  readonly furthestStep = signal(0);
  readonly sourcePresets = SOURCE_PRESETS;
  readonly targetFieldOrder = TARGET_FIELD_ORDER;
  readonly targetFieldLabelMap = TARGET_FIELD_LABEL;

  // Step 1 — source & profil ------------------------------------------------
  readonly profiles = signal<SatisfactionImportMappingProfileResponse[]>([]);
  readonly profilesLoading = signal(true);
  readonly profileMode = signal<'existing' | 'new'>('existing');
  readonly existingMappings = signal<SatisfactionImportMappingResponse[]>([]);
  readonly sourceStepError = signal<string | null>(null);

  readonly sourceForm = this.fb.nonNullable.group({
    sourceLabel: 'CSV',
    profileId: '',
    newProfileCode: '',
    newProfileNom: '',
    newProfileDescription: '',
    surveyId: '',
    delimiter: ';',
    encoding: 'UTF-8',
    hasHeader: true,
    duplicateStrategy: 'IGNORER' as SatisfactionImportDuplicateStrategy,
  });

  // Step 2 — fichier ---------------------------------------------------------
  readonly selectedFile = signal<File | null>(null);
  readonly csvColumns = signal<string[]>([]);
  readonly csvPreviewRows = signal<string[][]>([]);
  readonly fileError = signal<string | null>(null);
  readonly fileReading = signal(false);

  // Step 3 — mapping -----------------------------------------------------------
  readonly mappingRows = signal<MappingRow[]>([]);
  readonly mappingStepError = signal<string | null>(null);

  // Step 4 — validation / résultat --------------------------------------------
  readonly processing = signal(false);
  readonly processingStatusText = signal('');
  readonly processError = signal<string | null>(null);
  readonly importResult = signal<SatisfactionImportResponse | null>(null);
  readonly importReport = signal<SatisfactionImportReportResponse | null>(null);
  readonly importErrors = signal<SatisfactionImportErrorResponse[]>([]);
  readonly errorsLoading = signal(false);

  readonly mappedRowsCount = computed(() => this.mappingRows().filter((r) => r.targetField).length);
  readonly canRetryResult = computed(() => {
    const r = this.importResult();
    return r ? r.statut === 'FAILED' || r.statut === 'PARTIAL_SUCCESS' || r.statut === 'CANCELLED' : false;
  });
  readonly isResultProcessing = computed(() => {
    const r = this.importResult();
    return r ? r.statut === 'PENDING' || r.statut === 'RUNNING' : false;
  });

  // Historique -----------------------------------------------------------------
  readonly history = signal<SatisfactionImportResponse[]>([]);
  readonly historyLoading = signal(true);
  readonly historyError = signal<string | null>(null);

  readonly historyDetailVisible = signal(false);
  readonly selectedHistoryImport = signal<SatisfactionImportResponse | null>(null);
  readonly historyReport = signal<SatisfactionImportReportResponse | null>(null);
  readonly historyErrors = signal<SatisfactionImportErrorResponse[]>([]);
  readonly historyDetailLoading = signal(false);

  readonly deleteTarget = signal<SatisfactionImportResponse | null>(null);
  readonly deleting = signal(false);

  constructor() {
    this.fetchProfiles();
    this.fetchHistory();
  }

  private fetchProfiles(): void {
    this.profilesLoading.set(true);
    this.satisfactionImportService.listProfiles().subscribe({
      next: (profiles) => {
        this.profiles.set(profiles);
        this.profilesLoading.set(false);
      },
      error: () => this.profilesLoading.set(false),
    });
  }

  fetchHistory(): void {
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.satisfactionImportService.listAll().subscribe({
      next: (page) => {
        this.history.set(page.content);
        this.historyLoading.set(false);
      },
      error: () => {
        this.historyError.set('Impossible de charger l’historique des imports.');
        this.historyLoading.set(false);
      },
    });
  }

  // Step navigation --------------------------------------------------------

  setProfileMode(mode: 'existing' | 'new'): void {
    this.profileMode.set(mode);
    this.sourceStepError.set(null);
  }

  selectSourcePreset(label: string): void {
    this.sourceForm.controls.sourceLabel.setValue(label);
  }

  onProfileSelected(value: string): void {
    this.sourceForm.controls.profileId.setValue(value);
    const id = Number(value);
    if (!id) {
      this.existingMappings.set([]);
      return;
    }
    this.satisfactionImportService.listMappings(id).subscribe({ next: (m) => this.existingMappings.set(m) });
  }

  private validateSourceStep(): boolean {
    const raw = this.sourceForm.getRawValue();
    if (this.profileMode() === 'existing') {
      if (!raw.profileId) {
        this.sourceStepError.set('Sélectionnez un profil de mapping.');
        return false;
      }
    } else if (!raw.newProfileCode.trim() || !raw.newProfileNom.trim()) {
      this.sourceStepError.set('Le code et le nom du nouveau profil sont requis.');
      return false;
    }
    this.sourceStepError.set(null);
    return true;
  }

  private validateFileStep(): boolean {
    if (!this.selectedFile()) {
      this.fileError.set('Sélectionnez un fichier CSV.');
      return false;
    }
    if (this.csvColumns().length === 0) {
      this.fileError.set('Aucune colonne détectée dans ce fichier.');
      return false;
    }
    return true;
  }

  private validateMappingStep(): boolean {
    if (this.mappedRowsCount() === 0) {
      this.mappingStepError.set('Associez au moins une colonne à un champ Assur Zen.');
      return false;
    }
    this.mappingStepError.set(null);
    return true;
  }

  nextStep(): void {
    if (this.currentStep() === 0 && !this.validateSourceStep()) return;
    if (this.currentStep() === 1 && !this.validateFileStep()) return;
    if (this.currentStep() === 2 && !this.validateMappingStep()) return;
    if (this.currentStep() === 1) this.buildMappingRows();

    const next = Math.min(this.currentStep() + 1, this.steps.length - 1);
    this.currentStep.set(next);
    this.furthestStep.set(Math.max(this.furthestStep(), next));
  }

  prevStep(): void {
    this.currentStep.set(Math.max(this.currentStep() - 1, 0));
  }

  goToStep(index: number): void {
    if (index > this.furthestStep()) return;
    this.currentStep.set(index);
  }

  // Step 2 — file parsing ----------------------------------------------------

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.selectedFile.set(file);
    this.parseCsvFile(file);
  }

  private parseCsvFile(file: File): void {
    this.fileReading.set(true);
    this.fileError.set(null);
    const reader = new FileReader();
    const delimiter = this.sourceForm.controls.delimiter.value || ';';
    const hasHeader = this.sourceForm.controls.hasHeader.value;

    reader.onload = () => {
      const text = String(reader.result ?? '');
      const lines = text.split(/\r\n|\n|\r/).filter((l) => l.trim().length > 0);
      if (lines.length === 0) {
        this.fileError.set('Le fichier est vide.');
        this.fileReading.set(false);
        return;
      }
      const splitLine = (line: string) => line.split(delimiter).map((c) => c.trim().replace(/^"|"$/g, ''));
      if (hasHeader) {
        this.csvColumns.set(splitLine(lines[0]));
        this.csvPreviewRows.set(lines.slice(1, 4).map(splitLine));
      } else {
        const colCount = splitLine(lines[0]).length;
        this.csvColumns.set(Array.from({ length: colCount }, (_, i) => `Colonne ${i + 1}`));
        this.csvPreviewRows.set(lines.slice(0, 3).map(splitLine));
      }
      this.fileReading.set(false);
    };
    reader.onerror = () => {
      this.fileError.set('Impossible de lire ce fichier.');
      this.fileReading.set(false);
    };
    reader.readAsText(file, this.sourceForm.controls.encoding.value || 'UTF-8');
  }

  // Step 3 — mapping ----------------------------------------------------------

  private guessTargetField(column: string): SatisfactionImportTargetField | '' {
    const c = column.toLowerCase();
    if (/horodat|date/.test(c)) return 'RESPONSE_DATE';
    if (/e-?mail/.test(c)) return 'EMAIL';
    if (/t[ée]l|phone|mobile/.test(c)) return 'PHONE';
    if (/contrat|police|policy/.test(c)) return 'POLICY_NUMBER';
    if (/note|score|nps/.test(c)) return 'SCORE';
    if (/comment|remarque|avis/.test(c)) return 'COMMENT';
    if (/nom|name|client/.test(c)) return 'CUSTOMER_NAME';
    return '';
  }

  private buildMappingRows(): void {
    const existing = this.existingMappings();
    const rows: MappingRow[] = this.csvColumns().map((col) => {
      const match = existing.find((m) => m.csvColumnName.trim().toLowerCase() === col.trim().toLowerCase());
      return {
        csvColumnName: col,
        targetField: match?.targetField ?? this.guessTargetField(col),
        obligatoire: match?.obligatoire ?? false,
        existingMappingId: match?.id ?? null,
        valueMappings: [],
      };
    });
    this.mappingRows.set(rows);

    const profileId = Number(this.sourceForm.controls.profileId.value);
    if (!profileId) return;
    rows.forEach((row, index) => {
      if (row.existingMappingId && row.targetField === 'SCORE') {
        this.satisfactionImportService.listMappingValues(profileId, row.existingMappingId).subscribe({
          next: (values) => {
            const current = [...this.mappingRows()];
            current[index] = {
              ...current[index],
              valueMappings: values.map((v) => ({ valeurSource: v.valeurSource, valeurCible: v.valeurCible, existing: true })),
            };
            this.mappingRows.set(current);
          },
        });
      }
    });
  }

  setRowTargetField(index: number, value: string): void {
    const rows = [...this.mappingRows()];
    rows[index] = { ...rows[index], targetField: value as SatisfactionImportTargetField | '' };
    this.mappingRows.set(rows);
  }

  toggleRowObligatoire(index: number, checked: boolean): void {
    const rows = [...this.mappingRows()];
    rows[index] = { ...rows[index], obligatoire: checked };
    this.mappingRows.set(rows);
  }

  addValueMapping(index: number): void {
    const rows = [...this.mappingRows()];
    rows[index] = { ...rows[index], valueMappings: [...rows[index].valueMappings, { valeurSource: '', valeurCible: '', existing: false }] };
    this.mappingRows.set(rows);
  }

  removeValueMapping(index: number, vIndex: number): void {
    const rows = [...this.mappingRows()];
    rows[index] = { ...rows[index], valueMappings: rows[index].valueMappings.filter((_, i) => i !== vIndex) };
    this.mappingRows.set(rows);
  }

  setValueMappingField(index: number, vIndex: number, field: 'valeurSource' | 'valeurCible', value: string): void {
    const rows = [...this.mappingRows()];
    const vms = [...rows[index].valueMappings];
    vms[vIndex] = { ...vms[vIndex], [field]: value };
    rows[index] = { ...rows[index], valueMappings: vms };
    this.mappingRows.set(rows);
  }

  // Step 4 — run the import -----------------------------------------------------

  runImport(): void {
    const file = this.selectedFile();
    if (!file) return;

    this.processing.set(true);
    this.processError.set(null);
    this.importResult.set(null);
    this.importReport.set(null);
    this.importErrors.set([]);
    this.processingStatusText.set('Préparation du profil de mapping…');

    const raw = this.sourceForm.getRawValue();
    const profile$: Observable<{ id: number }> =
      this.profileMode() === 'new'
        ? this.satisfactionImportService.createProfile({
            code: raw.newProfileCode.trim(),
            nom: raw.newProfileNom.trim(),
            description: raw.newProfileDescription.trim() || undefined,
          })
        : of({ id: Number(raw.profileId) });

    profile$
      .pipe(
        switchMap((profile) => {
          const profileId = profile.id;
          this.processingStatusText.set('Enregistrement de la correspondance des colonnes…');
          const rows = this.mappingRows().filter((r) => r.targetField);
          const mappingRequests = rows.map((row) => {
            const payload: SatisfactionImportMappingRequest = {
              csvColumnName: row.csvColumnName,
              targetField: row.targetField as SatisfactionImportTargetField,
              obligatoire: row.obligatoire,
            };
            const saved$ = row.existingMappingId
              ? this.satisfactionImportService.updateMapping(profileId, row.existingMappingId, payload)
              : this.satisfactionImportService.addMapping(profileId, payload);
            return saved$.pipe(map((saved) => ({ row, saved })));
          });
          return (mappingRequests.length ? forkJoin(mappingRequests) : of([])).pipe(map((saved) => ({ profileId, saved })));
        }),
        switchMap(({ profileId, saved }) => {
          const valueRequests: Observable<unknown>[] = [];
          for (const { row, saved: mapping } of saved) {
            for (const vm of row.valueMappings) {
              if (!vm.existing && vm.valeurSource.trim() && vm.valeurCible.trim()) {
                valueRequests.push(
                  this.satisfactionImportService.addMappingValue(profileId, mapping.id, {
                    valeurSource: vm.valeurSource.trim(),
                    valeurCible: vm.valeurCible.trim(),
                  })
                );
              }
            }
          }
          this.processingStatusText.set('Envoi et traitement du fichier…');
          return (valueRequests.length ? forkJoin(valueRequests) : of([])).pipe(
            switchMap(() =>
              this.satisfactionImportService.upload(file, {
                mappingProfileId: profileId,
                surveyId: raw.surveyId.trim() || undefined,
                source: raw.sourceLabel,
                delimiter: raw.delimiter,
                encoding: raw.encoding,
                hasHeader: raw.hasHeader,
                duplicateStrategy: raw.duplicateStrategy,
                executeImmediately: true,
              })
            )
          );
        })
      )
      .subscribe({
        next: (result) => {
          this.importResult.set(result);
          this.processingStatusText.set('');
          this.processing.set(false);
          if (!this.isProcessingStatut(result.statut)) {
            this.loadReportAndErrors(result.id, result.lignesEnErreur, this.importReport, this.importErrors);
          }
          this.fetchHistory();
          this.fetchProfiles();
        },
        error: () => {
          this.processing.set(false);
          this.processingStatusText.set('');
          this.processError.set("L'import a échoué. Vérifiez le fichier et la correspondance des colonnes, puis réessayez.");
        },
      });
  }

  private loadReportAndErrors(
    importId: number,
    lignesEnErreur: number,
    reportSignal: ReturnType<typeof signal<SatisfactionImportReportResponse | null>>,
    errorsSignal: ReturnType<typeof signal<SatisfactionImportErrorResponse[]>>
  ): void {
    this.satisfactionImportService.getReport(importId).subscribe({ next: (r) => reportSignal.set(r) });
    if (lignesEnErreur > 0) {
      this.errorsLoading.set(true);
      this.satisfactionImportService.getErrors(importId).subscribe({
        next: (errs) => {
          errorsSignal.set(errs);
          this.errorsLoading.set(false);
        },
        error: () => this.errorsLoading.set(false),
      });
    }
  }

  refreshImportStatus(): void {
    const result = this.importResult();
    if (!result) return;
    this.satisfactionImportService.get(result.id).subscribe({
      next: (updated) => {
        this.importResult.set(updated);
        if (updated.statut !== 'PENDING' && updated.statut !== 'RUNNING') {
          this.loadReportAndErrors(updated.id, updated.lignesEnErreur, this.importReport, this.importErrors);
        }
        this.fetchHistory();
      },
    });
  }

  isProcessingStatut(statut: SatisfactionImportStatut): boolean {
    return statut === 'PENDING' || statut === 'RUNNING';
  }

  retryCurrentResult(): void {
    const result = this.importResult();
    if (!result) return;
    this.processing.set(true);
    this.processingStatusText.set('Relance du traitement…');
    this.satisfactionImportService.retry(result.id).subscribe({
      next: (updated) => {
        this.importResult.set(updated);
        this.processing.set(false);
        this.processingStatusText.set('');
        this.loadReportAndErrors(updated.id, updated.lignesEnErreur, this.importReport, this.importErrors);
        this.fetchHistory();
      },
      error: () => {
        this.processing.set(false);
        this.processingStatusText.set('');
        this.processError.set('Impossible de relancer cet import.');
      },
    });
  }

  resetWizard(): void {
    this.currentStep.set(0);
    this.furthestStep.set(0);
    this.profileMode.set('existing');
    this.sourceForm.reset({
      sourceLabel: 'CSV',
      profileId: '',
      newProfileCode: '',
      newProfileNom: '',
      newProfileDescription: '',
      surveyId: '',
      delimiter: ';',
      encoding: 'UTF-8',
      hasHeader: true,
      duplicateStrategy: 'IGNORER',
    });
    this.selectedFile.set(null);
    this.csvColumns.set([]);
    this.csvPreviewRows.set([]);
    this.fileError.set(null);
    this.mappingRows.set([]);
    this.existingMappings.set([]);
    this.mappingStepError.set(null);
    this.importResult.set(null);
    this.importReport.set(null);
    this.importErrors.set([]);
    this.processError.set(null);
  }

  // Historique --------------------------------------------------------------

  openHistoryDetail(imp: SatisfactionImportResponse): void {
    this.selectedHistoryImport.set(imp);
    this.historyDetailVisible.set(true);
    this.historyReport.set(null);
    this.historyErrors.set([]);
    if (this.isProcessingStatut(imp.statut)) return;
    this.historyDetailLoading.set(true);
    this.satisfactionImportService.getReport(imp.id).subscribe({
      next: (r) => {
        this.historyReport.set(r);
        this.historyDetailLoading.set(false);
      },
      error: () => this.historyDetailLoading.set(false),
    });
    if (imp.lignesEnErreur > 0) {
      this.satisfactionImportService.getErrors(imp.id).subscribe({ next: (e) => this.historyErrors.set(e) });
    }
  }

  refreshHistoryStatus(): void {
    const imp = this.selectedHistoryImport();
    if (!imp) return;
    this.historyDetailLoading.set(true);
    this.satisfactionImportService.get(imp.id).subscribe({
      next: (updated) => {
        this.selectedHistoryImport.set(updated);
        this.historyDetailLoading.set(false);
        if (updated.statut !== 'PENDING' && updated.statut !== 'RUNNING') {
          this.loadReportAndErrors(updated.id, updated.lignesEnErreur, this.historyReport, this.historyErrors);
        }
        this.fetchHistory();
      },
      error: () => this.historyDetailLoading.set(false),
    });
  }

  retryHistoryImport(): void {
    const imp = this.selectedHistoryImport();
    if (!imp) return;
    this.historyDetailLoading.set(true);
    this.satisfactionImportService.retry(imp.id).subscribe({
      next: (updated) => {
        this.selectedHistoryImport.set(updated);
        this.historyDetailLoading.set(false);
        this.loadReportAndErrors(updated.id, updated.lignesEnErreur, this.historyReport, this.historyErrors);
        this.fetchHistory();
      },
      error: () => this.historyDetailLoading.set(false),
    });
  }

  askDeleteHistory(imp: SatisfactionImportResponse): void {
    this.deleteTarget.set(imp);
  }

  cancelDeleteHistory(): void {
    this.deleteTarget.set(null);
  }

  confirmDeleteHistory(): void {
    const imp = this.deleteTarget();
    if (!imp) return;
    this.deleting.set(true);
    this.satisfactionImportService.delete(imp.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.historyDetailVisible.set(false);
        this.fetchHistory();
      },
      error: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        this.historyError.set('Impossible de supprimer cet import.');
      },
    });
  }

  canRetry(imp: SatisfactionImportResponse): boolean {
    return imp.statut === 'FAILED' || imp.statut === 'PARTIAL_SUCCESS' || imp.statut === 'CANCELLED';
  }

  selectedProfileLabel(): string {
    if (this.profileMode() === 'new') return this.sourceForm.controls.newProfileNom.value || '—';
    const id = Number(this.sourceForm.controls.profileId.value);
    return this.profiles().find((p) => p.id === id)?.nom ?? '—';
  }

  targetFieldLabel(field: SatisfactionImportTargetField): string {
    return TARGET_FIELD_LABEL[field];
  }

  statutLabel(statut: SatisfactionImportStatut): string {
    return STATUT_LABEL[statut];
  }

  statutVariant(statut: SatisfactionImportStatut): string {
    return STATUT_VARIANT[statut];
  }

  errorTypeLabel(type: SatisfactionImportErrorType): string {
    return ERROR_TYPE_LABEL[type];
  }

  formatPercent(value: number | null): string {
    return value == null ? '—' : `${Math.round(value)} %`;
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }
}
