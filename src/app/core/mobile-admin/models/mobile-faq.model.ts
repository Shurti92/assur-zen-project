/** FAQ de l'application mobile (§9 du contrat) — GET/PUT /admin/mobile/faq. */

export interface MobileFaqItem {
  id?: number | null;
  question: string;
  answer: string;
  order?: number | null;
  visible?: boolean | null;
}

export interface MobileFaqList {
  items: MobileFaqItem[];
}

/** Corps du PUT : remplace toute la FAQ ; l'ordre de la liste fixe l'ordre d'affichage. */
export interface MobileFaqReplaceRequest {
  items: { question: string; answer: string; visible?: boolean }[];
}

/** Avis d'un client sur l'application (GET /admin/mobile/feedback). */
export interface MobileFeedback {
  id: number;
  userId?: number | null;
  phone?: string | null;
  contractId?: number | null;
  /** Note de 1 à 5. */
  rating: number;
  comment?: string | null;
  createdAt: string;
}
