import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Configuration SMTP effective (GET /admin/mobile/mail/diagnostic) : jamais le mot de passe. */
export interface MailDiagnostic {
  host: string | null;
  port: number | null;
  ssl: boolean;
  starttls: boolean;
  auth: boolean;
  username: string | null;
  passwordSet: boolean;
  /** Origine de la configuration : back-office ou environnement. */
  source: string | null;
}

/** Résultat du test de connexion (POST /admin/mobile/mail/test) : aucun e-mail n'est envoyé. */
export interface MailTestResult {
  ok: boolean;
  error?: string | null;
}

/** Diagnostic de la messagerie (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class MobileMailService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/mobile/mail';

  diagnostic(): Observable<MailDiagnostic> {
    return this.http.get<MailDiagnostic>(`${this.base}/diagnostic`);
  }

  test(): Observable<MailTestResult> {
    return this.http.post<MailTestResult>(`${this.base}/test`, {});
  }
}
