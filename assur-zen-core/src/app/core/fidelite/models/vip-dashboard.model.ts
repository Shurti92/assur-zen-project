export interface VipOverviewResponse {
  totalVip: number;
  totalComptes: number;
  scoreMoyenVip: number;
  primeGenereeVip: number;
  recompensesDistribuees: number;
  pointsRecompensesDistribues: number;
  entreesVipMoisCourant: number;
  sortiesVipMoisCourant: number;
}

export interface VipMensuelItem {
  mois: string;
  entrees: number;
  sorties: number;
}

export interface VipParDimensionItem {
  id: number;
  nom: string;
  nombreVip: number;
  primeTotale: number;
}

export interface VipParNiveauItem {
  palierId: number;
  palierLibelle: string;
  couleur: string | null;
  icone: string | null;
  estVip: boolean;
  nombreComptes: number;
}

export interface VipTopClientItem {
  insuredId: number;
  /** Nullable depuis le 2026-09-14 : le backend renvoie désormais null (au lieu d'un 500)
   * quand l'entité référencée par la FK a été supprimée (hard-delete). */
  insuredName: string | null;
  scoreFidelite: number;
  palierLibelle: string | null;
  soldePoints: number;
}
