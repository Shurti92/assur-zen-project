export type PrimeObjectifStatut = 'EN_ATTENTE' | 'VALIDEE' | 'PAYEE' | 'REJETEE';

export interface PrimeObjectifRequest {
  partenaireId: number;
}

export interface PrimeObjectifResponse {
  id: number;
  reference: string;
  partenaireId: number;
  partenaireNom: string;
  exerciceComptableId: number;
  exerciceComptableLibelle: string;
  objectifProduction: number;
  objectifRealise: number;
  tauxRealisation: number;
  montant: number;
  statut: PrimeObjectifStatut;
  dateDemande: string;
  demandeParUserId: number;
  valideParUserId: number | null;
  dateValidation: string | null;
  payeParUserId: number | null;
  datePaiement: string | null;
  motifRejet: string | null;
  /** Envoyé uniquement à la transition vers PAYEE. */
  notificationEnvoyee: boolean;
}

export interface PrimeObjectifStatutRequest {
  statut: PrimeObjectifStatut;
  /** Obligatoire côté backend pour REJETEE (400 sinon) — pas de contrainte formelle sur le
   * schéma, à valider côté frontend avant l'appel. */
  motifRejet?: string;
}

export interface PrimeObjectifPage {
  content: PrimeObjectifResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** GET /partenaires/{id}/prime-objectif/simulation — aperçu sans engagement, ne crée aucune
 * demande. Les 4 flags (primeActivee/exerciceCloture/objectifAtteint/demandeDejaExistante)
 * expliquent individuellement pourquoi `eligible` peut être false. */
export interface PrimeObjectifSimulationResponse {
  partenaireId: number;
  exerciceComptableId: number;
  exerciceComptableLibelle: string;
  objectifProduction: number;
  objectifRealise: number;
  tauxRealisation: number;
  montantSimule: number;
  primeActivee: boolean;
  exerciceCloture: boolean;
  objectifAtteint: boolean;
  demandeDejaExistante: boolean;
  eligible: boolean;
}
