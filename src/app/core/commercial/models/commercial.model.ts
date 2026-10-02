/**
 * Module Commercial (force de vente) — DTOs vérifiés en direct contre /v3/api-docs le
 * 2026-09-07 (backend démarré, contrat initial docs/api-contracts/module-commercial.md corrigé
 * en conséquence — noms de champs réels différents des suppositions initiales : employeeCode,
 * firstname/lastname/email/phone, baseSalary, statut à 4 valeurs au lieu d'un booléen actif).
 */

export type CommercialStatut = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'TERMINATED';

export interface CommercialRequest {
  /** Généré côté backend si absent. */
  employeeCode?: string;
  username: string;
  /** Optionnel — généré aléatoirement côté backend si absent (voir temporaryPassword en réponse). */
  password?: string;
  firstname: string;
  lastname: string;
  email: string;
  phone: string;
  villePartenaireId: number;
  baseSalary: number;
  transportBenefitEnabled?: boolean;
  transportBenefitAmount?: number;
  phoneAllowanceEnabled?: boolean;
  phoneAllowanceAmount?: number;
  hireDate: string;
}

/** PUT /commercials/{id} — pas de username/password/employeeCode (immuables après création),
 * le statut se change uniquement via PATCH /commercials/{id}/statut. */
export interface CommercialUpdateRequest {
  firstname: string;
  lastname: string;
  email: string;
  phone: string;
  villePartenaireId: number;
  baseSalary: number;
  transportBenefitEnabled?: boolean;
  transportBenefitAmount?: number;
  phoneAllowanceEnabled?: boolean;
  phoneAllowanceAmount?: number;
  hireDate: string;
  endDate?: string;
}

export interface CommercialStatusRequest {
  statut: CommercialStatut;
  motif?: string;
}

export interface CommercialResponse {
  id: number;
  employeeCode: string | null;
  profileId: number | null;
  userId: number | null;
  username: string | null;
  firstname: string;
  lastname: string;
  email: string | null;
  phone: string | null;
  villePartenaireId: number | null;
  villePartenaireNom: string | null;
  baseSalary: number;
  transportBenefitEnabled: boolean;
  transportBenefitAmount: number | null;
  phoneAllowanceEnabled: boolean;
  phoneAllowanceAmount: number | null;
  hireDate: string | null;
  endDate: string | null;
  statut: CommercialStatut;
  createdAt: string;
  updatedAt: string;
  /** Présent uniquement sur la réponse de création (UserProfileService.create() côté backend). */
  temporaryPassword?: string | null;
  notificationEnvoyee?: boolean;
}

