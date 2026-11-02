import { TestBed } from '@angular/core/testing';

import { DevtoolsGuardService } from './devtools-guard.service';
import { AuthService } from '../auth/auth.service';

function keydown(init: Partial<KeyboardEventInit> & { key: string }): KeyboardEvent {
  return new KeyboardEvent('keydown', { cancelable: true, ...init });
}

describe('DevtoolsGuardService', () => {
  let service: DevtoolsGuardService;
  let logout: ReturnType<typeof vi.fn>;
  let currentUser: { role: string } | null;

  beforeEach(() => {
    logout = vi.fn();
    currentUser = { id: '1', role: 'ADMIN' } as unknown as { role: string };

    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { currentUser: () => currentUser, logout } }],
    });
    service = TestBed.inject(DevtoolsGuardService);
    service.init();
  });

  afterEach(() => {
    // init() attache des listeners au document réel — on ne peut pas les retirer individuellement
    // (fonctions anonymes), mais chaque test recrée son propre service/spy donc ça reste sans effet
    // sur les autres tests. On vide juste le state visuel éventuel.
  });

  it('empêche le menu contextuel (clic droit)', () => {
    const event = new MouseEvent('contextmenu', { cancelable: true });
    document.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
  });

  it('bloque F12', () => {
    const event = keydown({ key: 'F12' });
    document.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
  });

  it('bloque Ctrl+Shift+I', () => {
    const event = keydown({ key: 'i', ctrlKey: true, shiftKey: true });
    document.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
  });

  it('bloque Ctrl+U (affichage du code source)', () => {
    const event = keydown({ key: 'u', ctrlKey: true });
    document.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
  });

  it("ne bloque pas une combinaison anodine (Ctrl+S par exemple)", () => {
    const event = keydown({ key: 's', ctrlKey: true });
    document.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(false);
  });

  it("déconnecte après 3 tentatives répétées d'ouverture des DevTools", () => {
    document.dispatchEvent(keydown({ key: 'F12' }));
    document.dispatchEvent(keydown({ key: 'F12' }));
    expect(logout).not.toHaveBeenCalled();
    document.dispatchEvent(keydown({ key: 'F12' }));
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it("ne déclenche pas de déconnexion si personne n'est connecté", () => {
    currentUser = null;
    document.dispatchEvent(keydown({ key: 'F12' }));
    document.dispatchEvent(keydown({ key: 'F12' }));
    document.dispatchEvent(keydown({ key: 'F12' }));
    expect(logout).not.toHaveBeenCalled();
  });
});
