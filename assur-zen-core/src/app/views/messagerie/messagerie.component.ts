import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SpinnerComponent } from '@coreui/angular';

import { MailDiagnostic, MailTestResult, MobileMailService } from '../../core/mobile-admin/mobile-mail.service';

interface LastTest {
  result: MailTestResult;
  at: Date;
  ms: number;
}

/** Mobile AssurZen > Messagerie : état du serveur d'e-mail et test de connexion (ADMIN). Maquette validée le 2026-11-06. */
@Component({
  selector: 'app-messagerie',
  standalone: true,
  imports: [RouterLink, SpinnerComponent],
  templateUrl: './messagerie.component.html',
  styleUrl: './messagerie.component.scss',
})
export class MessagerieComponent {
  private readonly service = inject(MobileMailService);

  readonly diag = signal<MailDiagnostic | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly testing = signal(false);
  readonly last = signal<LastTest | null>(null);

  /** Origine lisible de la configuration. */
  readonly sourceLabel = computed(() => {
    const s = (this.diag()?.source ?? '').toLowerCase();
    if (!s) return '—';
    if (s.includes('back')) return 'Back-office';
    if (s.includes('env')) return 'Environnement';
    return this.diag()?.source ?? '—';
  });

  /** Le mot de passe manque alors qu'un utilisateur ou l'authentification est configuré. */
  readonly missingPassword = computed(() => {
    const d = this.diag();
    return !!d && !d.passwordSet && (d.auth || !!d.username);
  });

  /** Pistes selon la cause renvoyée par le serveur. */
  readonly hints = computed(() => {
    const e = (this.last()?.result.error ?? '').toLowerCase();
    const d = this.diag();
    const out: string[] = [];
    if (/auth|535|identifiant|mot de passe|password|credential|refus/.test(e)) {
      out.push(`Vérifiez le mot de passe de ${d?.username || "l'utilisateur SMTP"}.`);
    }
    if (/d[eé]lai|timeout|timed out|connect|unreachable|inaccessible|refused/.test(e)) {
      out.push('Le serveur ne répond pas : port bloqué par le réseau, ou mauvais serveur / port.');
    }
    if (/certif|ssl|tls|handshake/.test(e)) {
      out.push('Vérifiez le mode de connexion (SSL direct sur le port 465, STARTTLS sur le 587) et le certificat.');
    }
    return out;
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.service.diagnostic().subscribe({
      next: (d) => {
        this.diag.set(d);
        this.loading.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.loadError.set(
          e.status === 404
            ? "La passerelle ne propose pas encore le diagnostic de messagerie (version à déployer)."
            : 'Impossible de lire la configuration de la messagerie.',
        );
        this.loading.set(false);
      },
    });
  }

  runTest(): void {
    if (this.testing()) return;
    this.testing.set(true);
    const t0 = performance.now();
    this.service.test().subscribe({
      next: (result) => {
        this.last.set({ result, at: new Date(), ms: performance.now() - t0 });
        this.testing.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.last.set({
          result: { ok: false, error: e.error?.error ?? e.error?.message ?? `Erreur ${e.status || 'réseau'}` },
          at: new Date(),
          ms: performance.now() - t0,
        });
        this.testing.set(false);
      },
    });
  }

  duration(ms: number): string {
    return (ms / 1000).toLocaleString('fr-FR', { maximumFractionDigits: 1 }) + ' s';
  }
}
