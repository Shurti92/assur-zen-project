import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';

export type EnterpriseRequestStatus = 'RECUE' | 'EN_COURS' | 'TRAITEE' | 'REFUSEE';

/** État d'un envoi d'e-mail lié à la demande (null = demande antérieure au suivi des e-mails). */
export type EmailStatus = 'EN_ATTENTE' | 'ENVOYE' | 'ECHEC' | 'NON_CONFIGURE' | 'DESACTIVE';

/** Demande de souscription Entreprise envoyée depuis l'application après une cotation. */
export interface EnterpriseRequest {
  id: number;
  reference: string;
  status: EnterpriseRequestStatus;
  userId?: number | null;
  accountPhone?: string | null;
  formulaId: string;
  formulaName?: string | null;
  option: string;
  optionLabel?: string | null;
  /** Prime TTC annoncée au client au moment de la demande. */
  quotedTotal: number;
  activityDomain?: string | null;
  companyName: string;
  rccmNumber?: string | null;
  location?: string | null;
  representativeName?: string | null;
  representativeRole?: string | null;
  representativePhone?: string | null;
  representativeEmail?: string | null;
  createdAt: string;
  processedAt?: string | null;
  processedBy?: string | null;
  processComment?: string | null;
  /** Niveau Zen Points du client (absent si non concerné). */
  loyaltyLevel?: string | null;
  /** Alerte e-mail au conseiller (champs de sortie, absents sur les demandes antérieures au suivi). */
  alertStatus?: EmailStatus | null;
  alertDetail?: string | null;
  alertAt?: string | null;
  /** E-mail de confirmation au représentant. */
  confirmationStatus?: EmailStatus | null;
  confirmationDetail?: string | null;
  confirmationAt?: string | null;
}

export interface ActivityDomain {
  code: string;
  label: string;
  order: number;
  active: boolean;
}

export const ENTERPRISE_STATUS_LABEL: Record<EnterpriseRequestStatus, string> = {
  RECUE: 'Reçue',
  EN_COURS: 'En cours',
  TRAITEE: 'Traitée',
  REFUSEE: 'Refusée',
};

const ERROR_MESSAGES: Record<string, string> = {
  REQUEST_ALREADY_PROCESSED: 'Cette demande a déjà été traitée ou refusée : elle n’est plus modifiable.',
  REQUEST_NOT_FOUND: 'Demande introuvable.',
  VALIDATION_FAILED: 'Champ manquant ou invalide.',
};

/** Message lisible pour une erreur des souscriptions Entreprise. */
export function enterpriseErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const code = err.error?.code as string | undefined;
    if (code && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code];
    if (err.status === 403) return 'Accès réservé aux administrateurs.';
    if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
  }
  return fallback;
}

/** Souscriptions Entreprise et domaines d'activité — /admin/mobile/enterprise-requests, /admin/quotes/entreprise (ADMIN). */
@Injectable({ providedIn: 'root' })
export class EnterpriseRequestService {
  private readonly http = inject(HttpClient);

  /** Demandes reçues ou en cours (pastille du menu). null = inconnu. */
  readonly pendingCount = signal<number | null>(null);

  list(status?: EnterpriseRequestStatus): Observable<EnterpriseRequest[]> {
    const params = status ? new HttpParams().set('status', status) : new HttpParams();
    return this.http.get<{ items: EnterpriseRequest[] }>('/admin/mobile/enterprise-requests', { params }).pipe(map((r) => r.items));
  }

  /** RECUE → EN_COURS / TRAITEE / REFUSEE ; EN_COURS → TRAITEE / REFUSEE. Le client est notifié. */
  setStatus(id: number, status: 'EN_COURS' | 'TRAITEE' | 'REFUSEE', comment?: string): Observable<EnterpriseRequest> {
    return this.http.put<EnterpriseRequest>(`/admin/mobile/enterprise-requests/${id}/status`, {
      status,
      ...(comment ? { comment } : {}),
    });
  }

  /** Recompte les demandes à traiter pour la pastille du menu. */
  refreshPending(): void {
    this.list().subscribe({
      next: (items) => this.pendingCount.set(items.filter((i) => i.status === 'RECUE' || i.status === 'EN_COURS').length),
      error: () => this.pendingCount.set(null),
    });
  }

  domains(): Observable<ActivityDomain[]> {
    return this.http.get<{ items: ActivityDomain[] }>('/admin/quotes/entreprise/activity-domains').pipe(map((r) => r.items));
  }

  /** Remplace la liste DANS L'ORDRE ; un code absent est désactivé (jamais supprimé). */
  saveDomains(items: { code: string; label: string; active: boolean }[]): Observable<ActivityDomain[]> {
    return this.http
      .put<{ items: ActivityDomain[] }>('/admin/quotes/entreprise/activity-domains', { items })
      .pipe(map((r) => r.items));
  }
}
