import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  FormCheckInputDirective,
  FormControlDirective,
  FormDirective,
  FormLabelDirective,
  FormSelectDirective,
  SpinnerComponent,
} from '@coreui/angular';

import { ConfigurationService } from '../../core/configurations/configuration.service';
import {
  ConfigurationCategory,
  ConfigurationEnvironment,
  ConfigurationFilter,
  ConfigurationValueType,
  DynamicConfigurationRequest,
  DynamicConfigurationResponse,
} from '../../core/configurations/models/configuration.model';
import { HistoriqueModificationResponse } from '../../core/audit-trail/models/audit-trail.model';
import { ModalComponent } from '../../shared/modal/modal.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { AlertMessageComponent } from '../../shared/alert-message/alert-message.component';
import { ConfigurationValueEditorComponent } from './configuration-value-editor/configuration-value-editor.component';
import {
  CONFIG_CATEGORY_LABEL,
  CONFIG_CATEGORY_ORDER,
  CONFIG_ENVIRONMENT_LABEL,
  CONFIG_ENVIRONMENT_ORDER,
  CONFIG_ENVIRONMENT_VARIANT,
  CONFIG_VALUE_TYPE_LABEL,
  CONFIG_VALUE_TYPE_ORDER,
} from './configuration-status';

type View = 'dashboard' | 'configurations' | 'categories' | 'modules' | 'historique';

/** Valeur renvoyée par le backend à la place d'une valeur sensible ; renvoyée telle quelle, elle
 * conserve la valeur existante (backend du 2026-09-24). */
const SENSITIVE_MASK = '********';

/** Clés que le backend force en sensible à la création (même règle, 2026-09-24). */
const SENSITIVE_KEY_PATTERN = /SECRET|PASSWORD|API_KEY|PRIVATE_KEY|MERCHANT_ID/i;

const TITLES: Record<View, string> = {
  dashboard: 'Tableau de bord',
  configurations: 'Configurations',
  categories: 'Catégories',
  modules: 'Modules',
  historique: 'Historique',
};

const EMPTY_FORM: DynamicConfigurationRequest = {
  configKey: '',
  configValue: '',
  valueType: 'STRING',
  description: '',
  module: '',
  category: undefined,
  environment: undefined,
  defaultValue: '',
  validationRule: '',
  allowedValues: '',
  active: true,
  editable: true,
  sensitive: false,
  restartRequired: false,
  cacheEnabled: true,
  displayOrder: 0,
};

