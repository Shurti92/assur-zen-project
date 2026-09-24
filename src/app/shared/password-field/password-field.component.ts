import { Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { IconDirective } from '@coreui/icons-angular';
import { ButtonDirective, FormControlDirective, InputGroupComponent } from '@coreui/angular';

@Component({
  selector: 'app-password-field',
  templateUrl: './password-field.component.html',
  imports: [InputGroupComponent, FormControlDirective, ButtonDirective, IconDirective],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PasswordFieldComponent),
      multi: true,
    },
  ],
})
export class PasswordFieldComponent implements ControlValueAccessor {
  readonly id = input<string>();
  readonly autoComplete = input<string>('current-password');

  readonly visible = signal(false);
  readonly disabled = signal(false);

  value = '';

  private onChange: (value: string) => void = () => {};
  onTouched: () => void = () => {};

  toggleVisibility(): void {
    this.visible.update((v) => !v);
  }

  onInput(value: string): void {
    this.value = value;
    this.onChange(value);
  }

  writeValue(value: string): void {
    this.value = value ?? '';
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }
}
