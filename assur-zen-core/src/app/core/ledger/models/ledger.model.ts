export type TypeOperation = 'SOUSCRIPTION' | 'PAIEMENT' | 'COMMISSION' | 'ANNULATION';
export type TypeCompte = 'ACTIF' | 'PASSIF' | 'CHARGE' | 'PRODUIT';

export interface LigneEcritureResponse {
  id: number;
  compteId: number;
  compteCode: string;
  compteLibelle: string;
  montantDebit: number;
  montantCredit: number;
  libelle: string;
}

export interface EcritureComptableResponse {
  id: number;
  reference: string;
  dateEcriture: string;
  libelle: string;
  typeOperation: TypeOperation;
  contractId: number;
  contractPolicyNumber: string;
  exerciceComptableId: number;
  exerciceComptableLibelle: string;
  ecritureOrigineId: number | null;
  contrepassee: boolean;
  lignes: LigneEcritureResponse[];
  creation: string;
}

export interface EcriturePage {
  content: EcritureComptableResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CompteComptableResponse {
  id: number;
  code: string;
  libelle: string;
  type: TypeCompte;
  active: boolean;
}

export interface BalanceCompteResponse {
  compteId: number;
  compteCode: string;
  compteLibelle: string;
  type: TypeCompte;
  totalDebit: number;
  totalCredit: number;
  solde: number;
}

export interface GrandLivreLineResponse {
  dateEcriture: string;
  ecritureReference: string;
  typeOperation: TypeOperation;
  libelle: string;
  montantDebit: number;
  montantCredit: number;
  soldeProgressif: number;
}
