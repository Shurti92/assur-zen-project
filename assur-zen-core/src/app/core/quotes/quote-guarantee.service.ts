import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type GuaranteeKind = 'GUARANTEE' | 'LIMIT' | 'NOTE';

/** Ligne de garantie d'une formule et d'une option Entreprise (niveau 1 = ligne « dont » rattachée à la ligne de niveau 0 précédente). */
export interface EntrepriseGuarantee {
  id?: number;
  kind: GuaranteeKind;
  order: number;
  level: number;
  name: string | null;
  indemnityAmount: number | null;
  indemnityLabel: string | null;
  deductible: string | null;
  basis: string | null;
  active: boolean;
}

export interface EntrepriseGuaranteeList {
  productKey: string;
  formulaId: string;
  option: string;
  items: EntrepriseGuarantee[];
}

export interface EntrepriseGuaranteeInput {
  kind: GuaranteeKind;
  level: number;
  name: string | null;
  indemnityAmount: number | null;
  indemnityLabel: string | null;
  deductible: string | null;
  basis: string | null;
  active: boolean;
}

/** Garanties et montants d'indemnité des produits Entreprise — /admin/quotes/entreprise/guarantees (rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class QuoteGuaranteeService {
  private readonly http = inject(HttpClient);
  private readonly base = '/admin/quotes/entreprise/guarantees';

  get(formulaId: string, option: string): Observable<EntrepriseGuaranteeList> {
    const params = new HttpParams().set('formulaId', formulaId).set('option', option);
    return this.http.get<EntrepriseGuaranteeList>(this.base, { params });
  }

  /** Remplace TOUT le tableau de la formule et de l'option, dans l'ordre de la liste (liste vide = aucune garantie). */
  save(formulaId: string, option: string, items: EntrepriseGuaranteeInput[]): Observable<EntrepriseGuaranteeList> {
    return this.http.put<EntrepriseGuaranteeList>(this.base, { formulaId, option, items });
  }
}
