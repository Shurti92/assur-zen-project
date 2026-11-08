import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import {
  ButtonDirective,
  DropdownComponent,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
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
type ActiveFilter = 'all' | 'active' | 'inactive';
type YesNoFilter = '' | 'true' | 'false';
type DetailTab = 'informations' | 'historique';

/** Libellé du groupe des configurations sans module renseigné. */
const SANS_MODULE = 'Sans module';

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

const REMINDER_DAYS_KEY = 'MOBILE_EXPIRY_REMINDER_DAYS';

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
  styleUrl: './configuration-dynamique.component.scss',
  imports: [
    ButtonDirective,
    DropdownComponent,
    DropdownItemDirective,
    DropdownMenuDirective,
    DropdownToggleDirective,
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
    if (view !== 'historique' && this.allConfigs().length === 0) this.fetchAllConfigs();
  }

  // ================= Référentiel modules/catégories + agrégat complet (toutes les vues) =================

  readonly modules = signal<string[]>([]);
  readonly allConfigs = signal<DynamicConfigurationResponse[]>([]);
  readonly allConfigsLoading = signal(false);

  fetchAllConfigs(): void {
    this.allConfigsLoading.set(true);
    this.error.set(null);
    this.configurationService.search(0, 1000).subscribe({
      next: (page) => {
        this.allConfigs.set(page.content);
        this.allConfigsLoading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les configurations.');
        this.allConfigsLoading.set(false);
      },
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
      redemarrage: all.filter((c) => c.restartRequired && c.active).length,
    };
  });

  readonly parModule = computed(() => {
    const counts = new Map<string, number>();
    for (const c of this.allConfigs()) {
      const key = c.module || SANS_MODULE;
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
    this.resetFilters();
    this.moduleFilter.set(module);
    this.view.set('configurations');
  }

  filterByCategory(category: ConfigurationCategory): void {
    this.resetFilters();
    this.categoryFilter.set(category);
    this.advancedFiltersOpen.set(true);
    this.view.set('configurations');
  }

  // ================= Liste : filtres côté client, regroupement par module =================
  // Validé sur maquette (2026-10-05). Le jeu complet est petit (quelques dizaines de clés) : il est
  // chargé une fois par fetchAllConfigs(), puis filtré et regroupé ici, sans pagination.

  readonly error = signal<string | null>(null);

  readonly searchQuery = signal('');
  readonly activeFilter = signal<ActiveFilter>('all');
  readonly moduleFilter = signal('');
  readonly categoryFilter = signal<ConfigurationCategory | ''>('');
  readonly valueTypeFilter = signal<ConfigurationValueType | ''>('');
  readonly environmentFilter = signal<ConfigurationEnvironment | ''>('');
  readonly editableFilter = signal<YesNoFilter>('');
  readonly sensitiveFilter = signal<YesNoFilter>('');
  readonly advancedFiltersOpen = signal(false);

  readonly advancedFiltersCount = computed(
    () =>
      [this.categoryFilter(), this.valueTypeFilter(), this.environmentFilter(), this.editableFilter(), this.sensitiveFilter()].filter(
        (value) => value !== '',
      ).length,
  );

  readonly filteredConfigs = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    const active = this.activeFilter();
    const module = this.moduleFilter();
    const category = this.categoryFilter();
    const valueType = this.valueTypeFilter();
    const environment = this.environmentFilter();
    const editable = this.editableFilter();
    const sensitive = this.sensitiveFilter();
    return this.allConfigs().filter((c) => {
      if (active !== 'all' && c.active !== (active === 'active')) return false;
      if (module && (c.module || SANS_MODULE) !== module) return false;
      if (category && c.category !== category) return false;
      if (valueType && c.valueType !== valueType) return false;
      if (environment && c.environment !== environment) return false;
      if (editable && c.editable !== (editable === 'true')) return false;
      if (sensitive && c.sensitive !== (sensitive === 'true')) return false;
      if (!q) return true;
      // Une valeur sensible n'est jamais comparée : la recherche ne doit pas permettre de la deviner.
      const haystack = [c.configKey, c.description, c.module, c.sensitive ? null : c.configValue];
      return haystack.some((text) => text?.toLowerCase().includes(q));
    });
  });

  readonly groupedConfigs = computed(() => {
    const groups = new Map<string, DynamicConfigurationResponse[]>();
    for (const c of this.filteredConfigs()) {
      const key = c.module || SANS_MODULE;
      const group = groups.get(key);
      if (group) group.push(c);
      else groups.set(key, [c]);
    }
    return [...groups.entries()]
      .map(([module, configs]) => ({
        module,
        configs: configs.sort((x, y) => (x.displayOrder ?? 0) - (y.displayOrder ?? 0) || x.configKey.localeCompare(y.configKey)),
      }))
      .sort((x, y) => x.module.localeCompare(y.module));
  });

  readonly moduleOptions = computed(() => [...this.parModule()].sort((x, y) => x.module.localeCompare(y.module)));

  readonly collapsedModules = signal<ReadonlySet<string>>(new Set());

  toggleModuleCollapsed(module: string): void {
    this.collapsedModules.update((current) => {
      const next = new Set(current);
      if (!next.delete(module)) next.add(module);
      return next;
    });
  }

  resetFilters(): void {
    this.searchQuery.set('');
    this.activeFilter.set('all');
    this.moduleFilter.set('');
    this.categoryFilter.set('');
    this.valueTypeFilter.set('');
    this.environmentFilter.set('');
    this.editableFilter.set('');
    this.sensitiveFilter.set('');
  }

  refreshAll(): void {
    this.refreshingAll.set(true);
    this.configurationService.refreshAll().subscribe({
      next: () => {
        this.refreshingAll.set(false);
        this.fetchAllConfigs();
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
    this.allConfigs.update((all) => all.map((c) => (c.configKey === updated.configKey ? updated : c)));
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
        this.allConfigs.update((all) => all.filter((c) => c.configKey !== target.configKey));
        if (this.selectedConfig()?.configKey === target.configKey) this.detailVisible.set(false);
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

  readonly detailTab = signal<DetailTab>('informations');

  /** L'historique est chargé à l'ouverture : il alimente l'onglet « Historique » de la fiche. */
  openDetail(config: DynamicConfigurationResponse, tab: DetailTab = 'informations'): void {
    this.selectedConfig.set(config);
    this.detailTab.set(tab);
    this.detailVisible.set(true);
    this.historyItems.set([]);
    this.historyKey.set(config.configKey);
    this.fetchHistory(config.configKey);
  }

  editFromDetail(config: DynamicConfigurationResponse): void {
    this.detailVisible.set(false);
    this.openEdit(config);
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

  /** Rappels d'échéance du mobile : liste de jours « 30,7 » (0 = le jour même). */
  isReminderDaysKey(): boolean {
    return this.configForm.controls.configKey.value.trim() === REMINDER_DAYS_KEY;
  }

  /** Textes et titre des notifications push du mobile (affichés sur l'écran verrouillé). */
  isPushTextKey(): boolean {
    const key = this.configForm.controls.configKey.value.trim();
    return key === 'MOBILE_PUSH_TITLE' || key.startsWith('MOBILE_PUSH_TEXT_');
  }

  /** Clé ENTERPRISE_REQUEST_* : retourne la clé (en majuscules) ou '' si autre. */
  enterpriseKey(): string {
    const k = this.configForm.controls.configKey.value.trim().toUpperCase();
    return k.startsWith('ENTERPRISE_REQUEST_') ? k : '';
  }

  /** Clé LOYALTY_* : retourne la clé (en majuscules) ou '' si autre. */
  loyaltyKey(): string {
    const k = this.configForm.controls.configKey.value.trim().toUpperCase();
    return k.startsWith('LOYALTY_') ? k : '';
  }

  isSmsConsentTextKey(): boolean {
    return this.configForm.controls.configKey.value.trim() === 'MOBILE_SMS_CONSENT_TEXT';
  }

  isPushGenericKey(): boolean {
    return this.configForm.controls.configKey.value.trim() === 'MOBILE_PUSH_GENERIC';
  }

  /** Jours valides (0-365) de la valeur saisie, pour l'affichage en puces. */
  reminderDayChips(value: string): number[] {
    return this.parseReminderDays(value).valid;
  }

  reminderDaysInvalid(value: string): string[] {
    return this.parseReminderDays(value).invalid;
  }

  private parseReminderDays(value: string): { valid: number[]; invalid: string[] } {
    const valid: number[] = [];
    const invalid: string[] = [];
    for (const part of (value ?? '').split(',').map((p) => p.trim()).filter(Boolean)) {
      const n = /^\d+$/.test(part) ? Number(part) : NaN;
      if (Number.isInteger(n) && n >= 0 && n <= 365) valid.push(n);
      else invalid.push(part);
    }
    return { valid, invalid };
  }

  submitForm(): void {
    const payload = this.buildPayload();
    if (!payload.configKey) {
      this.formError.set('La clé de configuration est requise.');
      return;
    }
    if (payload.configKey === REMINDER_DAYS_KEY) {
      const invalid = this.reminderDaysInvalid(payload.configValue ?? '');
      if (invalid.length > 0) {
        this.formError.set(`Valeurs invalides : ${invalid.join(', ')}. Entiers de 0 à 365, séparés par des virgules.`);
        return;
      }
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
        this.fetchAllConfigs();
      },
      error: (err: unknown) => {
        this.formSaving.set(false);
        const body = err instanceof HttpErrorResponse ? (err.error as { message?: string } | null) : null;
        this.formError.set(body?.message || (key ? 'Impossible de mettre à jour cette configuration.' : 'Impossible de créer cette configuration.'));
      },
    });
  }

  // ================= Historique / restauration =================

  readonly historyLoading = signal(false);
  readonly historyError = signal<string | null>(null);
  readonly historyItems = signal<HistoriqueModificationResponse[]>([]);
  readonly historyKey = signal<string | null>(null);
  readonly restoreTarget = signal<HistoriqueModificationResponse | null>(null);
  readonly restoring = signal(false);

  readonly historySearchKey = signal('');

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

  formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('fr-FR') : '—';
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
    if (this.view() !== 'historique') this.fetchAllConfigs();
  }
}
