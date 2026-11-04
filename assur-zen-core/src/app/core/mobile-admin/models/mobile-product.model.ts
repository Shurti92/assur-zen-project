/** Contrat : docs/api-contracts/admin-mobile.md du gateway (§7 Catalogue mobile et §8 contenu enrichi). */

export type ProductFamily = 'VEHICLE' | 'PERSON' | 'PROPERTY' | 'PROFESSIONAL';
export type GuaranteeKind = 'MANDATORY' | 'INCLUDED' | 'OPTIONAL' | 'TO_CONFIRM';

export interface MobileGuarantee {
  id?: string | null;
  name: string;
  kind: GuaranteeKind;
  description?: string | null;
  limit?: string | null;
  formulaIds?: string[] | null;
  displayOrder?: number | null;
  visible?: boolean | null;
}

export interface MobileFormula {
  id: string;
  name: string;
  description?: string | null;
  displayOrder?: number | null;
  visible?: boolean | null;
}

export interface MobileFaq {
  question: string;
  answer: string;
}

/** Produit du back-office avec son contenu mobile (GET /admin/mobile/products[/{id}]). */
export interface MobileProduct {
  productId: number;
  productKey?: string | null;
  title: string;
  productActive: boolean;
  /** false = aucun contenu mobile enregistré : le produit est invisible sur l'application. */
  configured: boolean;
  visibleMobile: boolean;
  shortName?: string | null;
  family?: ProductFamily | null;
  description?: string | null;
  benefits?: string[] | null;
  howItWorks?: string | null;
  eligibility?: string | null;
  guarantees?: MobileGuarantee[] | null;
  formulas?: MobileFormula[] | null;
  exclusions?: string[] | null;
  requiredDocuments?: string[] | null;
  faq?: MobileFaq[] | null;
  icon?: string | null;
  displayOrder?: number | null;
  onlineSubscription?: boolean | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface MobileProductList {
  items: MobileProduct[];
}

/** Corps du PUT : remplace TOUT le contenu mobile du produit (champs absents effacés). */
export interface MobileProductContentRequest {
  visibleMobile: boolean;
  shortName?: string;
  family?: ProductFamily;
  description?: string;
  benefits?: string[];
  howItWorks?: string;
  eligibility?: string;
  guarantees?: MobileGuarantee[];
  formulas?: MobileFormula[];
  exclusions?: string[];
  requiredDocuments?: string[];
  faq?: MobileFaq[];
  icon?: string;
  displayOrder?: number;
  onlineSubscription?: boolean;
}

/** Une prestation d'un niveau d'assistance (texte libre, condition facultative). */
export interface AssistanceItem {
  text: string;
  condition?: string | null;
}

/** Une section de prestations d'un niveau (accident, panne, vol, incendie…). */
export interface AssistanceSection {
  title: string;
  items: AssistanceItem[];
}

/** Niveau d'assistance de l'Assurance Auto (admin-mobile.md §11, jamais pour le VTC). */
export interface AssistancePlan {
  key: string;
  name: string;
  /** Entier >= 0 en F CFA (0 pour « Gratuit »). */
  price: number;
  /** Libellé libre affiché tel quel sur le mobile (ex. « +38 000 F »). */
  priceLabel?: string | null;
  minVehicleAge?: number | null;
  maxVehicleAge?: number | null;
  displayOrder?: number | null;
  active?: boolean;
  benefit?: boolean;
  recommended?: boolean;
  sections: AssistanceSection[];
}

/** GET /admin/mobile/products/{id}/assistance. */
export interface AssistanceResponse {
  productId: number;
  productKey?: string | null;
  /** false hors Assurance Auto (plans vide). */
  applicable: boolean;
  plans: AssistancePlan[];
  updatedAt?: string | null;
  updatedBy?: string | null;
}
