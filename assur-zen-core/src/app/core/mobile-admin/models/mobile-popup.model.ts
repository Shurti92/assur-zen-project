/** Pop-up publicitaires de l'application mobile — /admin/promotions/popups (ADMIN). */

export type PopupMediaType = 'IMAGE' | 'VIDEO';
export type PopupFrequency = 'ONCE' | 'DAILY' | 'EVERY_OPEN';
export type PopupStatus = 'ACTIVE' | 'SCHEDULED' | 'EXPIRED' | 'INACTIVE';

export interface PopupStats {
  views: number;
  clicks: number;
  closes: number;
}

export interface Popup {
  id: string;
  title: string;
  internalName: string | null;
  placement: string;
  mediaType: PopupMediaType;
  mediaUrl: string | null;
  posterUrl: string | null;
  linkUrl: string | null;
  productId: string | null;
  productLabel: string | null;
  linkLabel: string | null;
  displayAfterSeconds: number;
  frequency: PopupFrequency;
  priority: number;
  startAt: string | null;
  endAt: string | null;
  active: boolean;
  status: PopupStatus;
  /** Change à chaque modification (le mobile remontre alors le pop-up). */
  version: string;
  stats: PopupStats;
  createdAt: string;
  updatedAt: string;
}

export interface PopupSummary {
  total: number;
  active: number;
  scheduled: number;
  expired: number;
  inactive: number;
}

export interface PopupList {
  items: Popup[];
  summary: PopupSummary;
}

/** Corps de POST (création, créée inactive par défaut) et PUT (modification). */
export interface PopupRequest {
  title: string;
  internalName: string | null;
  placement: string;
  mediaType: PopupMediaType;
  mediaUrl: string | null;
  posterUrl: string | null;
  linkUrl: string | null;
  productId: string | null;
  linkLabel: string | null;
  displayAfterSeconds: number;
  frequency: PopupFrequency;
  priority: number;
  startAt: string | null;
  endAt: string | null;
  active: boolean;
}

export interface PopupLimits {
  image: { maxBytes: number; types: string[] };
  video: { maxBytes: number; types: string[]; codec: string; maxDurationSeconds: number };
  poster: { maxBytes: number; types: string[] };
  maxDisplayAfterSeconds: number;
  maxPriority: number;
  placements: string[];
  frequencies: string[];
}

export interface PopupMediaUpload {
  url: string;
  mediaType: PopupMediaType;
  sizeBytes: number;
  widthPx?: number | null;
  heightPx?: number | null;
  durationSeconds?: number | null;
}

/** Produit ciblable : identifiant du catalogue MOBILE (texte, ex. « voyage »), pas l'id numérique. */
export interface PopupProduct {
  id: string;
  title: string;
}
