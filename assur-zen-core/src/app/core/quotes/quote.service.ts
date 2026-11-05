import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  QuoteContract,
  QuoteConvertRequest,
  QuoteCreateRequest,
  QuoteDocument,
  QuoteDocumentType,
  QuoteInsuredCandidate,
  QuoteLookup,
  QuotePaymentFilter,
  QuotePaymentReminderItem,
  QuoteForm,
  QuoteList,
  QuoteEmailHistoryItem,
  QuoteSendEmailRequest,
  QuoteSendSmsRequest,
  QuoteSmsHistoryItem,
  QuoteSimulation,
  QuoteStatus,
  QuoteVehicleRequest,
  QuoteView,
  VtcQuoteCreateRequest,
  VtcQuoteForm,
  VtcVehicleRequest,
} from './models/quote.model';

/** Devis saisis au back-office (moto et automobile VTC) — /admin/quotes (rôles ADMIN et COMMERCIAL). */
@Injectable({ providedIn: 'root' })
export class QuoteService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/quotes';

  /** Un commercial ne voit que ses devis, un administrateur voit tous les devis. */
  list(status?: QuoteStatus | null, q?: string | null, payment?: QuotePaymentFilter | null): Observable<QuoteView[]> {
    let params = new HttpParams();
    if (status) params = params.set('statut', status);
    if (payment) params = params.set('payment', payment);
    if (q?.trim()) params = params.set('q', q.trim());
    return this.http.get<QuoteList>(this.base, { params }).pipe(map((r) => r.items));
  }

  get(reference: string): Observable<QuoteView> {
    return this.http.get<QuoteView>(`${this.base}/${encodeURIComponent(reference)}`);
  }

  create(request: QuoteCreateRequest): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${this.base}/moto`, request);
  }

  createVtc(request: VtcQuoteCreateRequest): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${this.base}/vtc`, request);
  }

  /** Accord du prospect (409 QUOTE_EXPIRED si le devis est périmé). */
  accept(reference: string): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${this.base}/${encodeURIComponent(reference)}/accept`, {});
  }

  /** Envoie le devis par e-mail au prospect (inactif tant que QUOTE_EMAIL_ENABLED est false). */
  sendEmail(reference: string, request: QuoteSendEmailRequest): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${this.base}/${encodeURIComponent(reference)}/send-email`, request);
  }

  /** Envoie le lien du devis par SMS (inactif tant que QUOTE_SMS_ENABLED est false). */
  sendSms(reference: string, request: QuoteSendSmsRequest = {}): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${this.base}/${encodeURIComponent(reference)}/send-sms`, request);
  }

  /** Historique des SMS envoyés pour un devis, du plus récent au plus ancien. */
  smsHistory(reference: string): Observable<QuoteSmsHistoryItem[]> {
    return this.http
      .get<{ items: QuoteSmsHistoryItem[] }>(`${this.base}/${encodeURIComponent(reference)}/sms`)
      .pipe(map((r) => [...r.items].sort((a, b) => b.sentAt.localeCompare(a.sentAt))));
  }

  /** Historique complet des envois d'un devis, trié du plus récent au plus ancien. */
  emailHistory(reference: string): Observable<QuoteEmailHistoryItem[]> {
    return this.http
      .get<{ items: QuoteEmailHistoryItem[] }>(`${this.base}/${encodeURIComponent(reference)}/emails`)
      .pipe(map((r) => [...r.items].sort((a, b) => b.sentAt.localeCompare(a.sentAt))));
  }

  /** Options du formulaire moto (types de véhicule, durées, date d'effet minimale). */
  form(): Observable<QuoteForm> {
    return this.http.get<QuoteForm>('/public/quotes/moto/form');
  }

  /** Calcul du prix moto sans aucune écriture. */
  simulate(vehicle: QuoteVehicleRequest): Observable<QuoteSimulation> {
    return this.http.post<QuoteSimulation>('/public/quotes/moto/simulate', vehicle);
  }

  /** Options du formulaire VTC (énergies, durées, marques, plateformes, bornes CV et places). */
  vtcForm(): Observable<VtcQuoteForm> {
    return this.http.get<VtcQuoteForm>('/public/quotes/vtc/form');
  }

  /** Plafond de remise VTC, lisible par ADMIN et COMMERCIAL (GET /admin/quotes/vtc/discount-limit). */
  vtcDiscountLimit(): Observable<number> {
    return this.http.get<{ maxDiscountPercent: number }>(`${this.base}/vtc/discount-limit`).pipe(map((r) => r.maxDiscountPercent));
  }

  /** Calcul du prix VTC sans aucune écriture (sans remise : elle est estimée à l'écran). */
  simulateVtc(vehicle: VtcVehicleRequest): Observable<QuoteSimulation> {
    return this.http.post<QuoteSimulation>('/public/quotes/vtc/simulate', vehicle);
  }

  // ---------- Conversion d'un devis payé en contrat ----------

  private ref(reference: string): string {
    return `${this.base}/${encodeURIComponent(reference)}`;
  }

  documents(reference: string): Observable<QuoteDocument[]> {
    return this.http.get<{ items: QuoteDocument[] }>(`${this.ref(reference)}/documents`).pipe(map((r) => r.items));
  }

  uploadDocument(reference: string, type: QuoteDocumentType, file: File): Observable<QuoteDocument> {
    const form = new FormData();
    form.append('type', type);
    form.append('file', file);
    return this.http.post<QuoteDocument>(`${this.ref(reference)}/documents`, form);
  }

  /** Aperçu : le fichier brut (PDF, JPEG ou PNG). */
  documentBlob(reference: string, id: number | string): Observable<Blob> {
    return this.http.get(`${this.ref(reference)}/documents/${id}`, { responseType: 'blob' });
  }

  deleteDocument(reference: string, id: number | string): Observable<void> {
    return this.http.delete<void>(`${this.ref(reference)}/documents/${id}`);
  }

  convert(reference: string, request: QuoteConvertRequest): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${this.ref(reference)}/convert`, request);
  }

  contract(reference: string): Observable<QuoteContract> {
    return this.http.get<QuoteContract>(`${this.ref(reference)}/contract`);
  }

  lookup(kind: 'companies' | 'partners' | 'products'): Observable<QuoteLookup[]> {
    return this.http.get<{ items: QuoteLookup[] }>(`${this.base}/lookups/${kind}`).pipe(map((r) => r.items));
  }

  insuredCandidates(reference: string, q?: string): Observable<QuoteInsuredCandidate[]> {
    let params = new HttpParams();
    if (q?.trim()) params = params.set('q', q.trim());
    return this.http.get<{ items: QuoteInsuredCandidate[] }>(`${this.ref(reference)}/insured-candidates`, { params }).pipe(map((r) => r.items));
  }

  /** Rappel SMS « payez dans l'application » (devis accepté non payé). */
  sendPaymentReminder(reference: string, phone?: string): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${this.ref(reference)}/payment-reminder`, phone ? { phone } : {});
  }

  paymentReminders(reference: string): Observable<QuotePaymentReminderItem[]> {
    return this.http
      .get<{ items: QuotePaymentReminderItem[] }>(`${this.ref(reference)}/payment-reminders`)
      .pipe(map((r) => [...r.items].sort((a, b) => b.sentAt.localeCompare(a.sentAt))));
  }
}