@Component({
  selector: 'app-configuration-dynamique',
  templateUrl: './configuration-dynamique.component.html',
  imports: [
    ButtonDirective,
    SpinnerComponent,
    IconDirective,
    FormsModule,
    ReactiveFormsModule,
    FormDirective,
    FormControlDirective,
    FormSelectDirective,
    FormCheckInputDirective,
    FormLabelDirective,
    ModalComponent,
    ConfirmDialogComponent,
    AlertMessageComponent,
    ConfigurationValueEditorComponent,
  ],
})
export class ConfigurationDynamiqueComponent {
  private readonly configurationService = inject(ConfigurationService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly view = signal<View>((this.route.snapshot.data['mode'] as View) ?? 'dashboard');
  readonly title = computed(() => TITLES[this.view()]);

  readonly valueTypeOrder = CONFIG_VALUE_TYPE_ORDER;
  readonly valueTypeLabel = CONFIG_VALUE_TYPE_LABEL;
  readonly categoryOrder = CONFIG_CATEGORY_ORDER;
  readonly categoryLabel = CONFIG_CATEGORY_LABEL;
  readonly environmentOrder = CONFIG_ENVIRONMENT_ORDER;
  readonly environmentLabel = CONFIG_ENVIRONMENT_LABEL;
  readonly environmentVariant = CONFIG_ENVIRONMENT_VARIANT;

  setView(view: View): void {
    this.view.set(view);
    if (view === 'dashboard' && this.allConfigs().length === 0) this.fetchAllConfigs();
    if (view === 'configurations' && this.items().length === 0) this.fetchLogs();
    if ((view === 'categories' || view === 'modules') && this.allConfigs().length === 0) this.fetchAllConfigs();
  }

  // ================= Référentiel modules/catégories + agrégat complet (tableau de bord) =================

  readonly modules = signal<string[]>([]);
  readonly allConfigs = signal<DynamicConfigurationResponse[]>([]);
  readonly allConfigsLoading = signal(false);

  fetchAllConfigs(): void {
    this.allConfigsLoading.set(true);
    this.configurationService.search(0, 1000).subscribe({
      next: (page) => {
        this.allConfigs.set(page.content);
        this.allConfigsLoading.set(false);
      },
      error: () => this.allConfigsLoading.set(false),
    });
  }

  readonly kpis = computed(() => {
    const all = this.allConfigs();
    return {
      total: all.length,
      actives: all.filter((c) => c.active).length,
      inactives: all.filter((c) => !c.active).length,
      modifiables: all.filter((c) => c.editable).length,
      sensibles: all.filter((c) => c.sensitive).length,
    };
  });

  readonly parModule = computed(() => {
    const counts = new Map<string, number>();
    for (const c of this.allConfigs()) {
      const key = c.module || 'Sans module';
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    return [...counts.entries()].map(([module, total]) => ({ module, total })).sort((a, b) => b.total - a.total);
  });

  readonly parCategorie = computed(() => {
    const counts = new Map<string, number>();
    for (const c of this.allConfigs()) {
      const key = c.category ?? 'OTHER';
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    return [...counts.entries()]
      .map(([category, total]) => ({ category: category as ConfigurationCategory, total }))
      .sort((a, b) => b.total - a.total);
  });

  readonly dernieresModifications = computed(() =>
    [...this.allConfigs()]
      .filter((c) => c.updatedAt)
      .sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''))
      .slice(0, 8),
  );

  readonly attentionRequise = computed(() =>
    this.allConfigs().filter((c) => !c.active || (c.restartRequired && c.active)),
  );

  filterByModule(module: string): void {
    this.filterForm.patchValue({ module });
    this.view.set('configurations');
    this.applyFilters();
  }

  filterByCategory(category: ConfigurationCategory): void {
    this.filterForm.patchValue({ category });
    this.view.set('configurations');
    this.applyFilters();
  }

  // ================= Liste / recherche =================

  readonly items = signal<DynamicConfigurationResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pageIndex = signal(0);
  readonly totalPages = signal(1);
  readonly totalElements = signal(0);
  readonly pageSize = 20;

  readonly filterForm = this.fb.nonNullable.group({
    q: '',
    module: '',
    category: this.fb.nonNullable.control<ConfigurationCategory | ''>(''),
    valueType: this.fb.nonNullable.control<ConfigurationValueType | ''>(''),
    environment: this.fb.nonNullable.control<ConfigurationEnvironment | ''>(''),
    active: this.fb.nonNullable.control<'true' | 'false' | ''>(''),
    editable: this.fb.nonNullable.control<'true' | 'false' | ''>(''),
    sensitive: this.fb.nonNullable.control<'true' | 'false' | ''>(''),
  });

  private buildFilters(): ConfigurationFilter {
    const raw = this.filterForm.getRawValue();
    return {
      q: raw.q.trim() || undefined,
      module: raw.module.trim() || undefined,
      category: raw.category || undefined,
      valueType: raw.valueType || undefined,
      environment: raw.environment || undefined,
      active: raw.active === '' ? undefined : raw.active === 'true',
      editable: raw.editable === '' ? undefined : raw.editable === 'true',
      sensitive: raw.sensitive === '' ? undefined : raw.sensitive === 'true',
    };
  }

  fetchLogs(): void {
    this.loading.set(true);
    this.error.set(null);
    this.configurationService.search(this.pageIndex(), this.pageSize, this.buildFilters()).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.totalPages.set(Math.max(1, page.totalPages));
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les configurations.');
        this.loading.set(false);
      },
    });
  }

  applyFilters(): void {
    this.pageIndex.set(0);
    this.fetchLogs();
  }

  resetFilters(): void {
    this.filterForm.reset({ q: '', module: '', category: '', valueType: '', environment: '', active: '', editable: '', sensitive: '' });
    this.pageIndex.set(0);
    this.fetchLogs();
  }

  goToPage(index: number): void {
    if (index < 0 || index >= this.totalPages()) return;
    this.pageIndex.set(index);
    this.fetchLogs();
  }

  refreshAll(): void {
    this.refreshingAll.set(true);
    this.configurationService.refreshAll().subscribe({
      next: () => {
        this.refreshingAll.set(false);
        this.fetchLogs();
      },
      error: () => {
        this.error.set('Impossible de rafraîchir toutes les configurations.');
        this.refreshingAll.set(false);
      },
    });
  }

  readonly refreshingAll = signal(false);
  readonly refreshingKey = signal<string | null>(null);

  refreshOne(config: DynamicConfigurationResponse): void {
    this.refreshingKey.set(config.configKey);
    this.configurationService.refresh(config.configKey).subscribe({
      next: (updated) => {
        this.applyUpdate(updated);
        this.refreshingKey.set(null);
      },
      error: () => {
        this.error.set(`Impossible de rafraîchir « ${config.configKey} ».`);
        this.refreshingKey.set(null);
      },
    });
  }

  private applyUpdate(updated: DynamicConfigurationResponse): void {
    this.items.set(this.items().map((c) => (c.configKey === updated.configKey ? updated : c)));
    if (this.selectedConfig()?.configKey === updated.configKey) this.selectedConfig.set(updated);
  }

  // ================= Activer / désactiver =================

  readonly toggleTarget = signal<{ config: DynamicConfigurationResponse; activate: boolean } | null>(null);
  readonly togglingActive = signal(false);

  askToggleActive(config: DynamicConfigurationResponse): void {
    this.toggleTarget.set({ config, activate: !config.active });
  }

  cancelToggleActive(): void {
    this.toggleTarget.set(null);
  }

  confirmToggleActive(): void {
    const target = this.toggleTarget();
    if (!target) return;
    this.togglingActive.set(true);
    const request = target.activate
      ? this.configurationService.activate(target.config.configKey)
      : this.configurationService.deactivate(target.config.configKey);
    request.subscribe({
      next: (updated) => {
        this.applyUpdate(updated);
        this.togglingActive.set(false);
        this.toggleTarget.set(null);
      },
      error: () => {
        this.error.set('Impossible de changer le statut de cette configuration.');
        this.togglingActive.set(false);
        this.toggleTarget.set(null);
      },
    });
  }

  // ================= Suppression =================

  readonly deleteTarget = signal<DynamicConfigurationResponse | null>(null);
  readonly deleting = signal(false);

  askDelete(config: DynamicConfigurationResponse): void {
    this.deleteTarget.set(config);
  }

  cancelDelete(): void {
    this.deleteTarget.set(null);
  }

  confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target) return;
    this.deleting.set(true);
    this.configurationService.delete(target.configKey).subscribe({
      next: () => {
        this.items.set(this.items().filter((c) => c.configKey !== target.configKey));
        this.deleting.set(false);
        this.deleteTarget.set(null);
      },
      error: () => {
        this.error.set(`Impossible de supprimer « ${target.configKey} ».`);
        this.deleting.set(false);
        this.deleteTarget.set(null);
      },
    });
  }

  // ================= Détail =================

  readonly detailVisible = signal(false);
  readonly selectedConfig = signal<DynamicConfigurationResponse | null>(null);

  openDetail(config: DynamicConfigurationResponse): void {
    this.selectedConfig.set(config);
    this.detailVisible.set(true);
  }

  maskedValue(config: DynamicConfigurationResponse): string {
    return config.sensitive ? '••••••••' : (config.configValue ?? '—');
  }

  // ================= Création / modification =================

  readonly formVisible = signal(false);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly editingKey = signal<string | null>(null);
  readonly pendingSubmit = signal<DynamicConfigurationRequest | null>(null);
  readonly sensitiveMotif = signal('');
  /** La configuration éditée était déjà sensible : valeur non rechargée (masquée), et elle ne peut
   * plus repasser en non sensible (409 backend depuis le 2026-09-24). */
  readonly editingWasSensitive = signal(false);
  /** Vider volontairement une valeur sensible (envoie "") : l'application retombe alors sur la
   * variable d'environnement du serveur. */
  readonly clearSensitiveValue = signal(false);
  /** Création : « Sensible » coché et verrouillé parce que la clé l'impose (voir SENSITIVE_KEY_PATTERN). */
  readonly sensitiveForcedByKey = signal(false);

  readonly sensitiveSwitchTitle = computed(() => {
    if (this.editingWasSensitive()) return 'Une configuration sensible ne peut pas redevenir non sensible';
    if (this.sensitiveForcedByKey()) return "Imposé par le nom de la clé (secret, mot de passe, clé d'API…)";
    return '';
  });

  toggleClearSensitiveValue(checked: boolean): void {
    this.clearSensitiveValue.set(checked);
    if (checked) this.configForm.controls.configValue.setValue('');
  }

  readonly configForm = this.fb.nonNullable.group({
    configKey: this.fb.nonNullable.control(EMPTY_FORM.configKey),
    configValue: this.fb.nonNullable.control<string>(EMPTY_FORM.configValue ?? ''),
    valueType: this.fb.nonNullable.control<ConfigurationValueType>(EMPTY_FORM.valueType),
    description: this.fb.nonNullable.control(EMPTY_FORM.description ?? ''),
    module: this.fb.nonNullable.control(EMPTY_FORM.module ?? ''),
    category: this.fb.nonNullable.control<ConfigurationCategory | ''>(''),
    environment: this.fb.nonNullable.control<ConfigurationEnvironment | ''>(''),
    defaultValue: this.fb.nonNullable.control(EMPTY_FORM.defaultValue ?? ''),
    validationRule: this.fb.nonNullable.control(EMPTY_FORM.validationRule ?? ''),
    allowedValues: this.fb.nonNullable.control(EMPTY_FORM.allowedValues ?? ''),
    active: this.fb.nonNullable.control(true),
    editable: this.fb.nonNullable.control(true),
    sensitive: this.fb.nonNullable.control(false),
    restartRequired: this.fb.nonNullable.control(false),
    cacheEnabled: this.fb.nonNullable.control(true),
    displayOrder: this.fb.nonNullable.control(0),
  });

  openCreate(): void {
    this.editingKey.set(null);
    this.editingWasSensitive.set(false);
    this.clearSensitiveValue.set(false);
    this.sensitiveForcedByKey.set(false);
    this.formError.set(null);
    this.configForm.reset({
      configKey: '', configValue: '', valueType: 'STRING', description: '', module: '',
      category: '', environment: '', defaultValue: '', validationRule: '', allowedValues: '',
      active: true, editable: true, sensitive: false, restartRequired: false, cacheEnabled: true, displayOrder: 0,
    });
    this.configForm.controls.sensitive.enable();
    this.formVisible.set(true);
  }

  openEdit(config: DynamicConfigurationResponse): void {
    this.editingKey.set(config.configKey);
    this.editingWasSensitive.set(config.sensitive);
    this.clearSensitiveValue.set(false);
    this.sensitiveForcedByKey.set(false);
    this.formError.set(null);
    this.configForm.reset({
      configKey: config.configKey,
      configValue: config.sensitive ? '' : (config.configValue ?? ''),
      valueType: config.valueType,
      description: config.description ?? '',
      module: config.module ?? '',
      category: config.category ?? '',
      environment: config.environment ?? '',
      defaultValue: config.defaultValue ?? '',
      validationRule: config.validationRule ?? '',
      allowedValues: config.allowedValues ?? '',
      active: config.active,
      editable: config.editable,
      sensitive: config.sensitive,
      restartRequired: config.restartRequired,
      cacheEnabled: config.cacheEnabled,
      displayOrder: config.displayOrder ?? 0,
    });
    // getRawValue() inclut les contrôles désactivés : sensitive reste bien envoyé à true.
    if (config.sensitive) this.configForm.controls.sensitive.disable();
    else this.configForm.controls.sensitive.enable();
    this.formVisible.set(true);
  }

  private buildPayload(): DynamicConfigurationRequest {
    const raw = this.configForm.getRawValue();
    return {
      configKey: raw.configKey.trim(),
      // Sensible déjà enregistrée et champ laissé vide : renvoyer le masque, que le backend
      // interprète comme « conserver la valeur existante » (une chaîne vide l'effacerait).
      configValue: this.editingWasSensitive() && raw.configValue === '' && !this.clearSensitiveValue() ? SENSITIVE_MASK : raw.configValue,
      valueType: raw.valueType,
      description: raw.description.trim() || undefined,
      module: raw.module.trim() || undefined,
      category: raw.category || undefined,
      environment: raw.environment || undefined,
      // Interdit sur une configuration sensible (400 backend) : la valeur par défaut serait en clair.
      defaultValue: raw.sensitive ? undefined : raw.defaultValue.trim() || undefined,
      validationRule: raw.validationRule.trim() || undefined,
      allowedValues: raw.allowedValues.trim() || undefined,
      active: raw.active,
      editable: raw.editable,
      sensitive: raw.sensitive,
      restartRequired: raw.restartRequired,
      cacheEnabled: raw.cacheEnabled,
      displayOrder: raw.displayOrder,
    };
  }

  submitForm(): void {
    const payload = this.buildPayload();
    if (!payload.configKey) {
      this.formError.set('La clé de configuration est requise.');
      return;
    }
    // Modification (pas création) d'un paramètre sensible : demander confirmation avant d'envoyer.
    if (this.editingKey() && this.configForm.getRawValue().sensitive) {
      this.pendingSubmit.set(payload);
      return;
    }
    this.saveConfig(payload);
  }

  cancelSensitiveConfirm(): void {
    this.pendingSubmit.set(null);
    this.sensitiveMotif.set('');
  }

  confirmSensitiveSubmit(): void {
    const payload = this.pendingSubmit();
    if (!payload) return;
    this.pendingSubmit.set(null);
    this.saveConfig({ ...payload, motif: this.sensitiveMotif().trim() || undefined });
    this.sensitiveMotif.set('');
  }

  private saveConfig(payload: DynamicConfigurationRequest): void {
    this.formSaving.set(true);
    this.formError.set(null);
    const key = this.editingKey();
    const request = key ? this.configurationService.update(key, payload) : this.configurationService.create(payload);
    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.formVisible.set(false);
        this.fetchLogs();
      },
      error: (err: unknown) => {
        this.formSaving.set(false);
        const body = err instanceof HttpErrorResponse ? (err.error as { message?: string } | null) : null;
        this.formError.set(body?.message || (key ? 'Impossible de mettre à jour cette configuration.' : 'Impossible de créer cette configuration.'));
      },
    });
  }

  // ================= Historique / restauration =================

  readonly historyVisible = signal(false);
  readonly historyLoading = signal(false);
  readonly historyError = signal<string | null>(null);
  readonly historyItems = signal<HistoriqueModificationResponse[]>([]);
  readonly historyKey = signal<string | null>(null);
  readonly restoreTarget = signal<HistoriqueModificationResponse | null>(null);
  readonly restoring = signal(false);

  readonly historySearchKey = signal('');

  openHistory(config: DynamicConfigurationResponse): void {
    this.historyKey.set(config.configKey);
    this.historyVisible.set(true);
    this.fetchHistory(config.configKey);
  }

  searchHistoryByKey(): void {
    const key = this.historySearchKey().trim();
    if (!key) return;
    this.historyKey.set(key);
    this.fetchHistory(key);
  }

  private fetchHistory(key: string): void {
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.configurationService.getHistory(key).subscribe({
      next: (items) => {
        this.historyItems.set(items);
        this.historyLoading.set(false);
      },
      error: () => {
        this.historyError.set(`Impossible de charger l'historique de « ${key} ».`);
        this.historyLoading.set(false);
      },
    });
  }

  askRestore(entry: HistoriqueModificationResponse): void {
    this.restoreTarget.set(entry);
  }

  cancelRestore(): void {
    this.restoreTarget.set(null);
  }

  confirmRestore(): void {
    const entry = this.restoreTarget();
    const key = this.historyKey();
    if (!entry || !key) return;
    this.restoring.set(true);
    this.configurationService.restoreVersion(key, entry.id).subscribe({
      next: (updated) => {
        this.applyUpdate(updated);
        this.restoring.set(false);
        this.restoreTarget.set(null);
        this.fetchHistory(key);
      },
      error: () => {
        this.historyError.set('Impossible de restaurer cette version.');
        this.restoring.set(false);
        this.restoreTarget.set(null);
      },
    });
  }

  formatDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('fr-FR') : '—';
  }

  formatInt(value: number): string {
    return new Intl.NumberFormat('fr-FR').format(value);
  }

  constructor() {
    // Création uniquement : une clé « secrète » coche et verrouille « Sensible », comme le backend
    // le forcera de toute façon ; si la clé ne correspond plus, on rend la main sans décocher.
    this.configForm.controls.configKey.valueChanges.pipe(takeUntilDestroyed()).subscribe((key) => {
      if (this.editingKey()) return;
      const sensitive = this.configForm.controls.sensitive;
      if (SENSITIVE_KEY_PATTERN.test(key)) {
        sensitive.setValue(true);
        sensitive.disable({ emitEvent: false });
        this.sensitiveForcedByKey.set(true);
      } else if (this.sensitiveForcedByKey()) {
        sensitive.enable({ emitEvent: false });
        this.sensitiveForcedByKey.set(false);
      }
    });
    this.configurationService.getModules().subscribe({ next: (m) => this.modules.set(m), error: () => undefined });
    if (this.view() === 'configurations') this.fetchLogs();
    else this.fetchAllConfigs();
  }
}
