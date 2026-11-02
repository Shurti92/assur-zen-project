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

import { SegmentService } from '../../../core/segments/segment.service';
import {
  SegmentMembreResponse,
  SegmentRequest,
  SegmentResponse,
  SegmentRuleChamp,
  SegmentRuleOperateur,
  SegmentRuleOperateurLogique,
  SegmentRuleRequest,
  SegmentRuleResponse,
  SegmentType,
} from '../../../core/segments/models/segment.model';
import {
  SEGMENT_CHAMP_LABEL,
  SEGMENT_CHAMP_ORDER,
  SEGMENT_OPERATEUR_LABEL,
  SEGMENT_OPERATEUR_ORDER,
  SEGMENT_TYPE_LABEL,
  SEGMENT_TYPE_ORDER,
} from '../../../core/segments/segment-labels';
import { InsuredService } from '../../../core/insured/insured.service';
import { InsuredResponse } from '../../../core/insured/models/insured.model';
import { AlertMessageComponent } from '../../../shared/alert-message/alert-message.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { ModalComponent } from '../../../shared/modal/modal.component';

type TypeFilter = 'all' | SegmentType;
type PendingAction = 'enable' | 'disable' | 'delete' | null;

const EMPTY_FORM = {
  code: '',
  nom: '',
  description: '',
  type: 'STATIC' as SegmentType,
  priorite: '',
  couleur: '#1b3b8f',
  active: true,
};

const EMPTY_RULE_FORM = {
  champ: 'PRODUIT' as SegmentRuleChamp,
  operateur: 'EQUALS' as SegmentRuleOperateur,
  valeur: '',
  groupe: '1',
  operateurLogique: 'AND' as SegmentRuleOperateurLogique,
};

