import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Règle d'attribution (geste récompensé). */
export interface LoyaltyRule {
  code: string;
  label: string;
  points: number;
  limitLabel: string | null;
  monthlyCap: number | null;
  active: boolean;
  displayOrder: number;
}

export interface LoyaltyRuleUpdate {
  label: string;
  points: number;
  limitLabel: string | null;
  monthlyCap: number | null;
  active: boolean;
  displayOrder: number;
}

export interface LoyaltyLevel {
  code: string;
  label: string;
  threshold: number;
  perks: string[];
}

export interface LoyaltyLevelUpdate {
  label: string;
  threshold: number;
  perks: string[];
}

export interface LoyaltyLevelCreate extends LoyaltyLevelUpdate {
  code: string;
}

export interface LoyaltyStats {
  enabled: boolean;
  members: number;
  pointsDistributedThisMonth: number;
  globalMonthlyCap: number;
  expiryMonths: number;
  withdrawalGraceDays: number;
}

export interface LoyaltyClientLevel {
  code: string;
  label: string;
  qualifyingPoints: number;
  nextLevel?: { code: string; label: string; threshold: number; pointsToGo: number } | null;
}

export interface LoyaltyHistoryItem {
  id: string;
  date: string;
  /** EARN (règle), ADJUST (ajustement admin), EXPIRE (expiration). */
  type: string;
  label: string;
  points: number;
}

export interface LoyaltyClient {
  userId: number;
  accepted: boolean;
  acceptedAt: string | null;
  withdrawnAt: string | null;
  balance: number;
  level: LoyaltyClientLevel | null;
  history: { items: LoyaltyHistoryItem[]; page: number; size: number; total: number };
}

const ERROR_MESSAGES: Record<string, string> = {
  LOYALTY_MEMBER_NOT_FOUND: "Ce client n'a pas adhéré au programme Zen Points.",
  LOYALTY_NOT_MEMBER: "Ce client n'est plus membre du programme (accord retiré) : ajustement impossible.",
  INSUFFICIENT_BALANCE: 'Le solde ne peut pas devenir négatif.',
  LEVEL_CODE_INVALID: 'Code de niveau invalide ou déjà utilisé (majuscules, chiffres et _ uniquement).',
  VALIDATION_FAILED: 'Champ manquant ou invalide.',
};

/** Message lisible pour une erreur Zen Points. */
export function loyaltyErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const code = err.error?.code as string | undefined;
    if (code && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code];
    if (err.status === 403) return 'Accès réservé aux administrateurs.';
    if (err.status === 404) return 'Élément introuvable.';
    if (typeof err.error?.message === 'string' && err.error.message) return err.error.message;
  }
  return fallback;
}

/** Zen Points — /admin/loyalty (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class LoyaltyService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/loyalty';

  stats(): Observable<LoyaltyStats> {
    return this.http.get<LoyaltyStats>(`${this.base}/stats`);
  }

  rules(): Observable<LoyaltyRule[]> {
    return this.http.get<LoyaltyRule[]>(`${this.base}/rules`);
  }

  updateRule(code: string, body: LoyaltyRuleUpdate): Observable<LoyaltyRule> {
    return this.http.put<LoyaltyRule>(`${this.base}/rules/${encodeURIComponent(code)}`, body);
  }

  levels(): Observable<LoyaltyLevel[]> {
    return this.http.get<LoyaltyLevel[]>(`${this.base}/levels`);
  }

  updateLevel(code: string, body: LoyaltyLevelUpdate): Observable<LoyaltyLevel> {
    return this.http.put<LoyaltyLevel>(`${this.base}/levels/${encodeURIComponent(code)}`, body);
  }

  createLevel(body: LoyaltyLevelCreate): Observable<LoyaltyLevel> {
    return this.http.post<LoyaltyLevel>(`${this.base}/levels`, body);
  }

  client(userId: number, page = 0, size = 20): Observable<LoyaltyClient> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<LoyaltyClient>(`${this.base}/clients/${userId}`, { params });
  }

  adjust(userId: number, points: number, reason: string): Observable<LoyaltyClient> {
    return this.http.post<LoyaltyClient>(`${this.base}/clients/${userId}/adjust`, { points, reason });
  }
}
