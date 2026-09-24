import { AbstractControl, ValidationErrors } from '@angular/forms';

export const MIN_PASSWORD_LENGTH = 8;

export interface PasswordRequirements {
  minLength: boolean;
  hasUpperCase: boolean;
  hasLowerCase: boolean;
  hasNumber: boolean;
  hasSpecialChar: boolean;
}

export function checkPasswordRequirements(password: string): PasswordRequirements {
  return {
    minLength: password.length >= MIN_PASSWORD_LENGTH,
    hasUpperCase: /[A-Z]/.test(password),
    hasLowerCase: /[a-z]/.test(password),
    hasNumber: /[0-9]/.test(password),
    hasSpecialChar: /[^A-Za-z0-9]/.test(password),
  };
}

export function strongPasswordValidator(control: AbstractControl): ValidationErrors | null {
  const requirements = checkPasswordRequirements(control.value ?? '');
  return Object.values(requirements).every(Boolean) ? null : { weakPassword: true };
}

export function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const newPassword = control.get('newPassword')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  return newPassword === confirmPassword ? null : { passwordMismatch: true };
}
