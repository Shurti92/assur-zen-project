import {
  AssistancePlan,
  GuaranteeKind,
  MobileFaq,
  MobileFormula,
  MobileGuarantee,
  MobileProduct,
  ProductFamily,
} from './models/mobile-product.model';

/** Identifiant du format de fichier d'export / import de la fiche mobile d'un produit. */
export const CARD_FORMAT = 'assurzen-fiche-mobile';
export const CARD_VERSION = 1;

// Limites du contrat (admin-mobile.md §7 et §8), identiques à l'éditeur.
const MAX = { benefits: 10, guarantees: 20, formulas: 10, exclusions: 20, documents: 20, faq: 30 };
const FAMILIES: ProductFamily[] = ['VEHICLE', 'PERSON', 'PROPERTY', 'PROFESSIONAL'];
const KINDS: GuaranteeKind[] = ['MANDATORY', 'INCLUDED', 'OPTIONAL', 'TO_CONFIRM'];

/** Contenu d'une fiche : tout ce que le back-office enregistre, sauf la visibilité et l'ordre d'affichage. */
export interface CardContent {
  shortName?: string;
  family?: ProductFamily;
  icon?: string;
  description?: string;
  benefits: string[];
  howItWorks?: string;
  eligibility?: string;
  guarantees: MobileGuarantee[];
  formulas: MobileFormula[];
  exclusions: string[];
  requiredDocuments: string[];
  faq: MobileFaq[];
}

export interface CardFile {
  format: typeof CARD_FORMAT;
  version: number;
  source: string;
  exportedAt: string;
  produit: { cle: string | null; titre: string };
  contenu: CardContent;
  /** Niveaux d'assistance : seulement pour l'Assurance Auto. */
  assistance?: { plans: AssistancePlan[] };
}

export interface CardSummary {
  fiche: boolean;
  description: number;
  benefits: number;
  guarantees: number;
  formulas: number;
  exclusions: number;
  documents: number;
  faq: number;
  assistance: number | null;
  icon: string;
  family: string;
}

const text = (v: unknown): string | undefined => (typeof v === 'string' && v.trim() ? v : undefined);
const list = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);

/** Contenu exporté d'un produit tel qu'il est enregistré (sans visibilité ni ordre d'affichage). */
export function buildCardFile(product: MobileProduct, source: string, assistance: AssistancePlan[] | null): CardFile {
  const file: CardFile = {
    format: CARD_FORMAT,
    version: CARD_VERSION,
    source,
    exportedAt: new Date().toISOString(),
    produit: { cle: product.productKey ?? null, titre: product.title },
    contenu: {
      shortName: product.shortName ?? undefined,
      family: product.family ?? undefined,
      icon: product.icon ?? undefined,
      description: product.description ?? undefined,
      benefits: [...(product.benefits ?? [])],
      howItWorks: product.howItWorks ?? undefined,
      eligibility: product.eligibility ?? undefined,
      guarantees: (product.guarantees ?? []).map((g) => ({
        id: g.id ?? undefined,
        name: g.name,
        kind: g.kind,
        description: g.description ?? undefined,
        limit: g.limit ?? undefined,
        formulaIds: [...(g.formulaIds ?? [])],
        displayOrder: g.displayOrder ?? undefined,
        visible: g.visible ?? true,
      })),
      formulas: (product.formulas ?? []).map((f) => ({
        id: f.id,
        name: f.name,
        description: f.description ?? undefined,
        displayOrder: f.displayOrder ?? undefined,
        visible: f.visible ?? true,
      })),
      exclusions: [...(product.exclusions ?? [])],
      requiredDocuments: [...(product.requiredDocuments ?? [])],
      faq: (product.faq ?? []).map((q) => ({ question: q.question, answer: q.answer })),
    },
  };
  if (assistance) file.assistance = { plans: assistance };
  return file;
}

export type ParseResult = { ok: true; file: CardFile } | { ok: false; error: string };

