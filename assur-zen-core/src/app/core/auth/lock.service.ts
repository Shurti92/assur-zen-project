import { Injectable, signal } from '@angular/core';

const LOCK_KEY = 'auth_locked';

/** Session lock state, kept in sessionStorage so a page reload preserves the lock but a new tab/session does not. */
@Injectable({ providedIn: 'root' })
export class LockService {
  private readonly lockedSignal = signal<boolean>(sessionStorage.getItem(LOCK_KEY) === '1');

  readonly isLocked = this.lockedSignal.asReadonly();

  lock(): void {
    sessionStorage.setItem(LOCK_KEY, '1');
    this.lockedSignal.set(true);
  }

  unlock(): void {
    sessionStorage.removeItem(LOCK_KEY);
    this.lockedSignal.set(false);
  }
}
