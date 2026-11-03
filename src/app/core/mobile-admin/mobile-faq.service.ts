import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { MobileFaqItem, MobileFaqList, MobileFaqReplaceRequest, MobileFeedback } from './models/mobile-faq.model';

/** FAQ et avis de l'application mobile — /admin/mobile/faq et /admin/mobile/feedback (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class MobileFaqService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/mobile';

  listFaq(): Observable<MobileFaqItem[]> {
    return this.http.get<MobileFaqList>(`${this.base}/faq`).pipe(map((r) => r.items));
  }

  /** Remplace toute la FAQ. */
  replaceFaq(payload: MobileFaqReplaceRequest): Observable<MobileFaqItem[]> {
    return this.http.put<MobileFaqList>(`${this.base}/faq`, payload).pipe(map((r) => r.items));
  }

  listFeedback(): Observable<MobileFeedback[]> {
    return this.http.get<MobileFeedback[]>(`${this.base}/feedback`);
  }
}