/** Lit et contrôle un fichier de fiche. `product` = produit ouvert dans l'éditeur (la clé doit concorder). */
export function parseCardFile(raw: string, product: MobileProduct): ParseResult {
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return { ok: false, error: 'Fichier illisible : ce n’est pas un fichier JSON valide.' };
  }
  const f = data as Partial<CardFile> | null;
  if (!f || typeof f !== 'object' || f.format !== CARD_FORMAT || typeof f.contenu !== 'object' || !f.contenu) {
    return { ok: false, error: 'Ce fichier n’est pas une fiche AssurZen.' };
  }
  if (f.version !== CARD_VERSION) {
    return { ok: false, error: 'Version de fichier non prise en charge.' };
  }
  const fileKey = f.produit?.cle ?? null;
  const key = product.productKey ?? null;
  if (fileKey && key && fileKey !== key) {
    return { ok: false, error: `Ce fichier concerne « ${f.produit?.titre ?? fileKey} », pas « ${product.title} ».` };
  }
  if ((!fileKey || !key) && (f.produit?.titre ?? '').trim().toLowerCase() !== product.title.trim().toLowerCase()) {
    return { ok: false, error: `Ce fichier concerne « ${f.produit?.titre ?? 'un autre produit'} », pas « ${product.title} ».` };
  }

  const c = f.contenu as unknown as Record<string, unknown>;
  const guarantees = (Array.isArray(c['guarantees']) ? (c['guarantees'] as MobileGuarantee[]) : []).filter((g) => g && typeof g === 'object');
  const formulas = (Array.isArray(c['formulas']) ? (c['formulas'] as MobileFormula[]) : []).filter((x) => x && typeof x === 'object');
  const faq = (Array.isArray(c['faq']) ? (c['faq'] as MobileFaq[]) : []).filter((q) => q && typeof q === 'object');
  const content: CardContent = {
    shortName: text(c['shortName']),
    family: FAMILIES.includes(c['family'] as ProductFamily) ? (c['family'] as ProductFamily) : undefined,
    icon: text(c['icon']),
    description: text(c['description']),
    benefits: list(c['benefits']),
    howItWorks: text(c['howItWorks']),
    eligibility: text(c['eligibility']),
    guarantees,
    formulas,
    exclusions: list(c['exclusions']),
    requiredDocuments: list(c['requiredDocuments']),
    faq,
  };

  const limits: [string, number, number][] = [
    ['avantages', content.benefits.length, MAX.benefits],
    ['garanties', content.guarantees.length, MAX.guarantees],
    ['formules', content.formulas.length, MAX.formulas],
    ['exclusions', content.exclusions.length, MAX.exclusions],
    ['pièces à fournir', content.requiredDocuments.length, MAX.documents],
    ['questions fréquentes', content.faq.length, MAX.faq],
  ];
  const over = limits.find(([, n, max]) => n > max);
  if (over) return { ok: false, error: `Nombre de ${over[0]} trop élevé dans le fichier (${over[1]}, maximum ${over[2]}).` };

  if (content.guarantees.some((g) => !text(g.name) || !KINDS.includes(g.kind))) {
    return { ok: false, error: 'Une garantie du fichier est invalide (nom ou type manquant).' };
  }
  if (content.formulas.some((x) => !text(x.id) || !text(x.name))) {
    return { ok: false, error: 'Une formule du fichier est invalide (identifiant ou nom manquant).' };
  }
  if (content.faq.some((q) => !text(q.question) || !text(q.answer))) {
    return { ok: false, error: 'Une question fréquente du fichier est incomplète.' };
  }
  const formulaIds = new Set(content.formulas.map((x) => x.id));
  const unknown = content.guarantees.find((g) => (g.formulaIds ?? []).some((id) => !formulaIds.has(id)));
  if (unknown) {
    return { ok: false, error: `La garantie « ${unknown.name} » référence une formule absente du fichier.` };
  }

  const assistance = f.assistance && Array.isArray(f.assistance.plans) ? { plans: f.assistance.plans } : undefined;
  return {
    ok: true,
    file: {
      format: CARD_FORMAT,
      version: CARD_VERSION,
      source: typeof f.source === 'string' ? f.source : 'inconnue',
      exportedAt: typeof f.exportedAt === 'string' ? f.exportedAt : '',
      produit: { cle: fileKey, titre: f.produit?.titre ?? product.title },
      contenu: content,
      ...(assistance ? { assistance } : {}),
    },
  };
}

/** Résumé chiffré d'un contenu de fiche (pour la comparaison avant import). */
export function summarizeContent(
  c: Partial<CardContent> | null | undefined,
  hasContent: boolean,
  assistance: number | null,
): CardSummary {
  return {
    fiche: hasContent,
    description: (c?.description ?? '').length,
    benefits: c?.benefits?.length ?? 0,
    guarantees: c?.guarantees?.length ?? 0,
    formulas: c?.formulas?.length ?? 0,
    exclusions: c?.exclusions?.length ?? 0,
    documents: c?.requiredDocuments?.length ?? 0,
    faq: c?.faq?.length ?? 0,
    assistance,
    icon: c?.icon ?? '',
    family: c?.family ?? '',
  };
}