export interface CommercialPage {
  content: CommercialResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** GET /commercials n'a pas de paramètre de recherche texte — filtrage statut/ville uniquement. */
export interface CommercialFilter {
  statut?: CommercialStatut;
  villePartenaireId?: number;
}

/** Seul PREMIUM_AMOUNT est réellement câblé pour l'instant (décision produit actée dans le
 * contrat de départ — pas de recalcul pour les autres) ; targetCount n'a de sens que pour
 * CONTRACT_COUNT, non utilisé ici. */
export type CommercialObjectiveType = 'PREMIUM_AMOUNT' | 'CONTRACT_COUNT' | 'REVENUE' | 'OTHER';

export interface CommercialObjectiveRequest {
  exerciceComptableId: number;
  objectiveType: CommercialObjectiveType;
  targetAmount: number;
  targetCount?: number;
}

export interface CommercialObjectiveResponse {
  id: number;
  commercialId: number;
  exerciceComptableId: number;
  exerciceLibelle: string;
  objectiveType: CommercialObjectiveType;
  targetAmount: number;
  targetCount: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CommercialMonthlyObjectiveRequest {
  moisComptableId: number;
  objectiveType: CommercialObjectiveType;
  targetAmount: number;
  targetCount?: number;
}

/** PUT .../monthly-objectives/{objectiveId} — motif obligatoire : chaque révision est tracée
 * par le moteur d'audit fonctionnel existant (pas de table d'historique dédiée). */
export interface CommercialMonthlyObjectiveRevisionRequest {
  targetAmount: number;
  targetCount?: number;
  motif: string;
}

export interface CommercialMonthlyObjectiveResponse {
  id: number;
  commercialId: number;
  moisComptableId: number;
  moisLibelle: string;
  moisNumero: number;
  objectiveType: CommercialObjectiveType;
  targetAmount: number;
  targetCount: number | null;
  createdAt: string;
  updatedAt: string;
}

/** OPEN/CALCULATED/ELIGIBLE/NOT_ELIGIBLE → bonus estimé, jamais présenté comme acquis.
 * VALIDATED/PAID/CLOSED → montant définitif. */
export type CommercialPerformanceStatut =
  | 'OPEN'
  | 'CALCULATED'
  | 'ELIGIBLE'
  | 'NOT_ELIGIBLE'
  | 'VALIDATED'
  | 'PAID'
  | 'CLOSED';

export interface CommercialPerformanceResponse {
  id: number;
  commercialId: number;
  moisComptableId: number;
  moisLibelle: string;
  moisNumero: number;
  objectiveAmount: number;
  realizedAmount: number;
  achievementRate: number;
  bonusRateSnapshot: number | null;
  bonusAmount: number | null;
  eligible: boolean;
  baseSalarySnapshot: number | null;
  transportBenefitSnapshot: number | null;
  phoneAllowanceSnapshot: number | null;
  totalRemuneration: number | null;
  statut: CommercialPerformanceStatut;
  calculatedAt: string | null;
  validatedAt: string | null;
  validatedById: number | null;
  paidAt: string | null;
  paidById: number | null;
  notificationEnvoyee?: boolean;
  /** true tant que la performance n'est pas VALIDATED/PAID/CLOSED (valeurs recalculées en direct). */
  estime: boolean;
  /** Seuil d'éligibilité appliqué (en %) — détail du calcul du bonus. */
  seuilEligibilite: number | null;
  /** Bonus réellement payable maintenant (validé, éligible, montant > 0, aucun virement en cours). */
  payable: boolean;
  modePaiement: CommercialBonusModePaiement | null;
  paymentReference: string | null;
  /** Dernier virement SycaPay tenté pour ce bonus (null si aucun). */
  sycapayTransactionId: number | null;
  sycapayStatus: SycaPayTransactionStatus | null;
  sycapayTransactionReference: string | null;
  sycapayMessage: string | null;
  sycapayRequestDate: string | null;
  commercialNom: string | null;
  employeeCode: string | null;
}

export type SycaPayTransactionStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'UNKNOWN';

export type CommercialBonusModePaiement = 'VIREMENT_SYCAPAY' | 'ENREGISTREMENT_MANUEL';

/** PATCH …/statut — le passage à PAID passe exclusivement par POST …/bonus/pay (virement réel). */
export interface CommercialPerformanceStatusRequest {
  statut: Extract<CommercialPerformanceStatut, 'VALIDATED' | 'CLOSED'>;
  motif?: string;
}

/** POST /commercials/{id}/performance/{moisId}/bonus/pay */
export interface CommercialBonusPaymentRequest {
  reference?: string;
  motif?: string;
}

/** Issue du paiement : lire sycapayStatus — SUCCESS (payé), PROCESSING/UNKNOWN (en cours, ne pas
 * relancer), FAILED (aucun montant parti, relançable), REJECTED (aucune tentative). null en mode
 * ENREGISTREMENT_MANUEL (SycaPay désactivé, paiement fait hors application). */
export interface CommercialBonusPaymentResponse {
  performance: CommercialPerformanceResponse;
  modePaiement: CommercialBonusModePaiement;
  sycapayStatus: SycaPayTransactionStatus | 'REJECTED' | null;
  sycapayTransactionReference: string | null;
  sycapayMessage: string | null;
}

/** GET /commercial-performance/dashboard — vue globale équipe (distincte du dashboard
 * self-service /commercials/me/dashboard, hors périmètre de cette app back-office). */
export interface CommercialManagementDashboardResponse {
  moisComptableId: number;
  moisLibelle: string;
  totalCommercials: number;
  activeCommercials: number;
  monthlyObjective: number;
  monthlyProduction: number;
  monthlyAchievementRate: number;
  eligibleCommercials: number;
  nonEligibleCommercials: number;
  totalBonus: number;
}

/** GET /commercial-performance/ranking — jamais de montant, uniquement le taux d'atteinte
 * (règle explicite du contrat, y compris côté admin). Pas de champ "rang" dans la réponse —
 * le tableau est déjà trié par le backend, le rang s'affiche via l'index côté frontend. */
export interface CommercialRankingEntry {
  commercialId: number;
  employeeCode: string | null;
  nomComplet: string;
  achievementRate: number;
}

/** GET /commercial-payroll?moisComptableId= — éléments de préparation paie (salaire de base +
 * primes + bonus), endpoint dédié distinct de la performance. */
export interface CommercialPayrollEntry {
  commercialId: number;
  employeeCode: string | null;
  commercialNom: string;
  moisComptableId: number;
  moisLibelle: string;
  baseSalary: number;
  transportBenefitAmount: number | null;
  phoneAllowanceAmount: number | null;
  bonusAmount: number | null;
  totalVariable: number;
  statut: CommercialPerformanceStatut;
  /** Mois en cours / non validé : bonus estimé (non transmissible à la paie). */
  estime: boolean;
  objectiveAmount: number;
  realizedAmount: number;
  achievementRate: number;
  eligible: boolean;
  bonusPayable: boolean;
  sycapayStatus: SycaPayTransactionStatus | null;
  /** Taux du bonus (% de l'objectif) et seuil d'éligibilité (% d'atteinte) — détail du calcul. */
  bonusRate: number | null;
  seuilEligibilite: number | null;
  villeNom: string | null;
  /** Numéro Wave masqué par le backend (ex. « 07 •• •• 12 »). */
  telephoneMasque: string | null;
  paidAt: string | null;
  modePaiement: CommercialBonusModePaiement | null;
  sycapayTransactionId: number | null;
  sycapayTransactionReference: string | null;
}

/** GET /commercials/options — force de vente interne proposée dans la liste « Commercial » de la
 * souscription : commerciaux actifs avec compte, sans aucune donnée de rémunération. `userId` est la
 * valeur attendue par `salesRepId` du contrat. Trié par nom puis prénoms côté backend. */
export interface CommercialOptionResponse {
  commercialId: number;
  userId: number;
  nom: string;
  prenoms: string;
  employeeCode: string | null;
  villeNom: string | null;
}
