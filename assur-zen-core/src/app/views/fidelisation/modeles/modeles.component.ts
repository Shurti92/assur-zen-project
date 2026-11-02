import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  ColComponent,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  RowComponent,
  SpinnerComponent,
} from '@coreui/angular';

import { TemplateService } from '../../../core/templates/template.service';
import {
  CommunicationTemplateRequest,
  CommunicationTemplateResponse,
  TemplateCanal,
  TemplateCategoryResponse,
  TemplatePreviewResponse,
  TemplateStatut,
  TemplateVariableResponse,
  TemplateVariableType,
} from '../../../core/templates/models/template.model';
import { LangueService } from '../../../core/langues/langue.service';
import { LangueResponse } from '../../../core/langues/models/langue.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type StatutFilter = 'all' | TemplateStatut;
type PendingAction = 'enable' | 'disable' | 'delete' | null;

const CANAL_LABEL: Record<TemplateCanal, string> = {
  SMS: 'SMS',
  EMAIL: 'Email',
  WHATSAPP: 'WhatsApp',
  PUSH: 'Notification push',
  PDF: 'PDF',
};

const STATUT_LABEL: Record<TemplateStatut, string> = { BROUILLON: 'Brouillon', ACTIF: 'Actif', ARCHIVE: 'Archivé' };
const STATUT_VARIANT: Record<TemplateStatut, string> = { BROUILLON: 'neutral', ACTIF: 'success', ARCHIVE: 'danger' };
const STATUT_ORDER: TemplateStatut[] = ['BROUILLON', 'ACTIF', 'ARCHIVE'];

const EMPTY_FORM = {
  code: '',
  nom: '',
  description: '',
  canal: 'SMS' as TemplateCanal,
  categorieId: '',
  langueId: '',
  objet: '',
  contenu: '',
  maxCaracteres: '',
  variableCodes: [] as string[],
  active: true,
};

const EMPTY_CATEGORY_FORM = { code: '', libelle: '', description: '' };
const EMPTY_VARIABLE_FORM = { code: '', libelle: '', description: '', typeDonnee: 'TEXTE' as TemplateVariableType };

