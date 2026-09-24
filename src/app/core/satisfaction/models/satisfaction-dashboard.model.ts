export interface SatisfactionOverviewResponse {
  satisfactionMoyenne: number | null;
  nps: number | null;
  enquetesEnvoyees: number;
  reponses: number;
  tauxReponse: number | null;
  reclamationsOuvertes: number;
  reclamationsResolues: number;
  delaiMoyenTraitementHeures: number | null;
}

export interface NpsBreakdownResponse {
  promoteurs: number;
  passifs: number;
  detracteurs: number;
  totalReponses: number;
  nps: number;
}

export interface SatisfactionMensuelleResponse {
  mois: string;
  satisfactionMoyenne: number;
  nps: number;
  reponses: number;
}

export interface SatisfactionParDimensionItem {
  id: number;
  nom: string;
  satisfactionMoyenne: number;
  nps: number;
  reponses: number;
}

export interface RepartitionNotesResponse {
  parNote: Record<string, number>;
  total: number;
}