@Component({
  selector: 'app-segments',
  templateUrl: './segments.component.html',
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
export class SegmentsComponent {
  private readonly segmentService = inject(SegmentService);
  private readonly insuredService = inject(InsuredService);
  private readonly fb = inject(FormBuilder);

  readonly typeOrder = SEGMENT_TYPE_ORDER;
  readonly champOrder = SEGMENT_CHAMP_ORDER;
  readonly operateurOrder = SEGMENT_OPERATEUR_ORDER;
  readonly typeLabelMap = SEGMENT_TYPE_LABEL;
  readonly champLabelMap = SEGMENT_CHAMP_LABEL;
  readonly operateurLabelMap = SEGMENT_OPERATEUR_LABEL;

  readonly segments = signal<SegmentResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly typeFilter = signal<TypeFilter>('all');

  readonly formVisible = signal(false);
  readonly formMode = signal<'create' | 'edit'>('create');
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({ ...EMPTY_FORM });

  readonly detailVisible = signal(false);
  readonly selectedSegment = signal<SegmentResponse | null>(null);
  readonly isDynamic = computed(() => this.selectedSegment()?.type === 'DYNAMIC');

  readonly rules = signal<SegmentRuleResponse[]>([]);
  readonly rulesLoading = signal(false);
  readonly ruleForm = this.fb.nonNullable.group({ ...EMPTY_RULE_FORM });
  readonly editingRuleId = signal<number | null>(null);
  readonly ruleSaving = signal(false);
  readonly ruleError = signal<string | null>(null);
  private readonly ruleOperateurValue = toSignal(this.ruleForm.controls.operateur.valueChanges, {
    initialValue: this.ruleForm.controls.operateur.value,
  });
  readonly ruleNeedsValeur = computed(() => this.ruleOperateurValue() !== 'IS_NULL' && this.ruleOperateurValue() !== 'IS_NOT_NULL');

  readonly membres = signal<SegmentMembreResponse[]>([]);
  readonly membresLoading = signal(false);
  readonly membreToAdd = signal('');
  readonly membreSaving = signal(false);
  readonly membreError = signal<string | null>(null);
  readonly allInsureds = signal<InsuredResponse[]>([]);
  readonly availableInsureds = computed(() => {
    const memberIds = new Set(this.membres().map((m) => m.insuredId));
    return this.allInsureds().filter((i) => !memberIds.has(i.id));
  });

  readonly resolvePreview = signal<InsuredResponse[] | null>(null);
  readonly resolveLoading = signal(false);
  readonly resolveError = signal<string | null>(null);

  readonly pendingAction = signal<PendingAction>(null);
  readonly actionSaving = signal(false);

  readonly filteredSegments = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const filter = this.typeFilter();
    let list = this.segments();
    if (filter !== 'all') list = list.filter((s) => s.type === filter);
    if (!query) return list;
    return list.filter((s) => `${s.code} ${s.nom} ${s.description ?? ''}`.toLowerCase().includes(query));
  });

  readonly statTotal = computed(() => this.segments().length);
  readonly statStatiques = computed(() => this.segments().filter((s) => s.type === 'STATIC').length);
  readonly statDynamiques = computed(() => this.segments().filter((s) => s.type === 'DYNAMIC').length);
  readonly statActifs = computed(() => this.segments().filter((s) => s.active).length);

  readonly pendingActionTitle = computed(() => {
    const action = this.pendingAction();
    if (action === 'enable') return 'Activer le segment';
    if (action === 'disable') return 'Désactiver le segment';
    return 'Supprimer le segment';
  });

  readonly pendingActionMessage = computed(() => {
    const segment = this.selectedSegment();
    const action = this.pendingAction();
    if (!segment || !action) return '';
    if (action === 'enable') return `Activer le segment « ${segment.nom} » ?`;
    if (action === 'disable') return `Désactiver le segment « ${segment.nom} » ?`;
    return `Voulez-vous vraiment supprimer le segment « ${segment.nom} » ? Cette action est irréversible.`;
  });

  constructor() {
    this.fetch();
    this.insuredService.list(0, 500).subscribe({ next: (page) => this.allInsureds.set(page.content) });
  }

  fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.segmentService.listAll().subscribe({
      next: (page) => {
        this.segments.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les segments.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchQuery.set(value);
  }

  setTypeFilter(filter: TypeFilter): void {
    this.typeFilter.set(filter);
  }

  openCreate(): void {
    this.formMode.set('create');
    this.editingId.set(null);
    this.form.reset({ ...EMPTY_FORM });
    this.formError.set(null);
    this.formVisible.set(true);
  }

  openEdit(segment: SegmentResponse): void {
    this.formMode.set('edit');
    this.editingId.set(segment.id);
    this.form.reset({
      code: segment.code,
      nom: segment.nom,
      description: segment.description ?? '',
      type: segment.type,
      priorite: segment.priorite != null ? String(segment.priorite) : '',
      couleur: segment.couleur ?? '#1b3b8f',
      active: segment.active,
    });
    this.formError.set(null);
    this.formVisible.set(true);
  }

  closeForm(): void {
    this.formVisible.set(false);
  }

  submitForm(): void {
    const raw = this.form.getRawValue();
    if (!raw.code.trim() || !raw.nom.trim()) {
      this.formError.set('Le code et le nom sont requis.');
      return;
    }

    const payload: SegmentRequest = {
      code: raw.code.trim(),
      nom: raw.nom.trim(),
      description: raw.description.trim() || undefined,
      type: raw.type,
      priorite: raw.priorite ? Number(raw.priorite) : undefined,
      couleur: raw.couleur || undefined,
      active: raw.active,
    };

    this.formSaving.set(true);
    this.formError.set(null);
    const id = this.editingId();
    const request$ = id ? this.segmentService.update(id, payload) : this.segmentService.create(payload);
    request$.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetch();
      },
      error: () => {
        this.formSaving.set(false);
        this.formError.set('Impossible d’enregistrer ce segment.');
      },
    });
  }

  openDetail(segment: SegmentResponse): void {
    this.selectedSegment.set(segment);
    this.resolvePreview.set(null);
    this.resolveError.set(null);
    this.detailVisible.set(true);
    if (segment.type === 'DYNAMIC') {
      this.fetchRules(segment.id);
    } else {
      this.fetchMembres(segment.id);
    }
  }

  private fetchRules(segmentId: number): void {
    this.rulesLoading.set(true);
    this.segmentService.listRules(segmentId).subscribe({
      next: (rules) => {
        this.rules.set(rules);
        this.rulesLoading.set(false);
      },
      error: () => this.rulesLoading.set(false),
    });
  }

  openAddRule(): void {
    this.editingRuleId.set(null);
    this.ruleForm.reset({ ...EMPTY_RULE_FORM });
    this.ruleError.set(null);
  }

  openEditRule(rule: SegmentRuleResponse): void {
    this.editingRuleId.set(rule.id);
    this.ruleForm.reset({
      champ: rule.champ,
      operateur: rule.operateur,
      valeur: rule.valeur ?? '',
      groupe: rule.groupe != null ? String(rule.groupe) : '1',
      operateurLogique: rule.operateurLogique ?? 'AND',
    });
    this.ruleError.set(null);
  }

  submitRule(): void {
    const segment = this.selectedSegment();
    if (!segment) return;
    const raw = this.ruleForm.getRawValue();
    if (this.ruleNeedsValeur() && !raw.valeur.trim()) {
      this.ruleError.set('La valeur est requise pour cet opérateur.');
      return;
    }

    const payload: SegmentRuleRequest = {
      champ: raw.champ,
      operateur: raw.operateur,
      valeur: this.ruleNeedsValeur() ? raw.valeur.trim() : undefined,
      groupe: raw.groupe ? Number(raw.groupe) : undefined,
      operateurLogique: raw.operateurLogique,
    };

    this.ruleSaving.set(true);
    this.ruleError.set(null);
    const ruleId = this.editingRuleId();
    const request$ = ruleId
      ? this.segmentService.updateRule(segment.id, ruleId, payload)
      : this.segmentService.addRule(segment.id, payload);
    request$.subscribe({
      next: () => {
        this.ruleSaving.set(false);
        this.openAddRule();
        this.fetchRules(segment.id);
        this.fetch();
      },
      error: () => {
        this.ruleSaving.set(false);
        this.ruleError.set('Impossible d’enregistrer cette règle.');
      },
    });
  }

  deleteRule(rule: SegmentRuleResponse): void {
    const segment = this.selectedSegment();
    if (!segment) return;
    this.segmentService.deleteRule(segment.id, rule.id).subscribe({
      next: () => {
        this.fetchRules(segment.id);
        this.fetch();
      },
      error: () => this.ruleError.set('Impossible de supprimer cette règle.'),
    });
  }

  generateResolvePreview(): void {
    const segment = this.selectedSegment();
    if (!segment) return;
    this.resolveLoading.set(true);
    this.resolveError.set(null);
    this.segmentService.resolve(segment.id).subscribe({
      next: (insureds) => {
        this.resolvePreview.set(insureds);
        this.resolveLoading.set(false);
      },
      error: () => {
        this.resolveError.set("Impossible de calculer l'aperçu des assurés concernés.");
        this.resolveLoading.set(false);
      },
    });
  }

  private fetchMembres(segmentId: number): void {
    this.membresLoading.set(true);
    this.segmentService.listMembres(segmentId).subscribe({
      next: (membres) => {
        this.membres.set(membres);
        this.membresLoading.set(false);
      },
      error: () => this.membresLoading.set(false),
    });
  }

  addMembre(): void {
    const segment = this.selectedSegment();
    const insuredId = Number(this.membreToAdd());
    if (!segment || !insuredId) return;
    const ids = [...this.membres().map((m) => m.insuredId), insuredId];
    this.membreSaving.set(true);
    this.membreError.set(null);
    this.segmentService.replaceMembres(segment.id, { insuredIds: ids }).subscribe({
      next: (membres) => {
        this.membres.set(membres);
        this.membreSaving.set(false);
        this.membreToAdd.set('');
        this.fetch();
      },
      error: () => {
        this.membreSaving.set(false);
        this.membreError.set("Impossible d'ajouter cet assuré au segment.");
      },
    });
  }

  removeMembre(membre: SegmentMembreResponse): void {
    const segment = this.selectedSegment();
    if (!segment) return;
    const ids = this.membres()
      .filter((m) => m.id !== membre.id)
      .map((m) => m.insuredId);
    if (ids.length === 0) {
      this.membreError.set('Un segment statique doit conserver au moins un membre. Ajoutez un remplaçant avant de retirer celui-ci.');
      return;
    }
    this.membreSaving.set(true);
    this.membreError.set(null);
    this.segmentService.replaceMembres(segment.id, { insuredIds: ids }).subscribe({
      next: (membres) => {
        this.membres.set(membres);
        this.membreSaving.set(false);
        this.fetch();
      },
      error: () => {
        this.membreSaving.set(false);
        this.membreError.set('Impossible de retirer cet assuré du segment.');
      },
    });
  }

  askEnable(segment: SegmentResponse): void {
    this.selectedSegment.set(segment);
    this.pendingAction.set('enable');
  }

  askDisable(segment: SegmentResponse): void {
    this.selectedSegment.set(segment);
    this.pendingAction.set('disable');
  }

  askDelete(segment: SegmentResponse): void {
    this.selectedSegment.set(segment);
    this.pendingAction.set('delete');
  }

  cancelPendingAction(): void {
    this.pendingAction.set(null);
  }

  confirmPendingAction(): void {
    const segment = this.selectedSegment();
    const action = this.pendingAction();
    if (!segment || !action) return;

    this.actionSaving.set(true);
    const request$: Observable<unknown> =
      action === 'enable' ? this.segmentService.enable(segment.id) : action === 'disable' ? this.segmentService.disable(segment.id) : this.segmentService.delete(segment.id);

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
        this.error.set("Impossible d'effectuer cette action sur le segment.");
      },
    });
  }

  typeLabel(type: SegmentType): string {
    return SEGMENT_TYPE_LABEL[type];
  }

  champLabel(champ: SegmentRuleChamp): string {
    return SEGMENT_CHAMP_LABEL[champ];
  }

  operateurLabel(operateur: SegmentRuleOperateur): string {
    return SEGMENT_OPERATEUR_LABEL[operateur];
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }
}