@Component({
  selector: 'app-modeles',
  templateUrl: './modeles.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    RowComponent,
    ColComponent,
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
export class ModelesComponent {
  private readonly templateService = inject(TemplateService);
  private readonly langueService = inject(LangueService);
  private readonly fb = inject(FormBuilder);

  readonly statutOrder = STATUT_ORDER;

  readonly templates = signal<CommunicationTemplateResponse[]>([]);
  readonly categories = signal<TemplateCategoryResponse[]>([]);
  readonly variables = signal<TemplateVariableResponse[]>([]);
  readonly langues = signal<LangueResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly statutFilter = signal<StatutFilter>('all');

  readonly formVisible = signal(false);
  readonly formMode = signal<'create' | 'edit'>('create');
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({
    code: EMPTY_FORM.code,
    nom: EMPTY_FORM.nom,
    description: EMPTY_FORM.description,
    canal: EMPTY_FORM.canal,
    categorieId: EMPTY_FORM.categorieId,
    langueId: EMPTY_FORM.langueId,
    objet: EMPTY_FORM.objet,
    contenu: EMPTY_FORM.contenu,
    maxCaracteres: EMPTY_FORM.maxCaracteres,
    variableCodes: this.fb.nonNullable.control<string[]>(EMPTY_FORM.variableCodes),
    active: EMPTY_FORM.active,
  });
  private readonly canalValue = toSignal(this.form.controls.canal.valueChanges, { initialValue: this.form.controls.canal.value });
  readonly showObjet = computed(() => this.canalValue() !== 'SMS');

  readonly detailVisible = signal(false);
  readonly selectedTemplate = signal<CommunicationTemplateResponse | null>(null);
  readonly previewValues = signal<Record<string, string>>({});
  readonly previewResult = signal<TemplatePreviewResponse | null>(null);
  readonly previewLoading = signal(false);
  readonly previewError = signal<string | null>(null);

  readonly pendingAction = signal<PendingAction>(null);
  readonly actionSaving = signal(false);

  readonly refModalVisible = signal(false);
  readonly categoryForm = this.fb.nonNullable.group({ ...EMPTY_CATEGORY_FORM });
  readonly categorySaving = signal(false);
  readonly categoryError = signal<string | null>(null);
  readonly variableForm = this.fb.nonNullable.group({ ...EMPTY_VARIABLE_FORM });
  readonly variableSaving = signal(false);
  readonly variableError = signal<string | null>(null);

  readonly filteredTemplates = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const filter = this.statutFilter();
    let list = this.templates();
    if (filter !== 'all') list = list.filter((t) => t.statut === filter);
    if (!query) return list;
    return list.filter((t) => `${t.code} ${t.nom} ${t.description ?? ''}`.toLowerCase().includes(query));
  });

  readonly statTotal = computed(() => this.templates().length);
  readonly statActifs = computed(() => this.templates().filter((t) => t.statut === 'ACTIF').length);
  readonly statBrouillons = computed(() => this.templates().filter((t) => t.statut === 'BROUILLON').length);
  readonly statArchives = computed(() => this.templates().filter((t) => t.statut === 'ARCHIVE').length);

  readonly selectedVariables = computed(() => {
    const template = this.selectedTemplate();
    if (!template) return [];
    const catalog = this.variables();
    return template.variableCodes.map((code) => catalog.find((v) => v.code === code) ?? { code, libelle: code });
  });

  readonly pendingActionTitle = computed(() => {
    const action = this.pendingAction();
    if (action === 'enable') return 'Activer le modèle';
    if (action === 'disable') return 'Archiver le modèle';
    return 'Supprimer le modèle';
  });

  readonly pendingActionMessage = computed(() => {
    const template = this.selectedTemplate();
    const action = this.pendingAction();
    if (!template || !action) return '';
    if (action === 'enable') return `Activer le modèle « ${template.nom} » ? Il pourra être utilisé dans des campagnes.`;
    if (action === 'disable') return `Archiver le modèle « ${template.nom} » ?`;
    return `Voulez-vous vraiment supprimer le modèle « ${template.nom} » ? Cette action est irréversible.`;
  });

  constructor() {
    this.fetch();
    this.fetchReferences();
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.templateService.listAll().subscribe({
      next: (page) => {
        this.templates.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les modèles.');
        this.loading.set(false);
      },
    });
  }

  private fetchReferences(): void {
    this.templateService.listCategories().subscribe({ next: (list) => this.categories.set(list) });
    this.templateService.listVariables().subscribe({ next: (list) => this.variables.set(list) });
    this.langueService.list().subscribe({ next: (list) => this.langues.set(list) });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  setStatutFilter(filter: StatutFilter): void {
    this.statutFilter.set(filter);
  }

  openCreate(): void {
    this.formMode.set('create');
    this.editingId.set(null);
    this.form.reset({ ...EMPTY_FORM });
    this.formError.set(null);
    this.formVisible.set(true);
  }

  openEdit(template: CommunicationTemplateResponse): void {
    this.formMode.set('edit');
    this.editingId.set(template.id);
    this.form.reset({
      code: template.code,
      nom: template.nom,
      description: template.description ?? '',
      canal: template.canal,
      categorieId: String(template.categorieId),
      langueId: String(template.langueId),
      objet: template.objet ?? '',
      contenu: template.contenu,
      maxCaracteres: template.maxCaracteres != null ? String(template.maxCaracteres) : '',
      variableCodes: template.variableCodes,
      active: template.active,
    });
    this.formError.set(null);
    this.formVisible.set(true);
  }

  closeForm(): void {
    this.formVisible.set(false);
  }

  toggleVariable(code: string, checked: boolean): void {
    const current = this.form.controls.variableCodes.value;
    this.form.controls.variableCodes.setValue(checked ? [...current, code] : current.filter((c) => c !== code));
  }

  submitForm(): void {
    const raw = this.form.getRawValue();
    if (!raw.code.trim() || !raw.nom.trim()) {
      this.formError.set('Le code et le nom sont requis.');
      return;
    }
    if (!raw.categorieId || !raw.langueId) {
      this.formError.set('La catégorie et la langue sont requises.');
      return;
    }
    if (!raw.contenu.trim()) {
      this.formError.set('Le contenu du modèle est requis.');
      return;
    }

    const payload: CommunicationTemplateRequest = {
      code: raw.code.trim(),
      nom: raw.nom.trim(),
      description: raw.description.trim() || undefined,
      canal: raw.canal,
      categorieId: Number(raw.categorieId),
      langueId: Number(raw.langueId),
      objet: raw.objet.trim() || undefined,
      contenu: raw.contenu.trim(),
      maxCaracteres: raw.maxCaracteres ? Number(raw.maxCaracteres) : undefined,
      variableCodes: raw.variableCodes,
      active: raw.active,
    };

    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request$ = id ? this.templateService.update(id, payload) : this.templateService.create(payload);
    request$.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set("Impossible d'enregistrer ce modèle.");
      },
    });
  }

  openDetail(template: CommunicationTemplateResponse): void {
    this.selectedTemplate.set(template);
    this.previewValues.set({});
    this.previewResult.set(null);
    this.previewError.set(null);
    this.detailVisible.set(true);
  }

  setPreviewValue(code: string, value: string): void {
    this.previewValues.set({ ...this.previewValues(), [code]: value });
  }

  generatePreview(): void {
    const template = this.selectedTemplate();
    if (!template) return;
    this.previewLoading.set(true);
    this.previewError.set(null);
    this.templateService.apercu(template.id, { valeurs: this.previewValues() }).subscribe({
      next: (result) => {
        this.previewResult.set(result);
        this.previewLoading.set(false);
      },
      error: () => {
        this.previewError.set("Impossible de générer l'aperçu.");
        this.previewLoading.set(false);
      },
    });
  }

  dupliquer(template: CommunicationTemplateResponse): void {
    this.templateService.dupliquer(template.id).subscribe({
      next: () => {
        this.detailVisible.set(false);
        this.fetch();
      },
      error: () => this.error.set('Impossible de dupliquer ce modèle.'),
    });
  }

  askEnable(template: CommunicationTemplateResponse): void {
    this.selectedTemplate.set(template);
    this.pendingAction.set('enable');
  }

  askDisable(template: CommunicationTemplateResponse): void {
    this.selectedTemplate.set(template);
    this.pendingAction.set('disable');
  }

  askDelete(template: CommunicationTemplateResponse): void {
    this.selectedTemplate.set(template);
    this.pendingAction.set('delete');
  }

  cancelPendingAction(): void {
    this.pendingAction.set(null);
  }

  confirmPendingAction(): void {
    const template = this.selectedTemplate();
    const action = this.pendingAction();
    if (!template || !action) return;

    this.actionSaving.set(true);
    const request$: Observable<unknown> =
      action === 'enable'
        ? this.templateService.enable(template.id)
        : action === 'disable'
          ? this.templateService.disable(template.id)
          : this.templateService.delete(template.id);

    request$.subscribe({
      next: () => {
        this.actionSaving.set(false);
        this.pendingAction.set(null);
        this.detailVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.actionSaving.set(false);
        this.pendingAction.set(null);
        this.error.set("Impossible d'effectuer cette action sur le modèle.");
      },
    });
  }

  openRefModal(): void {
    this.categoryForm.reset({ ...EMPTY_CATEGORY_FORM });
    this.variableForm.reset({ ...EMPTY_VARIABLE_FORM });
    this.categoryError.set(null);
    this.variableError.set(null);
    this.refModalVisible.set(true);
  }

  closeRefModal(): void {
    this.refModalVisible.set(false);
  }

  submitCategory(): void {
    const raw = this.categoryForm.getRawValue();
    if (!raw.code.trim() || !raw.libelle.trim()) {
      this.categoryError.set('Le code et le libellé sont requis.');
      return;
    }
    this.categorySaving.set(true);
    this.categoryError.set(null);
    this.templateService
      .createCategory({ code: raw.code.trim(), libelle: raw.libelle.trim(), description: raw.description.trim() || undefined })
      .subscribe({
        next: () => {
          this.categorySaving.set(false);
          this.categoryForm.reset({ ...EMPTY_CATEGORY_FORM });
          this.fetchReferences();
        },
        error: () => {
          this.categorySaving.set(false);
          this.categoryError.set("Impossible de créer cette catégorie.");
        },
      });
  }

  submitVariable(): void {
    const raw = this.variableForm.getRawValue();
    if (!raw.code.trim() || !raw.libelle.trim()) {
      this.variableError.set('Le code et le libellé sont requis.');
      return;
    }
    this.variableSaving.set(true);
    this.variableError.set(null);
    this.templateService
      .createVariable({
        code: raw.code.trim(),
        libelle: raw.libelle.trim(),
        description: raw.description.trim() || undefined,
        typeDonnee: raw.typeDonnee,
      })
      .subscribe({
        next: () => {
          this.variableSaving.set(false);
          this.variableForm.reset({ ...EMPTY_VARIABLE_FORM });
          this.fetchReferences();
        },
        error: () => {
          this.variableSaving.set(false);
          this.variableError.set('Impossible de créer cette variable.');
        },
      });
  }

  canEdit(t: CommunicationTemplateResponse): boolean {
    return t.statut !== 'ARCHIVE';
  }

  canDelete(t: CommunicationTemplateResponse): boolean {
    return t.statut === 'BROUILLON';
  }

  canEnable(t: CommunicationTemplateResponse): boolean {
    return t.statut === 'BROUILLON' || t.statut === 'ARCHIVE';
  }

  canDisable(t: CommunicationTemplateResponse): boolean {
    return t.statut === 'ACTIF';
  }

  canalLabel(canal: TemplateCanal): string {
    return CANAL_LABEL[canal];
  }

  statutLabel(statut: TemplateStatut): string {
    return STATUT_LABEL[statut];
  }

  statutVariant(statut: TemplateStatut): string {
    return STATUT_VARIANT[statut];
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }
}
