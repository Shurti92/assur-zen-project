import { ParrainageStatut } from './parrainage.model';

export interface ParrainageDashboardOverviewResponse {
  totalParrainages: number;
  parrainagesEligibles: number;
  parrainagesConvertis: number;
  tauxConversion: number;
  recompensesAttribuees: number;
  montantTotalRecompenses: number;
  parrainsActifs: number;
}

export interface ParrainageTopParrainItem {
  parrainId: number;
  parrainNom: string;
  nombreParrainages: number;
  nombreConvertis: number;
  recompensesTotal: number;
}

export interface ParrainageParStatutItem {
  statut: ParrainageStatut;
  nombre: number;
}

export interface ParrainageMensuelItem {
  mois: string;
  nombre: number;
  convertis: number;
}
