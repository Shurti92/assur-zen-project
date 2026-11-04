import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  AssistancePlan,
  AssistanceResponse,
  MobileProduct,
  MobileProductContentRequest,
  MobileProductList,
} from './models/mobile-product.model';

/** Catalogue de l'application mobile — /admin/mobile/products (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class MobileProductService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/mobile/products';

  list(): Observable<MobileProduct[]> {
    return this.http.get<MobileProductList>(this.base).pipe(map((r) => r.items));
  }

  get(productId: number): Observable<MobileProduct> {
    return this.http.get<MobileProduct>(`${this.base}/${productId}`);
  }

  /** Remplace tout le contenu mobile du produit. */
  save(productId: number, content: MobileProductContentRequest): Observable<MobileProduct> {
    return this.http.put<MobileProduct>(`${this.base}/${productId}`, content);
  }

  /** Fixe l'ordre d'affichage : le premier identifiant est affiché en premier. */
  saveOrder(productIds: number[]): Observable<MobileProduct[]> {
    return this.http.put<MobileProductList>(`${this.base}/order`, { productIds }).pipe(map((r) => r.items));
  }

  /** Niveaux d'assistance de l'Assurance Auto (applicable=false pour les autres produits). */
  assistance(productId: number): Observable<AssistanceResponse> {
    return this.http.get<AssistanceResponse>(`${this.base}/${productId}/assistance`);
  }

  /** REMPLACE toute la liste des niveaux (liste vide autorisée). */
  saveAssistance(productId: number, plans: AssistancePlan[]): Observable<AssistanceResponse> {
    return this.http.put<AssistanceResponse>(`${this.base}/${productId}/assistance`, { plans });
  }
}
