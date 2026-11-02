import { Component, computed, effect, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, FormCheckInputDirective, FormControlDirective, FormSelectDirective } from '@coreui/angular';

import { ConfigurationValueType } from '../../../core/configurations/models/configuration.model';

/**
 * Éditeur de valeur générique — le contrôle affiché change selon `valueType`.
 * `value` reste toujours une string à plat (c'est le format stocké par `configValue`
 * côté backend, quel que soit le type) : JSON/LIST sont sérialisés/désérialisés en interne.
 *
 * Usage : <app-configuration-value-editor [valueType]="..." [allowedValues]="..." [(value)]="..." />
 */
@Component({
  selector: 'app-configuration-value-editor',
  templateUrl: './configuration-value-editor.component.html',
  imports: [FormsModule, IconDirective, ButtonDirective, FormControlDirective, FormCheckInputDirective, FormSelectDirective],
})
export class ConfigurationValueEditorComponent {
  readonly valueType = input.required<ConfigurationValueType>();
  readonly allowedValues = input<string | null>(null);
  readonly disabled = input(false);
  readonly value = model<string>('');

  readonly enumOptions = computed(() => {
    const raw = this.allowedValues();
    if (!raw) return [];
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed.map((v) => String(v));
    } catch {
      // pas du JSON — traiter comme une liste séparée par des virgules
    }
    return raw.split(',').map((v) => v.trim()).filter(Boolean);
  });

  readonly listItems = signal<string[]>([]);
  readonly listDraft = signal('');
  readonly jsonError = signal<string | null>(null);

  constructor() {
    effect(() => {
      if (this.valueType() !== 'LIST') return;
      const raw = this.value();
      try {
        const parsed = raw ? JSON.parse(raw) : [];
        this.listItems.set(Array.isArray(parsed) ? parsed.map((v) => String(v)) : []);
      } catch {
        this.listItems.set([]);
      }
    });
  }

  onBooleanChange(checked: boolean): void {
    this.value.set(String(checked));
  }

  get booleanValue(): boolean {
    return this.value() === 'true';
  }

  onJsonChange(raw: string): void {
    this.value.set(raw);
    if (!raw.trim()) {
      this.jsonError.set(null);
      return;
    }
    try {
      JSON.parse(raw);
      this.jsonError.set(null);
    } catch {
      this.jsonError.set('JSON invalide.');
    }
  }

  addListItem(): void {
    const draft = this.listDraft().trim();
    if (!draft) return;
    const next = [...this.listItems(), draft];
    this.listItems.set(next);
    this.value.set(JSON.stringify(next));
    this.listDraft.set('');
  }

  removeListItem(index: number): void {
    const next = this.listItems().filter((_, i) => i !== index);
    this.listItems.set(next);
    this.value.set(JSON.stringify(next));
  }

  moveListItem(index: number, direction: -1 | 1): void {
    const items = [...this.listItems()];
    const target = index + direction;
    if (target < 0 || target >= items.length) return;
    [items[index], items[target]] = [items[target], items[index]];
    this.listItems.set(items);
    this.value.set(JSON.stringify(items));
  }
}
