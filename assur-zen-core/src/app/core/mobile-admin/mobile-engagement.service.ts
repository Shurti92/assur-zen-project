import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  EngagementBadge,
  EngagementBadgeRequest,
  EngagementCriterion,
  EngagementCriterionRequest,
  EngagementSettings,
  EngagementStats,
  QuizQuestion,
  QuizQuestionRequest,
} from './models/mobile-engagement.model';

/** Administration de l'engagement mobile — /admin/mobile/engagement (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class MobileEngagementService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/mobile/engagement';

  settings(): Observable<EngagementSettings> {
    return this.http.get<EngagementSettings>(`${this.base}/settings`);
  }

  saveSettings(payload: Omit<EngagementSettings, 'consentVersion'>): Observable<EngagementSettings> {
    return this.http.put<EngagementSettings>(`${this.base}/settings`, payload);
  }

  stats(): Observable<EngagementStats> {
    return this.http.get<EngagementStats>(`${this.base}/stats`);
  }

  criteria(): Observable<EngagementCriterion[]> {
    return this.http.get<{ items: EngagementCriterion[] }>(`${this.base}/criteria`).pipe(map((r) => r.items));
  }

  saveCriterion(code: string, payload: EngagementCriterionRequest): Observable<EngagementCriterion> {
    return this.http.put<EngagementCriterion>(`${this.base}/criteria/${encodeURIComponent(code)}`, payload);
  }

  badges(): Observable<EngagementBadge[]> {
    return this.http.get<{ items: EngagementBadge[] }>(`${this.base}/badges`).pipe(map((r) => r.items));
  }

  saveBadge(code: string, payload: EngagementBadgeRequest): Observable<EngagementBadge> {
    return this.http.put<EngagementBadge>(`${this.base}/badges/${encodeURIComponent(code)}`, payload);
  }

  questions(active?: boolean | null, theme?: string | null): Observable<QuizQuestion[]> {
    let params = new HttpParams();
    if (active !== null && active !== undefined) params = params.set('active', String(active));
    if (theme) params = params.set('theme', theme);
    return this.http.get<{ items: QuizQuestion[] } | QuizQuestion[]>(`${this.base}/quiz/questions`, { params }).pipe(map((r) => (Array.isArray(r) ? r : r.items)));
  }

  createQuestion(payload: QuizQuestionRequest): Observable<QuizQuestion> {
    return this.http.post<QuizQuestion>(`${this.base}/quiz/questions`, payload);
  }

  updateQuestion(id: number, payload: QuizQuestionRequest): Observable<QuizQuestion> {
    return this.http.put<QuizQuestion>(`${this.base}/quiz/questions/${id}`, payload);
  }

  setQuestionActive(id: number, active: boolean): Observable<QuizQuestion> {
    return this.http.post<QuizQuestion>(`${this.base}/quiz/questions/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  deleteQuestion(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/quiz/questions/${id}`);
  }
}
