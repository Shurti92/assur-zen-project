import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { ThousandSeparatorDirective } from './thousand-separator.directive';

@Component({
  imports: [ReactiveFormsModule, ThousandSeparatorDirective],
  template: `<input [formControl]="control" wzThousandSeparator />`,
})
class HostComponent {
  readonly control = new FormControl<number | null>(null);
}

describe('ThousandSeparatorDirective', () => {
  function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const type = (text: string) => {
      input.value = text;
      input.dispatchEvent(new Event('input'));
    };
    return { fixture, input, type, control: fixture.componentInstance.control };
  }

  it('affiche « 2 500 » mais garde la valeur numérique 2500', () => {
    const { input, type, control } = setup();
    type('2500');
    expect(control.value).toBe(2500);
    expect(input.value).toBe('2 500');
  });

  it('montants FCFA entiers : ce qui suit la virgule est ignoré', () => {
    const { type, control } = setup();
    type('2 500,50');
    expect(control.value).toBe(2500);
  });

  it('ignore le texte « FCFA » et les points de milliers', () => {
    const { type, control } = setup();
    type('2.500 FCFA');
    expect(control.value).toBe(2500);
  });

  it('arrondit une valeur décimale reçue au lieu de concaténer ses chiffres', () => {
    const { fixture, input, control } = setup();
    control.setValue(2500.4);
    fixture.detectChanges();
    expect(input.value).toBe('2 500');
  });
});
