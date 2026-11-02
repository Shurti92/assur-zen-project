import { Directive, ElementRef, HostListener, Renderer2, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/** Formats a numeric input with spaces as thousand separators while keeping the form value a plain number. */
@Directive({
  selector: '[wzThousandSeparator]',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ThousandSeparatorDirective),
      multi: true,
    },
  ],
})
export class ThousandSeparatorDirective implements ControlValueAccessor {
  private onChange: (value: number | null) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(
    private readonly el: ElementRef<HTMLInputElement>,
    private readonly renderer: Renderer2,
  ) {}

  @HostListener('input')
  onInput(): void {
    const digits = this.extractDigits(this.el.nativeElement.value);
    const numericValue = digits === '' || digits === '-' ? null : Number(digits);
    this.setDisplayValue(digits);
    this.onChange(numericValue);
  }

  @HostListener('blur')
  onBlur(): void {
    this.onTouched();
  }

  writeValue(value: number | string | null): void {
    // Montants en FCFA entiers : un nombre décimal est arrondi — sans ça, String(2500.5) donnait
    // les chiffres « 25005 ».
    const normalized = typeof value === 'number' && Number.isFinite(value) ? String(Math.round(value)) : value;
    const digits = normalized === null || normalized === undefined || normalized === '' ? '' : this.extractDigits(String(normalized));
    this.setDisplayValue(digits);
  }

  registerOnChange(fn: (value: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.renderer.setProperty(this.el.nativeElement, 'disabled', isDisabled);
  }

  /** Montants en FCFA entiers : la virgule (séparateur décimal français) et ce qui la suit sont
   * ignorés — « 2 500,50 » donne 2500 et non 250050. Les points restent traités comme des
   * séparateurs de milliers (« 2.500 » → 2500). */
  private extractDigits(value: string): string {
    const negative = value.trim().startsWith('-');
    const digits = value.split(',')[0].replace(/[^\d]/g, '');
    return negative && digits ? `-${digits}` : digits;
  }

  private setDisplayValue(digits: string): void {
    this.renderer.setProperty(this.el.nativeElement, 'value', this.format(digits));
  }

  private format(digits: string): string {
    if (!digits) {
      return '';
    }
    const negative = digits.startsWith('-');
    const raw = negative ? digits.slice(1) : digits;
    const grouped = raw.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
    return negative ? `-${grouped}` : grouped;
  }
}
