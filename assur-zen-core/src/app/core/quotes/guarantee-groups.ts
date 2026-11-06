import { QuoteGuarantee } from './models/quote.model';

/** Rubrique de garanties (accordéon) ; `name` vide = garanties sans groupe, affichées à plat. */
export interface GuaranteeGroup {
  name: string;
  icon: string;
  items: QuoteGuarantee[];
}

/** Pictogramme d'une rubrique d'après son nom (🛡️ par défaut). */
export function groupIcon(name: string): string {
  const s = name.toLowerCase();
  if (/sant|m[ée]dic/.test(s)) return '🩺';
  if (/rapatri|transport/.test(s)) return '🚑';
  if (/bagage|retard/.test(s)) return '🧳';
  return '🛡️';
}

/** Regroupe les garanties par rubrique, dans l'ordre d'apparition. */
export function groupGuarantees(list: QuoteGuarantee[] | null | undefined): GuaranteeGroup[] {
  const out: GuaranteeGroup[] = [];
  for (const g of list ?? []) {
    const name = (g.group ?? '').trim();
    let grp = out.find((x) => x.name === name);
    if (!grp) {
      grp = { name, icon: groupIcon(name), items: [] };
      out.push(grp);
    }
    grp.items.push(g);
  }
  return out;
}
