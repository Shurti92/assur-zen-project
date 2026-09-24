export type Tendance = 'HAUSSE' | 'BAISSE' | 'STABLE';

/** `variationPourcentage` est `null` quand `valeurPrecedente` vaut 0 (division non définie) —
 * `tendance` reste calculable depuis le seul signe de `variation`. Voir docs/api-contracts/kpi-dashboard.md §2. */
export interface KpiValue<T> {
  valeur: T;
  valeurPrecedente: T;
  variation: T;
  variationPourcentage: number | null;
  tendance: Tendance;
}

export interface DashboardPeriodeResponse {
  dateDebut: string;
  dateFin: string;
}

// ================================================================ VIGNETTES ================================================================

export type VignetteMotoTypeDemandeCode = 'NOUVELLE_DEMANDE' | 'RENOUVELLEMENT';
export type VignetteMotoCategorieCylindreeCode = 'MOINS_125_CM3' | '125_CM3_ET_PLUS';
export type VignetteMotoCategorieAgeCode = '1_4_ANS' | '5_10_ANS' | '11_ANS_ET_PLUS';
export type VignetteMotoStatutCode =
  | 'BROUILLON'
  | 'EN_ATTENTE_PAIEMENT'
  | 'PAYEE'
  | 'EN_TRAITEMENT'
  | 'EN_EDITION'
  | 'EDITEE'
  | 'EN_LIVRAISON'
  | 'LIVREE'
  | 'ANNULEE'
  | 'REJETEE';

export interface VignetteKpis {
  nombreDemandes: KpiValue<number>;
  nouvellesDemandes: KpiValue<number>;
  renouvellements: KpiValue<number>;
  vignettesEditees: KpiValue<number>;
  vignettesLivrees: KpiValue<number>;
  demandesEnAttente: KpiValue<number>;
  demandesRejetees: KpiValue<number>;
  demandesAnnulees: KpiValue<number>;
  montantVignettes: KpiValue<number>;
  totalFraisOperation: KpiValue<number>;
  totalFraisLivraison: KpiValue<number>;
  totalFraisAccessoires: KpiValue<number>;
  chiffreAffaires: KpiValue<number>;
  panierMoyen: KpiValue<number>;
  tauxTransformation: KpiValue<number>;
  tauxEdition: KpiValue<number>;
  tauxLivraison: KpiValue<number>;
  tauxRejet: KpiValue<number>;
  tauxAnnulation: KpiValue<number>;
  /** Reste `null` en permanence pour l'instant — le workflow paiement/édition/livraison
   * n'a pas encore de service derrière (voir vignette-moto.md). Pas un bug. */
  delaiMoyenTraitementJours: number | null;
}

export interface ParTypeDemandeItem {
  type: VignetteMotoTypeDemandeCode;
  nombre: number;
}

export interface ParCylindreeItem {
  categorie: VignetteMotoCategorieCylindreeCode;
  nombre: number;
}

export interface ParAgeMotoItem {
  categorie: VignetteMotoCategorieAgeCode;
  nombre: number;
}

export interface ParZoneLivraisonItem {
  zoneCode: string;
  nombre: number;
}

export interface VignetteParStatutItem {
  statut: VignetteMotoStatutCode;
  nombre: number;
}

export interface VignetteRepartitions {
  parTypeDemande: ParTypeDemandeItem[];
  parCylindree: ParCylindreeItem[];
  parAgeMoto: ParAgeMotoItem[];
  parZoneLivraison: ParZoneLivraisonItem[];
  parStatut: VignetteParStatutItem[];
}

export interface VignetteEvolutionItem {
  periode: string;
  nombreDemandes: number;
  montant: number;
}

export interface DashboardVignetteResponse {
  periode: DashboardPeriodeResponse;
  kpis: VignetteKpis;
  repartitions: VignetteRepartitions;
  evolution: VignetteEvolutionItem[];
}

// ================================================================ SORTIES DE FONDS ================================================================

export interface SortieFondsKpis {
  totalSorties: KpiValue<number>;
  enAttente: KpiValue<number>;
  validees: KpiValue<number>;
  executees: KpiValue<number>;
  rejetees: KpiValue<number>;
  annulees: KpiValue<number>;
  montantTotal: KpiValue<number>;
  montantEnAttente: KpiValue<number>;
  montantValide: KpiValue<number>;
  montantExecute: KpiValue<number>;
  montantRejete: KpiValue<number>;
  montantAnnule: KpiValue<number>;
  montantMoyenParSortie: KpiValue<number>;
  /** Jamais un KpiValue — un maximum instantané n'a pas de sens comparé à une période précédente. */
  montantPlusGrosseSortie: number;
  /** exécutées / (validées + déjà exécutées) — pas exécutées / validées seules, sinon le taux
   * pourrait dépasser 100 % pour toute sortie qui a fini par être décaissée. */
  tauxExecution: KpiValue<number>;
  tauxRejet: KpiValue<number>;
  delaiMoyenTraitementJours: KpiValue<number>;
  totalEncaissements: KpiValue<number>;
  solde: KpiValue<number>;
  ratioSortiesEncaissements: KpiValue<number>;
}

export interface ParTypeItem {
  typeCode: string;
  nombre: number;
  montant: number;
}

export interface ParBeneficiaireItem {
  beneficiaire: string;
  nombre: number;
  montant: number;
}

export interface SortieFondsParStatutItem {
  statut: string;
  nombre: number;
  montant: number;
}

export interface SortieFondsRepartitions {
  parType: ParTypeItem[];
  parBeneficiaire: ParBeneficiaireItem[];
  parStatut: SortieFondsParStatutItem[];
}

export interface SortieFondsEvolutionItem {
  periode: string;
  nombre: number;
  montant: number;
}

export interface DashboardSortieFondsResponse {
  periode: DashboardPeriodeResponse;
  kpis: SortieFondsKpis;
  repartitions: SortieFondsRepartitions;
  evolution: SortieFondsEvolutionItem[];
}

// ================================================================ ASSURÉS ================================================================

export type TypeClientCode = 'PARTICULIER' | 'ENTREPRISE';

export interface AssureKpis {
  totalAssures: KpiValue<number>;
  /** Déduit de la date du premier contrat de l'assuré — un proxy, pas une vraie date d'inscription
   * (la table des assurés n'a aucune date de création propre). */
  nouveauxAssures: KpiValue<number>;
  assuresActifs: KpiValue<number>;
  assuresInactifs: KpiValue<number>;
  /** 2+ contrats, même produit ou non — à ne pas confondre avec assuresMultiEquipes. */
  assuresPlusieursContrats: KpiValue<number>;
  /** 2+ contrats sur des produits distincts — celui-ci alimente tauxMultiEquipement. */
  assuresMultiEquipes: KpiValue<number>;
  nombreMoyenContratsParAssure: KpiValue<number>;
  tauxCroissance: KpiValue<number>;
  tauxMultiEquipement: KpiValue<number>;
}

export interface ParVilleItem {
  ville: string;
  nombre: number;
}

export interface ParTypeClientItem {
  typeClient: TypeClientCode;
  nombre: number;
}

export interface AssureRepartitions {
  parVille: ParVilleItem[];
  parTypeClient: ParTypeClientItem[];
}

export interface AssureEvolutionItem {
  periode: string;
  nouveauxAssures: number;
  totalAssures: number;
}

export interface DashboardAssureResponse {
  periode: DashboardPeriodeResponse;
  kpis: AssureKpis;
  repartitions: AssureRepartitions;
  evolution: AssureEvolutionItem[];
}

// ================================================================ GLOBAL ================================================================

export interface DashboardGlobalResponse {
  periode: DashboardPeriodeResponse;
  assures: AssureKpis;
  vignettes: VignetteKpis;
  sortiesFonds: SortieFondsKpis;
}
